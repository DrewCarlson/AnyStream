/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.service.stream

import anystream.models.*
import anystream.models.api.EpisodeResponse
import anystream.models.api.MediaLookupResponse
import anystream.models.api.MovieResponse
import anystream.models.api.PlaybackSessionsResponse
import anystream.util.ObjectId
import com.github.kokorin.jaffree.LogLevel
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.*
import com.github.kokorin.jaffree.ffprobe.FFprobe
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS

private const val DEFAULT_WAIT_FOR_SEGMENTS = 8

// HLS Spec https://datatracker.ietf.org/doc/html/rfc8216
class StreamService(
    private val queries: StreamServiceQueries,
    private val ffmpeg: () -> FFmpeg,
    private val ffprobe: () -> FFprobe,
    private val transcodePath: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessionMap = ConcurrentHashMap<String, TranscodeSession>()
    private val transcodeJobs = ConcurrentHashMap<String, Job>()
    private val sessionUpdates = MutableSharedFlow<TranscodeSession>(
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    suspend fun getPlaybackSessions(): PlaybackSessionsResponse {
        val playbackStates = queries.fetchPlaybackStatesByIds(sessionMap.keys().toList())
        val userIds = playbackStates.map(PlaybackState::userId).distinct()
        val users = queries.fetchUsersByIds(userIds)
        val mediaIds = playbackStates.map(PlaybackState::mediaId).distinct()
        val mediaLookups = mediaIds.associateWith { id ->
            MediaLookupResponse(
                movie = queries.fetchMovieById(id)?.run(::MovieResponse),
                episode = queries.fetchEpisodeById(id)?.let { (episode, show) ->
                    EpisodeResponse(episode, show)
                },
            )
        }
        return PlaybackSessionsResponse(
            playbackStates = playbackStates,
            transcodeSessions = sessionMap,
            users = users.associateBy(User::id),
            mediaLookups = mediaLookups,
        )
    }

    suspend fun getPlaybackState(
        mediaRefId: String,
        userId: Int,
        create: Boolean
    ): PlaybackState? {
        val state = queries.fetchPlaybackState(mediaRefId, userId)
        return if (create && state == null) {
            val mediaRef = queries.fetchMediaRef(mediaRefId) ?: return null
            val file = when (mediaRef) {
                is LocalMediaReference -> mediaRef.filePath
                is DownloadMediaReference -> mediaRef.filePath
            }?.run(::File) ?: return null

            val runtime = getFileDuration(file).seconds
            val newState = PlaybackState(
                id = ObjectId.get().toString(),
                mediaReferenceId = mediaRefId,
                position = 0.0,
                userId = userId,
                mediaId = mediaRef.contentId,
                runtime = runtime.toDouble(SECONDS),
                updatedAt = Clock.System.now(),
            )
            if (queries.insertPlaybackState(newState)) {
                if (!sessionMap.containsKey(newState.id)) {
                    val output = File("$transcodePath/$mediaRefId/${newState.id}")
                    startTranscode(
                        token = newState.id,
                        name = mediaRefId,
                        mediaFile = file,
                        outputDir = output,
                        runtime = runtime,
                    )
                }
                newState
            } else null
        } else {
            state
        }
    }

    suspend fun deletePlaybackState(playbackStateId: String): Boolean {
        return queries.deletePlaybackState(playbackStateId)
    }

    suspend fun updateStatePosition(stateId: String, position: Double): Boolean {
        return queries.updatePlaybackState(stateId, position)
    }

    suspend fun getPlaylist(mediaRefId: String, token: String): String? {
        val mediaRef = queries.fetchMediaRef(mediaRefId) ?: return null
        val file = when (mediaRef) {
            is LocalMediaReference -> mediaRef.filePath
            is DownloadMediaReference -> mediaRef.filePath
        }?.run(::File) ?: return null

        val runtime = getFileDuration(file).seconds
        if (!sessionMap.containsKey(token)) {
            val output = File("$transcodePath/$mediaRefId/$token")
            startTranscode(
                token = token,
                name = mediaRefId,
                mediaFile = file,
                outputDir = output,
                runtime = runtime,
            )
        }

        return createVariantPlaylist(
            name = mediaRefId,
            mediaFile = file,
            token = token,
            runtime = runtime,
        )
    }

    suspend fun getFilePathForSegment(token: String, segmentFile: String): String? {
        val session = sessionMap[token] ?: return null
        val segmentIndex = segmentFile.substringAfter(session.mediaRefId)
            .substringBefore(".ts")
            .toInt()
        setSegmentTarget(token, segmentIndex)
        val output = File("${session.outputPath}/$segmentFile")
        return if (output.exists()) output.absolutePath else null
    }

    fun stopSession(token: String, deleteOutput: Boolean) {
        sessionMap.remove(token)?.also { session ->
            transcodeJobs.remove(token)?.cancel()
            if (deleteOutput) {
                File(session.outputPath).delete()
            }
        }
    }

    private suspend fun getFileDuration(mediaFile: File): Double {
        return Dispatchers.IO {
            ffprobe()
                .setLogLevel(LogLevel.QUIET)
                .setShowEntries("format=duration")
                .setInput(mediaFile.toPath())
                .execute()
        }.data.getSubDataDouble("format", "duration")
    }

    private suspend fun startTranscode(
        token: String,
        name: String,
        mediaFile: File,
        outputDir: File,
        runtime: Duration,
        startAt: Double = 0.0,
        waitForSegments: Int = DEFAULT_WAIT_FOR_SEGMENTS
    ): TranscodeSession {
        transcodeJobs[token]?.also { job ->
            if (job.isActive) {
                return sessionMap.getValue(token)
            } // TODO: check if transcode is complete
        }

        val mutex = Mutex(locked = true)

        outputDir.apply {
            mkdirs()
            setWritable(true)
        }

        val transcodedSegments = outputDir.listFiles()
            ?.toList()
            .orEmpty()
            .filter { it.length() > 0 }
            .mapNotNull { file ->
                file.name
                    .substringAfterLast(name, "")
                    .substringBefore(".ts", "")
                    .takeIf(String::isNotBlank)
                    ?.toInt()
            }
            .sorted()
        val transcodeProgress = MutableSharedFlow<FFmpegProgress>(
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val segmentLength = 6
        val requestedStartTime = (startAt - segmentLength).coerceAtLeast(0.0).seconds
        val requestedStartSegment = (requestedStartTime.inWholeSeconds / segmentLength).toInt().coerceAtLeast(0)
        val (segmentCount, lastSegmentDuration) = getSegmentCountAndFinalLength(runtime, segmentLength.seconds)
        val startSegment = if (transcodedSegments.contains(requestedStartSegment)) {
            var nextRequiredSegment = requestedStartSegment + 1
            while (transcodedSegments.contains(nextRequiredSegment)) {
                nextRequiredSegment++
            }
            nextRequiredSegment.coerceAtMost(segmentCount)
        } else {
            requestedStartSegment
        }
        val startTime = if (requestedStartSegment == startSegment) {
            requestedStartTime
        } else {
            (startSegment * segmentLength)
                .toDouble()
                .coerceAtMost(runtime.inWholeSeconds - lastSegmentDuration)
                .seconds
        }
        val command = ffmpeg().apply {
            addInput(
                UrlInput.fromPath(mediaFile.toPath())
                    .addArguments("-ss", startTime.toDouble(SECONDS).toString())
            )
            addArguments("-f", "hls")
            addArguments("-hls_time", segmentLength.toString())
            addArguments("-hls_playlist_type", "vod")
            addArguments("-hls_flags", "independent_segments")
            addArguments("-hls_segment_type", "mpegts")
            addArguments("-hls_segment_filename", "${outputDir.path}/$name%01d.ts")
            addArguments("-start_number", startSegment.toString())
            addArguments("-movflags", "+faststart")
            addArguments("-preset", "veryfast")
            addArguments("-b:a", "128000")
            addArguments("-ac:a", "2")
            addArguments("-force_key_frames", "expr:gte(t,n_forced*$segmentLength)")
            addArguments("-flush_packets", "1")
            addArgument("-start_at_zero")
            addArgument("-copyts")
            addOutput(
                UrlOutput.toPath(File(outputDir, "$name.m3u8").toPath()).apply {
                    setCodec(StreamType.VIDEO, "libx264")
                    setCodec(StreamType.AUDIO, "aac")
                }
            )
            setOverwriteOutput(true)
            setProgressListener { event ->
                transcodeProgress.tryEmit(event)
            }
        }
        val lastSegmentIndex = segmentCount - 1
        val startSession = sessionMap[token]?.copy(
            startSegment = startSegment,
            endSegment = startSegment,
            ffmpegCommand = command.toString(),
        ) ?: TranscodeSession(
            token = token,
            mediaRefId = name,
            mediaPath = mediaFile.absolutePath,
            outputPath = outputDir.absolutePath,
            ffmpegCommand = "", // TODO: Get ffmpeg cli arguments
            runtime = runtime.toDouble(SECONDS),
            segmentLength = segmentLength,
            startSegment = startSegment,
            endSegment = startSegment,
            transcodedSegments = transcodedSegments,
        )
        sessionMap[startSession.token] = startSession
        transcodeJobs[token] = scope.launch {
            transcodeProgress
                .map { event ->
                    val progress = event.timeMillis.milliseconds
                    ((startTime + progress).inWholeSeconds / segmentLength).toInt()
                }
                .distinctUntilChanged()
                .onEach { completedSegment ->
                    val session = sessionMap[token] ?: return@onEach
                    // Progress events may span multiple segments
                    val completedSegmentCount = completedSegment - session.endSegment
                    val completedSegments = if (completedSegmentCount > 1) {
                        // in that case, add all the missing segments
                        List(completedSegmentCount) { completedSegment - it }.sorted()
                    } else {
                        listOf(completedSegment)
                    }
                    logger.debug("segment completed ${completedSegments.last()} (${completedSegments.size}) of $lastSegmentIndex, session=$token")
                    val newSession = session.copy(
                        endSegment = completedSegment,
                        transcodedSegments = session.transcodedSegments + completedSegments,
                    )
                    sessionMap[session.token] = newSession
                    sessionUpdates.tryEmit(newSession)
                    if (mutex.isLocked) {
                        val targetSegmentIndex = (session.startSegment + waitForSegments).coerceAtMost(lastSegmentIndex)
                        if (newSession.transcodedSegments.contains(targetSegmentIndex)) {
                            runCatching { mutex.unlock() }
                        }
                    }
                }
                .launchIn(this)
            command.executeAwait()
        }

        mutex.withLock { /* Wait here for progress unlock */ }
        return startSession
    }

    private suspend fun setSegmentTarget(token: String, segment: Int) {
        fun isSegmentComplete(targetSegment: Int) =
            sessionMap[token]?.transcodedSegments?.contains(targetSegment) ?: false

        if (isSegmentComplete(segment)) return

        val session = sessionMap[token] ?: return
        val segmentTime = session.segmentLength * segment
        val runtime = session.runtime.seconds
        if (transcodeJobs[token]?.isActive == true) {
            val (segmentCount, _) = getSegmentCountAndFinalLength(runtime, session.segmentLength.seconds)
            val maxEndSegment = (session.endSegment + DEFAULT_WAIT_FOR_SEGMENTS * 2)
                .coerceAtMost(segmentCount)

            if (segment in session.startSegment..maxEndSegment) {
                logger.debug("Segment $segment for $token is in range, waiting")
                sessionUpdates
                    .filter { it.token == token }
                    .first { it.transcodedSegments.contains(segment) }
                logger.debug("Segment $segment ready for $token")
                return
            } else {
                logger.debug("Current session out of range for segment $segment, stopping")
                currentCoroutineContext().ensureActive()
                stopSession(token, false)
            }
        }
        logger.debug("Segment request out of range, retargeting $segment")
        startTranscode(
            token = session.token,
            name = session.mediaRefId,
            mediaFile = File(session.mediaPath),
            outputDir = File(session.outputPath),
            runtime = runtime,
            startAt = segmentTime.toDouble(),
        )
    }

    private fun createVariantPlaylist(
        name: String,
        mediaFile: File,
        token: String,
        runtime: Duration,
    ): String {
        logger.debug("Creating variant playlist for $mediaFile")

        val segmentContainer = null ?: "ts"
        val isHlsInFmp4 = segmentContainer.equals("mp4", ignoreCase = true)
        val hlsVersion = if (isHlsInFmp4) "7" else "3"

        val segmentLength = 6.seconds
        val (segmentsCount, finalSegLength) = getSegmentCountAndFinalLength(runtime, segmentLength)

        val segmentExtension = segmentContainer
        val queryString = "?token=$token"

        logger.debug("Creating $segmentsCount segments at $segmentLength, final length $finalSegLength")

        return buildString(128) {
            appendLine("#EXTM3U")
            append("#EXT-X-VERSION:")
            appendLine(hlsVersion)
            append("#EXT-X-TARGETDURATION:")
            appendLine(segmentLength.toDouble(SECONDS))
            appendLine("#EXT-X-MEDIA-SEQUENCE:0")

            if (isHlsInFmp4) {
                append("#EXT-X-MAP:URI=\"")
                append("$name-1.")
                append(segmentExtension)
                append(queryString)
                appendLine('"')
            }
            repeat(segmentsCount) { i ->
                append("#EXTINF:")
                if (i == segmentsCount - 1) {
                    append(segLenFormatter.format(finalSegLength))
                } else {
                    append(segLenFormatter.format(segmentLength.inWholeSeconds))
                }
                appendLine(", nodesc")
                append(name)
                append(i)
                append('.')
                append(segmentExtension)
                append(queryString)
                appendLine()
            }
            appendLine("#EXT-X-ENDLIST")
        }
    }

    private fun getSegmentCountAndFinalLength(
        runtime: Duration,
        segmentLength: Duration,
    ): Pair<Int, Double> {
        val wholeSegments = (runtime / segmentLength).toInt()
        val lastSegmentLength = (runtime.inWholeMilliseconds % segmentLength.inWholeMilliseconds).milliseconds
        return if (lastSegmentLength == Duration.ZERO) {
            wholeSegments to segmentLength.toDouble(SECONDS)
        } else {
            wholeSegments + 1 to lastSegmentLength.toDouble(SECONDS)
        }
    }

    private suspend fun FFmpeg.executeAwait(): FFmpegResult {
        return executeAsync().toCompletableFuture().await()
    }
}

private val segLenFormatter = DecimalFormat().apply {
    minimumFractionDigits = 4
    maximumFractionDigits = 4
}
