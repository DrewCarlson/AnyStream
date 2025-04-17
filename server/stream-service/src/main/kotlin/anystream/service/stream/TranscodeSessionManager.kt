/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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

import anystream.models.PlaybackState
import anystream.models.TranscodeDecision
import anystream.models.TranscodeSession
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffmpeg.UrlInput
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import com.github.kokorin.jaffree.process.CommandSender
import dev.drewhamilton.poko.Poko
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.times

class TranscodeSessionManager(
    private val ffmpeg: () -> FFmpeg,
    private val mediaFileProbe: MediaFileProbe,
    private val queries: StreamServiceQueries,
    private val fs: FileSystem,
) {
    private val scope = CoroutineScope(SupervisorJob() + Default)
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val transcodeJobs = ConcurrentHashMap<String, TranscodeJobHolder>()
    private val hlsPlaylistFactory = HlsPlaylistFactory()
    private val sessionMap = ConcurrentHashMap<String, TranscodeSession>()
    private val newPlaybackStates = ConcurrentHashMap<String, PlaybackState>()

    private val sessionUpdates = MutableSharedFlow<TranscodeSession>(
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun allSessionIds(): List<String> = sessionMap.keys.toList()

    fun temporaryPlaybackStates(): List<PlaybackState> = newPlaybackStates.values.toList()

    fun getSessionMap() = sessionMap.toMap()

    suspend fun startTranscode(
        token: String,
        name: String,
        mediaFile: Path,
        outputDir: Path,
        runtime: Duration,
        segmentDuration: Duration,
        startAt: Duration = ZERO,
        stopAt: Duration = runtime,
        transcodeDecision: TranscodeDecision,
        isHlsInFmp4: Boolean,
        initialState: PlaybackState? = null,
    ): TranscodeSession {
        logger.debug(
            "Start Transcode: {}, runtime={}, startAt={}, stopAt={}, token={}, transcodeDecision={}",
            name,
            runtime,
            startAt,
            stopAt,
            token,
            transcodeDecision,
        )
        val existingSession = sessionMap[token]
        if (existingSession?.isActive() == true) {
            stopTranscoding(token)
        }

        outputDir.createDirectories()
        initialState?.let { newPlaybackStates[token] = it }

        val flags = createTranscodeFlags(
            isHlsInFmp4 = isHlsInFmp4,
            segmentDuration = segmentDuration,
            startAt = startAt,
            stopAt = stopAt,
            runtime = runtime,
            outputDir = outputDir,
            name = name,
        )

        if (transcodeDecision == TranscodeDecision.DIRECT) {
            logger.debug("Direct streaming enabled for {}, copying codecs without transcoding", name)
        }

        if (flags.startSegment > flags.finalSegmentIndex) {
            // Occurs when all required segments are completed, create the session without transcoding
            return sessionMap.compute(token) { _, session ->
                session?.copy(
                    startSegment = 0,
                    endSegment = flags.finalSegmentIndex,
                    startTime = 0.0,
                    endTime = runtime.toDouble(SECONDS),
                    lastTranscodedSegment = flags.finalSegmentIndex,
                    state = TranscodeSession.State.COMPLETE,
                    ffmpegCommand = "",
                    transcodeDecision = transcodeDecision,
                ) ?: TranscodeSession(
                    token = token,
                    mediaLinkId = name,
                    mediaPath = mediaFile.absolutePathString(),
                    outputPath = outputDir.absolutePathString(),
                    segmentCount = flags.segmentCount,
                    ffmpegCommand = "",
                    runtime = runtime.toDouble(SECONDS),
                    segmentLength = segmentDuration.toInt(SECONDS),
                    lastTranscodedSegment = flags.startSegment,
                    state = TranscodeSession.State.COMPLETE,
                    startTime = flags.startTime.toDouble(SECONDS),
                    endTime = flags.endTime.toDouble(SECONDS),
                    startSegment = flags.startSegment,
                    endSegment = flags.endSegment,
                    transcodedSegments = flags.completedSegments,
                    transcodeDecision = transcodeDecision,
                )
            }.run(::checkNotNull)
        }

        val command = ffmpeg().apply {
            val startSeconds = flags.startTime.toDouble(SECONDS).toString()
            val segmentSeconds = segmentDuration.toDouble(SECONDS).toString()
            addArguments("-f", "hls")

            //addArguments("-movflags", "+faststart")
            //addArguments("-preset", "veryfast")

            //addArguments("-force_key_frames", "expr:gte(t,$startSeconds+n_forced*$segmentSeconds)")
            addArguments("-start_number", flags.startSegment.toString())
            addArguments("-hls_flags", "temp_file+independent_segments")
            addArguments("-hls_time", segmentSeconds)
            addArguments("-hls_playlist_type", "vod")
            addArguments("-hls_list_size", "0")
            addArguments("-hls_segment_type", if (isHlsInFmp4) "fmp4" else "mpegts")
            if (isHlsInFmp4) {
                addArguments("-hls_fmp4_init_filename", "$name-init.${flags.segmentExtension}")
            }
            addArguments(
                "-hls_segment_filename",
                outputDir.resolve("$name-%01d.${flags.segmentExtension}").absolutePathString()
            )
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
            logger.debug("Transcoding decision: {}", transcodeDecision)
            addOutput(
                UrlOutput.toPath(outputDir.resolve("$name.m3u8")).apply {
                    if (transcodeDecision.shouldTranscodeVideo) {
                        setCodec(StreamType.VIDEO, "libx264")
                        addArguments("-profile:v", "main")
                        addArguments("-pix_fmt", "yuv420p")
                    } else {
                        copyCodec(StreamType.VIDEO)
                    }

                    if (transcodeDecision.shouldTranscodeAudio) {
                        setCodec(StreamType.AUDIO, "aac")
                        addArguments("-b:a", "128000")
                        addArguments("-ac:a", "2")
                    } else {
                        copyCodec(StreamType.AUDIO)
                    }
                },
            )
            setOverwriteOutput(false)
        }
        logger.debug("Constructed FFmpeg command: {}", command.buildCommandString())

        val startSession = sessionMap.compute(token) { _, session ->
            session?.copy(
                startSegment = flags.startSegment,
                endSegment = flags.endSegment,
                startTime = flags.startTime.toDouble(SECONDS),
                endTime = flags.endTime.toDouble(SECONDS),
                lastTranscodedSegment = flags.startSegment,
                ffmpegCommand = command.buildCommandString(),
                state = TranscodeSession.State.RUNNING,
            ) ?: TranscodeSession(
                token = token,
                mediaLinkId = name,
                mediaPath = mediaFile.absolutePathString(),
                outputPath = outputDir.absolutePathString(),
                segmentCount = flags.segmentCount,
                ffmpegCommand = command.buildCommandString(),
                runtime = runtime.toDouble(SECONDS),
                segmentLength = segmentDuration.toInt(SECONDS),
                lastTranscodedSegment = flags.startSegment,
                state = TranscodeSession.State.RUNNING,
                startTime = flags.startTime.toDouble(SECONDS),
                endTime = flags.endTime.toDouble(SECONDS),
                startSegment = flags.startSegment,
                endSegment = flags.endSegment,
                transcodedSegments = flags.completedSegments,
                transcodeDecision = transcodeDecision,
            )
        }
        transcodeJobs[token] = createTranscodeJob(name, outputDir, token, command, flags.segmentExtension)

        val waitForSegment = (flags.startSegment + 1).coerceAtMost(flags.endSegment)
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

    @Poko
    class TranscodeFlags(
        val startSegment: Int,
        val endSegment: Int,
        val startTime: Duration,
        val endTime: Duration,
        val segmentCount: Int,
        val completedSegments: List<Int>,
        val segmentExtension: String,
        val finalSegmentIndex: Int,
    )

    private fun createTranscodeFlags(
        isHlsInFmp4: Boolean,
        runtime: Duration,
        segmentDuration: Duration,
        outputDir: Path,
        name: String,
        startAt: Duration,
        stopAt: Duration,
    ): TranscodeFlags {
        val segmentExtension = if (isHlsInFmp4) "mp4" else "ts"
        val (segmentCount, lastSegmentDuration) =
            hlsPlaylistFactory.getSegmentCountAndFinalLength(runtime, segmentDuration)
        val completedSegments = findExistingSegments(outputDir, name, segmentExtension)
        val requestedStartTime = startAt.coerceIn(ZERO, (runtime - segmentDuration))
        val requestedStartSegment =
            floor(requestedStartTime / segmentDuration).toInt().coerceAtLeast(0)
        val lastSegmentIndex = segmentCount - 1
        val startSegment = if (completedSegments.contains(requestedStartSegment)) {
            var nextRequiredSegment = requestedStartSegment + 1
            while (completedSegments.contains(nextRequiredSegment)) {
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
        return TranscodeFlags(
            segmentExtension = segmentExtension,
            startSegment = startSegment,
            endSegment = endSegment,
            startTime = startTime,
            endTime = endTime,
            segmentCount = segmentCount,
            completedSegments = completedSegments,
            finalSegmentIndex = lastSegmentIndex,
        )
    }

    private suspend fun setSegmentTarget(token: String, segment: Int) {
        val session = sessionMap[token] ?: return
        val transcodeJob = transcodeJobs[token] ?: return
        val segmentTime = (session.segmentLength * segment).seconds
        val runtime = session.runtime.seconds
        val stopAt = segmentTime + DEFAULT_THROTTLE_SECONDS

        suspend fun restartTranscode() {
            val mediaFile = fs.getPath(session.mediaPath)
            val mediaInfo = mediaFileProbe.probeFile(mediaFile)
            val isHlsInFmp4 = mediaInfo?.isHlsInFmp4 ?: false

            logger.debug(
                "Media info for {}: codec={}, isHlsInFmp4={}",
                session.mediaLinkId,
                mediaInfo?.videoCodec,
                isHlsInFmp4
            )

            startTranscode(
                token = session.token,
                name = session.mediaLinkId,
                mediaFile = mediaFile,
                outputDir = fs.getPath(session.outputPath),
                runtime = runtime,
                segmentDuration = session.segmentLength.seconds,
                startAt = segmentTime,
                stopAt = stopAt,
                transcodeDecision = session.transcodeDecision,
                isHlsInFmp4 = isHlsInFmp4
            )
        }

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

    private fun createTranscodeJob(
        name: String,
        outputDir: Path,
        token: String,
        command: FFmpeg,
        extension: String,
    ): TranscodeJobHolder {
        val commandSender = CommandSender()
        val nameRegex = "$name-(\\d+)\\.$extension\$".toRegex()
        val transcodeJob = scope.launch {
            fs.observeNewSegmentFiles(outputDir, nameRegex)
                .onEach { completedSegmentIndex ->
                    val currentSession = checkNotNull(sessionMap[token])
                    val endSegment = currentSession.endSegment
                    logger.trace(
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
                .flowOn(Default)
                .launchIn(this)
            val handle = command.executeAsync(commandSender)
            val future = handle.toCompletableFuture()
            try {
                future.await()
                logger.debug("FFmpeg process completed: token={}", token)
            } catch (e: CancellationException) {
                logger.debug("FFmpeg process cancelled: token={}", token)
                throw e
            } catch (e: Throwable) {
                logger.error("FFmpeg process failed: token=$token", e)
                stopSession(token, false)
            } finally {
                logger.debug("FFmpeg process is gone moving to IDLE: token={}", token)
                handle.graceStop()
                sessionMap.compute(token) { _, session ->
                    session?.copy(state = TranscodeSession.State.IDLE)
                }
            }
        }
        return TranscodeJobHolder(transcodeJob, commandSender)
    }

    private fun findExistingSegments(outputDir: Path, name: String, segmentExtension: String): List<Int> {
        val nameRegex = "$name-(\\d+)\\.$segmentExtension$".toRegex()
        return outputDir
            .listDirectoryEntries()
            .mapNotNull { nameRegex.find(it.name)?.groupValues?.lastOrNull()?.toInt() }
            .sorted()
    }

    suspend fun stopSession(token: String, deleteOutput: Boolean) {
        logger.debug("Stopping session: token={}, deleteOutput=$deleteOutput", token)
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
                sessionDir.listDirectoryEntries()
                    .forEach { it.deleteRecursively() }
                sessionDir.deleteRecursively()
            }
        }
    }

    suspend fun getFilePathForSegment(token: String, segmentFile: String): Path? {
        val session = sessionMap[token] ?: return null

        val segmentIndex = segmentFile
            .substringAfter("${session.mediaLinkId}-")
            .substringBeforeLast(".")
            .toIntOrNull()

        if (segmentIndex != null) {
            setSegmentTarget(token, segmentIndex)
        }
        return fs.getPath(session.outputPath)
            .resolve(segmentFile)
            .takeIf(Path::exists)
    }

    private suspend fun stopTranscoding(token: String) {
        transcodeJobs.remove(token)?.cancel()
        sessionMap.computeIfPresent(token) { _, session ->
            session.copy(state = TranscodeSession.State.IDLE)
        }
    }

    fun updateState(id: String, position: Duration) {
        newPlaybackStates.computeIfPresent(id) { _, existing ->
            existing.copy(position = position)
        }
    }

    fun removeTemporaryState(id: String) {
        newPlaybackStates.remove(id)
    }
}