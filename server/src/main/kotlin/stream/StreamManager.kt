/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.stream

import anystream.models.TranscodeSession
import com.github.kokorin.jaffree.LogLevel
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.*
import com.github.kokorin.jaffree.ffprobe.FFprobe
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.MarkerFactory
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.DurationUnit

private const val DEFAULT_WAIT_FOR_SEGMENTS = 8

// HLS Spec https://datatracker.ietf.org/doc/html/rfc8216
class StreamManager(
    private val ffmpeg: () -> FFmpeg,
    private val ffprobe: () -> FFprobe,
    private val logger: Logger,
) {

    private val classMarker = MarkerFactory.getMarker(this::class.simpleName)
    private val scope = CoroutineScope(IO + SupervisorJob())
    private val sessionMap = ConcurrentHashMap<String, TranscodeSession>()
    private val transcodeJobs = ConcurrentHashMap<String, Job>()
    private val sessionUpdates = MutableSharedFlow<TranscodeSession>(
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun dispose() {
        scope.cancel()
        sessionMap.clear()
    }

    fun hasSession(token: String): Boolean {
        return sessionMap.containsKey(token)
    }

    fun getSession(token: String): TranscodeSession? {
        return sessionMap[token]
    }

    fun getSessions(): List<TranscodeSession> {
        return sessionMap.values.toList()
    }

    suspend fun getFileDuration(mediaFile: File): Double {
        return IO {
            ffprobe()
                .setLogLevel(LogLevel.QUIET)
                .setShowEntries("format=duration")
                .setInput(mediaFile.toPath())
                .execute()
        }.data.getSubDataDouble("format", "duration")
    }

    suspend fun startTranscode(
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
        val requestedStartTime = (startAt - segmentLength)
            .coerceAtLeast(0.0)
            .run(Duration::seconds)
        val requestedStartSegment = (requestedStartTime.inWholeSeconds / segmentLength)
            .toInt()
            .coerceAtLeast(0)
        val (segmentCount, lastSegmentDuration) = getSegmentCountAndFinalLength(
            runtime,
            Duration.seconds(segmentLength),
        )
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
                .coerceAtMost(runtime.toDouble(DurationUnit.SECONDS) - lastSegmentDuration)
                .run(Duration::seconds)
        }
        val command = ffmpeg().apply {
            addInput(
                UrlInput.fromPath(mediaFile.toPath())
                    .addArguments("-ss", startTime.toDouble(DurationUnit.SECONDS).toString())
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
        val session = sessionMap[token]?.copy(
            startSegment = startSegment,
            endSegment = startSegment,
            ffmpegCommand = command.toString(),
        ) ?: TranscodeSession(
            token = token,
            mediaRefId = name,
            mediaPath = mediaFile.absolutePath,
            outputPath = outputDir.absolutePath,
            ffmpegCommand = "", // TODO: Get ffmpeg cli arguments
            runtime = runtime.toDouble(DurationUnit.SECONDS),
            segmentLength = segmentLength,
            startSegment = startSegment,
            endSegment = startSegment,
            transcodedSegments = transcodedSegments,
        )
        transcodeJobs[token] = scope.launch {
            transcodeProgress
                .filter { event -> event.timeMillis > 0 }
                .map { event ->
                    val progress = Duration.milliseconds(event.timeMillis)
                    if (progress == runtime) {
                        segmentCount
                    } else {
                        (progress.inWholeSeconds / segmentLength).toInt()
                    }
                }
                .distinctUntilChanged()
                .onEach { completedSegment ->
                    getSession(token)?.also { session ->
                        // Progress events may span multiple segments
                        val completedSegmentCount = completedSegment - session.endSegment
                        val completedSegments = if (completedSegmentCount > 1) {
                            // in that case, add all the missing segments
                            List(completedSegmentCount) { completedSegment - it }
                        } else listOf(completedSegment)
                        logger.debug("segment completed $completedSegments : $token")
                        sessionMap[session.token] = session.copy(
                            endSegment = completedSegment,
                            transcodedSegments = (session.transcodedSegments + completedSegments).sorted(),
                        ).also { sessionUpdates.tryEmit(it) }
                        if (mutex.isLocked) {
                            if (completedSegment >= session.startSegment + waitForSegments) {
                                runCatching { mutex.unlock() }
                            }
                        }
                    }
                }
                .launchIn(this)
            command.executeAwait()
        }

        sessionMap[session.token] = session
        mutex.withLock { /* Wait here for progress unlock */ }
        return session
    }

    fun stopSession(token: String, deleteOutput: Boolean) {
        sessionMap.remove(token)?.also { session ->
            transcodeJobs.remove(token)?.cancel()
            if (deleteOutput) {
                File(session.outputPath).delete()
            }
        }
    }

    suspend fun setSegmentTarget(token: String, segment: Int) {
        fun isSegmentComplete(targetSegment: Int) =
            getSession(token)?.transcodedSegments?.contains(targetSegment) ?: false

        if (isSegmentComplete(segment)) return

        val session = sessionMap[token] ?: return
        val segmentTime = session.segmentLength * segment
        val runtime = Duration.seconds(session.runtime)
        if (transcodeJobs[token]?.isActive == true) {
            val (segmentCount, _) = getSegmentCountAndFinalLength(
                runtime,
                Duration.seconds(session.segmentLength),
            )
            val maxEndSegment = (session.endSegment + DEFAULT_WAIT_FOR_SEGMENTS * 2)
                .coerceAtMost(segmentCount)

            if (segment in session.startSegment..maxEndSegment) {
                logger.debug(classMarker, "Segment $segment for $token is in range, waiting")
                sessionUpdates
                    .filter { it.token == token }
                    .first { it.transcodedSegments.contains(segment) }
                logger.debug(classMarker, "Segment $segment ready for $token")
                return
            } else {
                logger.debug(
                    classMarker,
                    "Current session out of range for segment $segment, stopping"
                )
                currentCoroutineContext().ensureActive()
                stopSession(token, false)
            }
        }
        logger.debug(classMarker, "Segment request out of range, retargeting $segment")
        startTranscode(
            token = session.token,
            name = session.mediaRefId,
            mediaFile = File(session.mediaPath),
            outputDir = File(session.outputPath),
            runtime = runtime,
            startAt = segmentTime.toDouble(),
        )
    }

    fun createVariantPlaylist(
        name: String,
        mediaFile: File,
        token: String,
        runtime: Duration,
    ): String {
        val marker = MarkerFactory.getMarker(name).apply { add(classMarker) }

        logger.debug(marker, "Creating variant playlist for $mediaFile")

        val segmentContainer = null ?: "ts"
        val isHlsInFmp4 = segmentContainer.equals("mp4", ignoreCase = true)
        val hlsVersion = if (isHlsInFmp4) "7" else "3"

        val segmentLength = Duration.seconds(6)
        val (segmentsCount, finalSegLength) = getSegmentCountAndFinalLength(runtime, segmentLength)

        val segmentExtension = segmentContainer
        val queryString = "?token=$token"

        logger.debug(
            marker,
            "Creating $segmentsCount segments at $segmentLength, final length $finalSegLength"
        )

        return buildString(128) {
            appendLine("#EXTM3U")
            append("#EXT-X-VERSION:")
            appendLine(hlsVersion)
            append("#EXT-X-TARGETDURATION:")
            appendLine(segmentLength)
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
        val lastSegmentLength = Duration.milliseconds(
            runtime.inWholeMilliseconds % segmentLength.inWholeMilliseconds
        )
        return if (lastSegmentLength == Duration.ZERO) {
            wholeSegments to segmentLength.toDouble(DurationUnit.SECONDS)
        } else {
            wholeSegments + 1 to lastSegmentLength.toDouble(DurationUnit.SECONDS)
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