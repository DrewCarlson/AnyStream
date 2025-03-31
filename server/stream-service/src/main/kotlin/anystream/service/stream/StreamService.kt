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
import anystream.models.api.PlaybackSessions
import anystream.util.ObjectId
import com.github.kokorin.jaffree.LogLevel
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffmpeg.FFmpegResult
import com.github.kokorin.jaffree.ffmpeg.UrlInput
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import com.github.kokorin.jaffree.ffprobe.FFprobe
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.name
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.times

private const val DEFAULT_BUFFER_PERCENT = 0.1
private const val DEFAULT_WAIT_FOR_SEGMENTS = 3
private val DEFAULT_SEGMENT_DURATION = 6.seconds

/** The required playback seconds to store PlaybackStates. */
private val REMEMBER_STATE_THRESHOLD = 60.seconds

// HLS Spec https://datatracker.ietf.org/doc/html/rfc8216
class StreamService(
    scope: CoroutineScope,
    private val queries: StreamServiceQueries,
    private val ffmpeg: () -> FFmpeg,
    private val ffprobe: () -> FFprobe,
    private val transcodePath: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + scope.coroutineContext)
    private val sessionMap = ConcurrentHashMap<String, TranscodeSession>()
    private val transcodeJobs = ConcurrentHashMap<String, Job>()
    private val sessionUpdates = MutableSharedFlow<TranscodeSession>(
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun getSessions(): Map<String, TranscodeSession> {
        return sessionMap
    }

    suspend fun getPlaybackSessions(): PlaybackSessions {
        val playbackStates = queries.fetchPlaybackStatesByIds(sessionMap.keys().toList())
        val userIds = playbackStates.map(PlaybackState::userId).distinct()
        val users = queries.fetchUsersByIds(userIds)
        val mediaIds = playbackStates.map(PlaybackState::metadataId).distinct()
        val mediaLookups = mediaIds.associateWith { id ->
            queries.fetchMovieById(id)?.run(::MovieResponse)
                ?: queries.fetchEpisodeById(id)?.let { (episode, show) ->
                    EpisodeResponse(episode, show)
                }
        }
        @Suppress("UNCHECKED_CAST")
        return PlaybackSessions(
            playbackStates = playbackStates,
            transcodeSessions = sessionMap,
            users = users.associateBy(User::id),
            mediaLookups = mediaLookups.filterValues { it != null } as Map<String, MediaLookupResponse>,
        )
    }

    suspend fun getPlaybackState(
        mediaLinkId: String,
        userId: String,
        create: Boolean,
    ): PlaybackState? {
        val state = queries.fetchPlaybackState(mediaLinkId, userId)
        val mediaLink = checkNotNull(queries.fetchMediaLink(mediaLinkId))
        val fileAndMetadataId = mediaLink.filePath?.let { filePath ->
            File(filePath) to checkNotNull(mediaLink.metadataId)
        }
        val newState = if (create && state == null) {
            val (file, metadataId) = fileAndMetadataId ?: return null
            val runtime = getFileDuration(file).seconds
            PlaybackState(
                id = ObjectId.next(),
                mediaLinkId = mediaLinkId,
                position = 0.0,
                userId = userId,
                metadataId = metadataId,
                runtime = runtime.toDouble(SECONDS),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
            )
        } else {
            checkNotNull(state)
        }
        if (create && !sessionMap.containsKey(newState.id)) {
            val (file, _) = fileAndMetadataId ?: return null
            val output = File("$transcodePath/${newState.id}/$mediaLinkId")
            val runtimeSeconds = newState.runtime.seconds
            val positionSeconds = newState.position.seconds
            startTranscode(
                token = newState.id,
                name = mediaLinkId,
                mediaFile = file,
                outputDir = output,
                runtime = runtimeSeconds,
                startAt = positionSeconds,
                stopAt = positionSeconds + (runtimeSeconds * DEFAULT_BUFFER_PERCENT),
                segmentDuration = DEFAULT_SEGMENT_DURATION,
                waitForSegments = 1,
            )
        }
        return newState
    }

    suspend fun deletePlaybackState(playbackStateId: String): Boolean {
        return queries.deletePlaybackState(playbackStateId)
    }

    suspend fun updateStatePosition(state: PlaybackState, position: Double): Boolean {
        if (position.seconds < REMEMBER_STATE_THRESHOLD) {
            return false
        }

        return if (queries.updatePlaybackState(state.id, position)) {
            true
        } else {
            queries.insertPlaybackState(
                state.copy(
                    position = position,
                    updatedAt = Clock.System.now(),
                ),
            )
        }
    }

    suspend fun getPlaylist(mediaLinkId: String, token: String): String? {
        val mediaLink = queries.fetchMediaLink(mediaLinkId) ?: return null
        val file = mediaLink.filePath?.run(::File) ?: return null

        val runtime = getFileDuration(file).seconds
        val segmentDuration = DEFAULT_SEGMENT_DURATION
        if (!sessionMap.containsKey(token)) {
            val output = File("$transcodePath/$mediaLinkId/$token")
            startTranscode(
                token = token,
                name = mediaLinkId,
                mediaFile = file,
                outputDir = output,
                runtime = runtime,
                segmentDuration = segmentDuration,
                waitForSegments = 0,
            )
        }

        return createVariantPlaylist(
            name = mediaLinkId,
            mediaFile = file,
            token = token,
            runtime = runtime,
            segmentDuration = segmentDuration,
        )
    }

    suspend fun getFilePathForSegment(token: String, segmentFile: String): String? {
        val session = sessionMap[token] ?: return null
        val segmentIndex = segmentFile
            .substringAfter("${session.mediaLinkId}-")
            .substringBefore(".ts")
            .toInt()
        setSegmentTarget(token, segmentIndex)
        val output = File("${session.outputPath}/$segmentFile")
        return if (output.exists()) output.absolutePath else null
    }

    suspend fun stopSession(token: String, deleteOutput: Boolean) {
        val session = sessionMap.remove(token) ?: return
        // Stop ffmpeg and associated file tracking if still running
        transcodeJobs.remove(token)?.cancel()

        // Delete the PlaybackState if playback hasn't reached threshold
        val state = queries.fetchPlaybackStateById(session.token)
        if (state?.run { position.seconds < REMEMBER_STATE_THRESHOLD } == true) {
            queries.deletePlaybackState(state.id)
        }

        if (deleteOutput) {
            File(session.outputPath).delete()
        }
    }

    fun stopTranscoding(token: String) {
        transcodeJobs.remove(token)?.cancel()
        sessionMap.computeIfPresent(token) { _, session ->
            session.copy(state = TranscodeSession.State.IDLE)
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
        segmentDuration: Duration,
        startAt: Duration = ZERO,
        stopAt: Duration = runtime,
        waitForSegments: Int = DEFAULT_WAIT_FOR_SEGMENTS,
    ): TranscodeSession {
        logger.debug("Start Transcode: $name, runtime=$runtime, startAt=$startAt, stopAt=$stopAt, waitForSegments=$waitForSegments, token=$token")
        val existingSession = sessionMap[token]
        if (existingSession?.isRunning() == true) {
            stopTranscoding(token)
        }

        outputDir.apply {
            mkdirs()
            setWritable(true)
        }

        val (segmentCount, lastSegmentDuration) = getSegmentCountAndFinalLength(
            runtime,
            segmentDuration,
        )
        val transcodedSegments = findExistingSegments(outputDir, name)
        val requestedStartTime = startAt.coerceIn(ZERO, (runtime - segmentDuration))
        val requestedStartSegment =
            floor(requestedStartTime / segmentDuration).toInt().coerceAtLeast(0)
        val lastSegmentIndex = segmentCount - 1
        val startSegment = if (transcodedSegments.contains(requestedStartSegment)) {
            var nextRequiredSegment = requestedStartSegment + 1
            while (transcodedSegments.contains(nextRequiredSegment)) {
                nextRequiredSegment++
            }
            nextRequiredSegment
        } else {
            requestedStartSegment
        }
        val startOffset = startSegment - requestedStartSegment
        val endSegment = if (stopAt < runtime) {
            (ceil(stopAt / segmentDuration).toInt() + startOffset).coerceAtMost(lastSegmentIndex)
        } else {
            lastSegmentIndex
        }

        val startTime =
            (startSegment * segmentDuration).coerceIn(ZERO, runtime - lastSegmentDuration)
        val endTime = if (stopAt < runtime) {
            ((startOffset + endSegment) * segmentDuration).coerceAtMost(runtime)
        } else {
            null
        }

        logger.debug(
            "Transcoding range: segments={}..{}, time={}..{}",
            startSegment,
            endSegment,
            startTime,
            endTime ?: runtime,
        )

        if (startSegment > lastSegmentIndex) {
            // Occurs when all required segments are completed, create the session without transcoding
            return sessionMap.compute(token) { _, session ->
                session?.copy(
                    startSegment = 0,
                    endSegment = lastSegmentIndex,
                    startTime = 0.0,
                    endTime = runtime.toDouble(SECONDS),
                    lastTranscodedSegment = lastSegmentIndex,
                    state = TranscodeSession.State.COMPLETE,
                    ffmpegCommand = "",
                ) ?: TranscodeSession(
                    token = token,
                    mediaLinkId = name,
                    mediaPath = mediaFile.absolutePath,
                    outputPath = outputDir.absolutePath,
                    segmentCount = segmentCount,
                    ffmpegCommand = "",
                    runtime = runtime.toDouble(SECONDS),
                    segmentLength = segmentDuration.toInt(SECONDS),
                    lastTranscodedSegment = startSegment,
                    state = TranscodeSession.State.COMPLETE,
                    startTime = startTime.toDouble(SECONDS),
                    endTime = endTime?.toDouble(SECONDS) ?: runtime.toDouble(SECONDS),
                    startSegment = startSegment,
                    endSegment = endSegment,
                    transcodedSegments = transcodedSegments,
                )
            }.run(::checkNotNull)
        }

        val command = ffmpeg().apply {
            val startSeconds = startTime.toDouble(SECONDS).toString()
            val segmentSeconds = segmentDuration.toDouble(SECONDS).toString()
            addArguments("-f", "hls")
            // addArguments("-movflags", "+faststart")
            addArguments("-preset", "veryfast")
            addArguments("-b:a", "128000")
            addArguments("-ac:a", "2")
            addArguments("-force_key_frames", "expr:gte(t,$startSeconds+n_forced*$segmentSeconds)")
            addArguments("-start_number", startSegment.toString())
            addArguments("-hls_flags", "temp_file+independent_segments")
            addArguments("-hls_time", segmentSeconds)
            addArguments("-hls_playlist_type", "vod")
            addArguments("-hls_list_size", "0")
            addArguments("-hls_segment_type", "mpegts")
            addArguments("-hls_segment_filename", "${outputDir.absolutePath}/$name-%01d.ts")
            addArguments("-avoid_negative_ts", "0")
            addArguments("-vsync", "-1")
            addArgument("-copyts")
            addArgument("-start_at_zero")
            addInput(
                UrlInput.fromPath(mediaFile.toPath()).apply {
                    addArgument("-noaccurate_seek")
                    addArguments("-ss", startSeconds)
                    if (endTime != null) {
                        addArguments("-to", endTime.toDouble(SECONDS).toString())
                    }
                },
            )
            addOutput(
                UrlOutput.toPath(File(outputDir, "$name.m3u8").toPath()).apply {
                    setCodec(StreamType.VIDEO, "libx264")
                    setCodec(StreamType.AUDIO, "aac")
                    // copyCodec(StreamType.VIDEO)
                    // copyCodec(StreamType.AUDIO)
                },
            )
            setOverwriteOutput(false)
        }
        logger.debug("Constructed FFmpeg command: ${command.buildCommandString()}")

        val startSession = sessionMap.compute(token) { _, session ->
            session?.copy(
                startSegment = startSegment,
                endSegment = endSegment,
                startTime = startTime.toDouble(SECONDS),
                endTime = endTime?.toDouble(SECONDS) ?: runtime.toDouble(SECONDS),
                lastTranscodedSegment = startSegment,
                ffmpegCommand = command.buildCommandString(),
                state = TranscodeSession.State.RUNNING,
            ) ?: TranscodeSession(
                token = token,
                mediaLinkId = name,
                mediaPath = mediaFile.absolutePath,
                outputPath = outputDir.absolutePath,
                segmentCount = segmentCount,
                ffmpegCommand = command.buildCommandString(),
                runtime = runtime.toDouble(SECONDS),
                segmentLength = segmentDuration.toInt(SECONDS),
                lastTranscodedSegment = startSegment,
                state = TranscodeSession.State.RUNNING,
                startTime = startTime.toDouble(SECONDS),
                endTime = endTime?.toDouble(SECONDS) ?: runtime.toDouble(SECONDS),
                startSegment = startSegment,
                endSegment = endSegment,
                transcodedSegments = transcodedSegments,
            )
        }.run(::checkNotNull)
        transcodeJobs[token] =
            createTranscodeJob(name, outputDir, endSegment, lastSegmentIndex, token, command)

        return if (waitForSegments == 0) {
            checkNotNull(sessionMap[token])
        } else {
            val targetSegmentIndex = (startSegment + waitForSegments).coerceAtMost(endSegment)
            if (startSession.isSegmentComplete(targetSegmentIndex)) {
                logger.debug("Target segment already completed")
                startSession
            } else {
                logger.debug("Target segment not complete, waiting")
                sessionUpdates
                    .onStart { sessionMap[token]?.let { emit(it) } }
                    .first { session ->
                        if (session.isSegmentComplete(targetSegmentIndex)) {
                            logger.debug("Target segment completed, unlocking")
                            true
                        } else {
                            false
                        }
                    }
            }
        }
    }

    private fun createTranscodeJob(
        name: String,
        outputDir: File,
        endSegment: Int,
        lastSegmentIndex: Int,
        token: String,
        command: FFmpeg,
    ) = scope.launch {
        val nameRegex = "$name-(\\d+)\\.ts\$".toRegex()
        val segmentWatchJob = createSegmentWatcher(outputDir.toPath(), nameRegex)
            .onEach { completedSegment ->
                logger.trace("Finished transcoding segment: $completedSegment of $endSegment total=$lastSegmentIndex, token=$token")
                val session = sessionMap.compute(token) { _, session ->
                    session?.copy(
                        transcodedSegments = session.transcodedSegments + completedSegment,
                        lastTranscodedSegment = completedSegment,
                    )
                }
                sessionUpdates.tryEmit(checkNotNull(session))
            }
            .onCompletion {
                logger.debug("Progress tracking completed.")
            }
            .flowOn(Dispatchers.Default)
            .launchIn(this)
        try {
            command.executeAwait()
            logger.debug("FFmpeg process completed: token=$token")
        } catch (_: CancellationException) {
            logger.debug("FFmpeg process cancelled: token=$token")
        } catch (e: Throwable) {
            logger.error("FFmpeg process failed: token=$token", e)
            stopSession(token, false)
        } finally {
            sessionMap.compute(token) { _, session ->
                session?.copy(state = TranscodeSession.State.IDLE)
            }
            segmentWatchJob.cancel()
        }
    }

    private fun findExistingSegments(outputDir: File, name: String): List<Int> {
        val segmentFiles = outputDir.listFiles()?.toList().orEmpty()
        val nameRegex = "$name-(\\d+)\\.ts\$".toRegex()
        return segmentFiles.mapNotNull { nameRegex.find(it.name)?.groupValues?.last()?.toInt() }
            .sorted()
    }

    private suspend fun setSegmentTarget(token: String, segment: Int) {
        val session = sessionMap[token] ?: return
        val segmentTime = (session.segmentLength * segment).seconds
        val runtime = session.runtime.seconds

        suspend fun restartTranscode() = startTranscode(
            token = session.token,
            name = session.mediaLinkId,
            mediaFile = File(session.mediaPath),
            outputDir = File(session.outputPath),
            runtime = runtime,
            segmentDuration = session.segmentLength.seconds,
            startAt = segmentTime,
            stopAt = segmentTime + (session.runtime * DEFAULT_BUFFER_PERCENT).seconds,
        )

        if (session.isSegmentComplete(segment)) {
            if (segment > session.endSegment) {
                logger.debug("Transcoding will resume from the first unavailable segment after $segment, endSegment=${session.endSegment}, token=$token")
                restartTranscode()
            }
            return
        }

        if (session.state == TranscodeSession.State.RUNNING) {
            if (segment in session.startSegment..session.endSegment) {
                logger.debug("Segment $segment for $token is in range, waiting")
                sessionUpdates
                    .filter { it.token == token }
                    .onStart { sessionMap[token]?.let { emit(it) } }
                    .onEach { sessionUpdate ->
                        if (sessionUpdate.state == TranscodeSession.State.IDLE) {
                            logger.debug("Session became idle, restarting transcode.")
                            restartTranscode()
                        }
                    }
                    .first { sessionUpdate ->
                        sessionUpdate.token == token && sessionUpdate.isSegmentComplete(segment)
                    }

                logger.debug("Segment $segment ready for $token")
                return
            }
        }
        logger.debug("Segment request out of range, retargeting $segment")
        restartTranscode()
    }

    private fun createVariantPlaylist(
        name: String,
        mediaFile: File,
        token: String,
        runtime: Duration,
        segmentDuration: Duration,
    ): String {
        logger.debug("Creating variant playlist for {}", mediaFile)

        val segmentContainer = null ?: "ts"
        val isHlsInFmp4 = segmentContainer.equals("mp4", ignoreCase = true)
        val hlsVersion = if (isHlsInFmp4) "7" else "3"

        val (segmentCount, finalSegLength) = getSegmentCountAndFinalLength(runtime, segmentDuration)

        val segmentExtension = segmentContainer

        logger.debug("Creating $segmentCount segments at $segmentDuration, final length $finalSegLength")

        return buildString(128) {
            appendLine("#EXTM3U")
            append("#EXT-X-VERSION:")
            appendLine(hlsVersion)
            append("#EXT-X-TARGETDURATION:")
            appendLine(segmentDuration.toInt(SECONDS))
            appendLine("#EXT-X-MEDIA-SEQUENCE:0")

            if (isHlsInFmp4) {
                append("#EXT-X-MAP:URI=\"")
                append(token)
                append('-')
                append("$name-1.")
                append(segmentExtension)
                appendLine('"')
            }
            repeat(segmentCount) { i ->
                append("#EXTINF:")
                if (i == segmentCount - 1) {
                    append(segLenFormatter.format(finalSegLength.toDouble(SECONDS)))
                } else {
                    append(segLenFormatter.format(segmentDuration.toDouble(SECONDS)))
                }
                appendLine(", nodesc")
                append(token)
                append('-')
                append(name)
                append('-')
                append(i)
                append('.')
                append(segmentExtension)
                appendLine()
            }
            appendLine("#EXT-X-ENDLIST")
        }
    }

    private fun getSegmentCountAndFinalLength(
        runtime: Duration,
        segmentLength: Duration,
    ): Pair<Int, Duration> {
        val segments = ceil(runtime / segmentLength).toInt()
        val lastPartialSegmentLength =
            (runtime.inWholeMilliseconds % segmentLength.inWholeMilliseconds).milliseconds
        return if (lastPartialSegmentLength == ZERO) {
            segments to segmentLength
        } else {
            segments to lastPartialSegmentLength
        }
    }

    private fun createSegmentWatcher(directory: Path, fileMatchRegex: Regex): Flow<Int> {
        return callbackFlow {
            val watchService = FileSystems.getDefault().newWatchService()
            directory.register(watchService, ENTRY_CREATE)
            while (isActive) {
                val key = watchService.poll(2, TimeUnit.SECONDS)
                key?.pollEvents()?.forEach { event ->
                    val targetPath = (event.context() as? Path) ?: return@forEach
                    val fileName = directory.resolve(targetPath).name
                    if (fileMatchRegex.containsMatchIn(fileName)) {
                        val segmentIndex =
                            checkNotNull(fileMatchRegex.find(fileName)).groupValues.last().toInt()
                        send(segmentIndex)
                    }
                }
                if (key?.reset() == false) break
            }
            awaitClose {
                try {
                    watchService.close()
                } catch (e: IOException) {
                    logger.error("Failed to close File Watch Service", e)
                }
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    private fun FFmpeg.buildCommandString(): String {
        val buildArguments = FFmpeg::class.java.getDeclaredMethod("buildArguments")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (buildArguments(this) as List<String>).joinToString(
            separator = " ",
            prefix = "ffmpeg ",
        ) { if (it.contains(' ')) "\"$it\"" else it }
    }
}

private fun PlaybackState.isPastThreshold(): Boolean {
    return position.seconds < REMEMBER_STATE_THRESHOLD
}

suspend fun FFmpeg.executeAwait(): FFmpegResult {
    return executeAsync().toCompletableFuture().await()
}

private val segLenFormatter = DecimalFormat().apply {
    minimumFractionDigits = 4
    maximumFractionDigits = 4
}
