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
import com.github.kokorin.jaffree.ffmpeg.UrlInput
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import com.github.kokorin.jaffree.ffprobe.FFprobe
import com.github.kokorin.jaffree.process.CommandSender
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.times

private val DEFAULT_THROTTLE_SECONDS = 120.seconds
private val DEFAULT_SEGMENT_DURATION = 6.seconds

/** The required playback seconds to store PlaybackStates. */
private val REMEMBER_STATE_THRESHOLD = 60.seconds

// HLS Spec https://datatracker.ietf.org/doc/html/rfc8216
class StreamService(
    scope: CoroutineScope,
    private val queries: StreamServiceQueries,
    private val ffmpeg: () -> FFmpeg,
    private val ffprobe: () -> FFprobe,
    private val transcodePath: Path,
    private val fs: FileSystem,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val hlsPlaylistFactory = HlsPlaylistFactory()
    private val scope = CoroutineScope(IO + scope.coroutineContext)
    private val sessionMap = ConcurrentHashMap<String, TranscodeSession>()
    private val transcodeJobs = ConcurrentHashMap<String, TranscodeJobHolder>()
    private val newPlaybackStates = ConcurrentHashMap<String, PlaybackState>()
    private val sessionUpdates = MutableSharedFlow<TranscodeSession>(
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun getSessions(): Map<String, TranscodeSession> {
        return sessionMap
    }

    suspend fun getPlaybackSessions(): PlaybackSessions {
        val sessionKeys = sessionMap.keys().toList()
        val storedPlaybackStates = queries.fetchPlaybackStatesByIds(sessionKeys)
        val newPlaybackStates = newPlaybackStates.mapNotNull { (id, state) ->
            if (storedPlaybackStates.any { it.id == id }) {
                newPlaybackStates.remove(id)
                null
            } else {
                state
            }
        }
        val playbackStates = storedPlaybackStates + newPlaybackStates
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
            users = users.map(User::toPublic).associateBy(UserPublic::id),
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
            fs.getPath(filePath) to checkNotNull(mediaLink.metadataId)
        }
        val newState = if (create && state == null) {
            val (file, metadataId) = fileAndMetadataId ?: return null
            val runtime = getFileDuration(file).seconds
            PlaybackState(
                id = ObjectId.next(),
                mediaLinkId = mediaLinkId,
                position = ZERO,
                userId = userId,
                metadataId = metadataId,
                runtime = runtime,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
            ).also { newPlaybackStates[it.id] = it }
        } else {
            checkNotNull(state)
        }
        if (create && !sessionMap.containsKey(newState.id)) {
            val (file, _) = fileAndMetadataId ?: return null
            val output = transcodePath.resolve(newState.id).resolve(mediaLinkId)
            startTranscode(
                token = newState.id,
                name = mediaLinkId,
                mediaFile = file,
                outputDir = output,
                runtime = newState.runtime,
                startAt = newState.position,
                stopAt = newState.position + DEFAULT_THROTTLE_SECONDS,
                segmentDuration = DEFAULT_SEGMENT_DURATION,
            )
        }
        return newState
    }

    suspend fun deletePlaybackState(playbackStateId: String): Boolean {
        return queries.deletePlaybackState(playbackStateId)
    }

    suspend fun updateStatePosition(state: PlaybackState, position: Duration): Boolean {
        if (!position.isPastThreshold()) {
            newPlaybackStates.compute(state.id) { _, existing ->
                (existing ?: state).copy(position = position)
            }
            return false
        }
        if (queries.updatePlaybackState(state.id, position)) {
            return true
        }
        return queries.insertPlaybackState(
            state.copy(
                position = position,
                updatedAt = Clock.System.now(),
            ),
        )
    }

    suspend fun getPlaylist(mediaLinkId: String, token: String): String? {
        val mediaLink = queries.fetchMediaLink(mediaLinkId) ?: return null
        val file = mediaLink.filePath?.run(fs::getPath) ?: return null

        val runtime = getFileDuration(file).seconds
        val segmentDuration = DEFAULT_SEGMENT_DURATION
        if (!sessionMap.containsKey(token)) {
            val output = transcodePath.resolve(token).resolve(mediaLinkId)
            startTranscode(
                token = token,
                name = mediaLinkId,
                mediaFile = file,
                outputDir = output,
                runtime = runtime,
                segmentDuration = segmentDuration,
            )
        }

        return hlsPlaylistFactory.createVariantPlaylist(
            name = mediaLinkId,
            mediaFile = file,
            token = token,
            runtime = runtime,
            segmentDuration = segmentDuration,
        )
    }

    suspend fun getFilePathForSegment(token: String, segmentFile: String): Path? {
        val session = sessionMap[token] ?: return null
        val segmentIndex = segmentFile
            .substringAfter("${session.mediaLinkId}-")
            .substringBefore(".ts")
            .toInt()
        setSegmentTarget(token, segmentIndex)
        return fs.getPath(session.outputPath)
            .resolve(segmentFile)
            .takeIf(Path::exists)
    }

    suspend fun stopSession(token: String, deleteOutput: Boolean) {
        newPlaybackStates.remove(token)
        val session = sessionMap.remove(token) ?: return
        // Stop ffmpeg and associated file tracking if still running
        transcodeJobs.remove(token)?.cancel()

        // Delete the PlaybackState if playback hasn't reached threshold
        val state = queries.fetchPlaybackStateById(session.token)
        if (state?.position?.isPastThreshold() == false) {
            queries.deletePlaybackState(state.id)
        }

        if (deleteOutput) {
            val sessionDir = fs.getPath(session.outputPath)
            if (sessionDir.exists() && sessionDir.isDirectory()) {
                sessionDir.deleteRecursively()
            }
        }
    }

    private fun stopTranscoding(token: String) {
        transcodeJobs.remove(token)?.cancel()
        sessionMap.computeIfPresent(token) { _, session ->
            session.copy(state = TranscodeSession.State.IDLE)
        }
    }

    private suspend fun getFileDuration(mediaFile: Path): Double {
        val probeData = withContext(IO) {
            ffprobe()
                .setLogLevel(LogLevel.QUIET)
                .setShowEntries("format=duration")
                .setInput(mediaFile)
                .execute()
                .data
        }
        return probeData.getSubDataDouble("format", "duration")
    }

    private suspend fun startTranscode(
        token: String,
        name: String,
        mediaFile: Path,
        outputDir: Path,
        runtime: Duration,
        segmentDuration: Duration,
        startAt: Duration = ZERO,
        stopAt: Duration = runtime,
    ): TranscodeSession {
        logger.debug(
            "Start Transcode: {}, runtime={}, startAt={}, stopAt={}, token={}",
            name,
            runtime,
            startAt,
            stopAt,
            token,
        )
        val existingSession = sessionMap[token]
        if (existingSession?.isActive() == true) {
            stopTranscoding(token)
        }

        outputDir.createDirectories()

        val (segmentCount, lastSegmentDuration) =
            hlsPlaylistFactory.getSegmentCountAndFinalLength(runtime, segmentDuration)
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
            runtime
        }

        logger.debug(
            "Transcoding range: segments={}..{}, time={}..{}",
            startSegment,
            endSegment,
            startTime,
            endTime,
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
                    mediaPath = mediaFile.absolutePathString(),
                    outputPath = outputDir.absolutePathString(),
                    segmentCount = segmentCount,
                    ffmpegCommand = "",
                    runtime = runtime.toDouble(SECONDS),
                    segmentLength = segmentDuration.toInt(SECONDS),
                    lastTranscodedSegment = startSegment,
                    state = TranscodeSession.State.COMPLETE,
                    startTime = startTime.toDouble(SECONDS),
                    endTime = endTime.toDouble(SECONDS),
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
            addArguments("-hls_segment_filename", outputDir.resolve("$name-%01d.ts").absolutePathString())
            addArguments("-avoid_negative_ts", "0")
            addArguments("-vsync", "-1")
            addArgument("-copyts")
            addArgument("-start_at_zero")
            addInput(
                UrlInput.fromPath(mediaFile).apply {
                    addArgument("-noaccurate_seek")
                    addArguments("-ss", startSeconds)
                },
            )
            addOutput(
                UrlOutput.toPath(outputDir.resolve("$name.m3u8")).apply {
                    setCodec(StreamType.VIDEO, "libx264")
                    setCodec(StreamType.AUDIO, "aac")
                    addArguments("-profile:v", "main")
                    addArguments("-pix_fmt", "yuv420p")
                    // copyCodec(StreamType.VIDEO)
                    // copyCodec(StreamType.AUDIO)
                },
            )
            setOverwriteOutput(false)
        }
        logger.debug("Constructed FFmpeg command: {}", command.buildCommandString())

        val startSession = sessionMap.compute(token) { _, session ->
            session?.copy(
                startSegment = startSegment,
                endSegment = endSegment,
                startTime = startTime.toDouble(SECONDS),
                endTime = endTime.toDouble(SECONDS),
                lastTranscodedSegment = startSegment,
                ffmpegCommand = command.buildCommandString(),
                state = TranscodeSession.State.RUNNING,
            ) ?: TranscodeSession(
                token = token,
                mediaLinkId = name,
                mediaPath = mediaFile.absolutePathString(),
                outputPath = outputDir.absolutePathString(),
                segmentCount = segmentCount,
                ffmpegCommand = command.buildCommandString(),
                runtime = runtime.toDouble(SECONDS),
                segmentLength = segmentDuration.toInt(SECONDS),
                lastTranscodedSegment = startSegment,
                state = TranscodeSession.State.RUNNING,
                startTime = startTime.toDouble(SECONDS),
                endTime = endTime.toDouble(SECONDS),
                startSegment = startSegment,
                endSegment = endSegment,
                transcodedSegments = transcodedSegments,
            )
        }
        transcodeJobs[token] = createTranscodeJob(name, outputDir, token, command)

        val waitForSegment = (startSegment + 1).coerceAtMost(endSegment)
        return if (checkNotNull(startSession).isSegmentComplete(waitForSegment)) {
            logger.debug("Target segment already completed")
            startSession
        } else {
            logger.debug("Target segment not complete, waiting")
            sessionUpdates
                .onStart { sessionMap[token]?.let { emit(it) } }
                .first { session ->
                    if (session.isSegmentComplete(waitForSegment)) {
                        logger.debug("Target segment completed, unlocking")
                        true
                    } else {
                        false
                    }
                }
        }
    }

    private fun createTranscodeJob(
        name: String,
        outputDir: Path,
        token: String,
        command: FFmpeg,
    ): TranscodeJobHolder {
        val commandSender = CommandSender()
        val nameRegex = "$name-(\\d+)\\.ts\$".toRegex()
        val transcodeJob = scope.launch {
            createSegmentWatcher(outputDir, nameRegex)
                .onEach { completedSegmentIndex ->
                    val currentSession = checkNotNull(sessionMap[token])
                    val endSegment = currentSession.endSegment
                    logger.debug(
                        "Finished transcoding segment: {} of {} total={}, token={}",
                        completedSegmentIndex,
                        endSegment,
                        currentSession.segmentCount,
                        token,
                    )
                    val pause = endSegment == completedSegmentIndex
                    if (pause) {
                        logger.debug("Pausing transcode {}", token)
                        checkNotNull(commandSender.sendCommand("p"))
                    }
                    val session = sessionMap.compute(token) { _, session ->
                        session?.copy(
                            endSegment = maxOf(session.endSegment, completedSegmentIndex),
                            transcodedSegments = session.transcodedSegments + completedSegmentIndex,
                            lastTranscodedSegment = completedSegmentIndex,
                            state = if (pause) TranscodeSession.State.PAUSED else session.state,
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
                command.executeAwait(commandSender)
                logger.debug("FFmpeg process completed: token={}", token)
            } catch (e: CancellationException) {
                logger.debug("FFmpeg process cancelled: token={}", token)
            } catch (e: Throwable) {
                logger.error("FFmpeg process failed: token=$token", e)
                stopSession(token, false)
            } finally {
                logger.debug("FFmpeg process is gone moving to IDLE: token={}", token)
                sessionMap.compute(token) { _, session ->
                    session?.copy(state = TranscodeSession.State.IDLE)
                }
                cancel()
            }
        }
        return TranscodeJobHolder(transcodeJob, commandSender)
    }

    private fun findExistingSegments(outputDir: Path, name: String): List<Int> {
        val nameRegex = "$name-(\\d+)\\.ts\$".toRegex()
        return outputDir
            .listDirectoryEntries()
            .mapNotNull { nameRegex.find(it.name)?.groupValues?.lastOrNull()?.toInt() }
            .sorted()
    }

    private suspend fun setSegmentTarget(token: String, segment: Int) {
        val session = sessionMap[token] ?: return
        val transcodeJob = transcodeJobs[token] ?: return
        val segmentTime = (session.segmentLength * segment).seconds
        val runtime = session.runtime.seconds
        val stopAt = segmentTime + DEFAULT_THROTTLE_SECONDS

        suspend fun restartTranscode() = startTranscode(
            token = session.token,
            name = session.mediaLinkId,
            mediaFile = fs.getPath(session.mediaPath),
            outputDir = fs.getPath(session.outputPath),
            runtime = runtime,
            segmentDuration = session.segmentLength.seconds,
            startAt = segmentTime,
            stopAt = stopAt,
        )

        if (session.isSegmentComplete(segment)) {
            if (segment > session.lastTranscodedSegment && session.state != TranscodeSession.State.PAUSED) {
                logger.debug(
                    "Transcoding will resume from the first unavailable segment after {}, endSegment={}, token={}",
                    segment,
                    session.endSegment,
                    token,
                )
                restartTranscode()
            }
            return
        }

        when (session.state) {
            TranscodeSession.State.IDLE,
            TranscodeSession.State.COMPLETE -> Unit

            TranscodeSession.State.RUNNING -> {
                if (segment in session.startSegment..session.endSegment) {
                    logger.debug("Segment {} for {} is in range, waiting", segment, token)
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

                    logger.debug("Segment {} ready for {}", segment, token)
                    return
                }

            }

            TranscodeSession.State.PAUSED -> {
                val updatedSession = sessionMap.compute(token) { _, currentSession ->
                    currentSession?.run {
                        val endSegment = currentSession.run {
                            lastTranscodedSegment + (endSegment - startSegment)
                        }.coerceAtMost(currentSession.segmentCount)
                        val endTime = (endSegment * currentSession.segmentLength.toDouble())
                            .coerceAtMost(runtime.toDouble(SECONDS))
                        copy(
                            state = TranscodeSession.State.RUNNING,
                            endSegment = endSegment,
                            endTime = endTime,
                        )
                    }
                }
                logger.debug("Resuming transcode, retargeting {}", updatedSession?.endSegment)
                transcodeJob.resume()
                return
            }
        }
        logger.debug("Segment request out of range, retargeting {}", segment)
        restartTranscode()
    }

    private fun createSegmentWatcher(directory: Path, fileMatchRegex: Regex): Flow<Int> {
        return callbackFlow {
            val watchService = fs.newWatchService()
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
        }.distinctUntilChanged().flowOn(IO)
    }
}

private fun Duration.isPastThreshold(): Boolean {
    return this > REMEMBER_STATE_THRESHOLD
}
