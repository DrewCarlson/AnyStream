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

import com.github.kokorin.jaffree.LogLevel
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.*
import com.github.kokorin.jaffree.ffprobe.FFprobe
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.MarkerFactory
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit

private const val DEFAULT_WAIT_FOR_SEGMENTS = 10

// HLS Spec https://datatracker.ietf.org/doc/html/rfc8216
class StreamManager(
    private val ffmpeg: () -> FFmpeg,
    private val ffprobe: () -> FFprobe,
    private val logger: Logger,
) {
    data class TranscodeSession(
        val token: String,
        val name: String,
        val mediaPath: String,
        val outputPath: String,
        val ffmpegCommand: String,
        val mediaDuration: Double,
        val job: Job? = null,
    )

    private val classMarker = MarkerFactory.getMarker(this::class.simpleName)
    private val scope = CoroutineScope(IO + SupervisorJob())
    private val sessionMap = ConcurrentHashMap<String, TranscodeSession>()

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
        duration: Double,
        startAt: Long = 0,
        waitForSegments: Int = DEFAULT_WAIT_FOR_SEGMENTS
    ): TranscodeSession {
        sessionMap[token]?.also { session ->
            if (session.job?.isActive == true) {
                return session
            } // TODO: check if transcode is complete
        }

        val mutex = Mutex(locked = true)

        outputDir.apply {
            mkdirs()
            setWritable(true)
        }

        val segmentTime = 6
        val command = ffmpeg().apply {
            val startOffset = (startAt - segmentTime).coerceAtLeast(0)
            if (startAt > 0) {
                addArguments("-ss", startOffset.toString())
            }
            addInput(UrlInput.fromPath(mediaFile.toPath()))
            addArguments("-f", "hls")
            addArguments("-hls_time", segmentTime.toString())
            addArguments("-hls_playlist_type", "vod")
            addArguments("-hls_flags", "independent_segments")
            addArguments("-hls_segment_type", "mpegts")
            addArguments("-hls_segment_filename", "${outputDir.path}/$name%01d.ts")
            if (startAt > 0) {
                addArguments("-start_number", (startOffset / segmentTime - 1).toString())
            }
            addArguments("-movflags", "+faststart")
            addArguments("-preset", "veryfast")
            addOutput(
                UrlOutput.toPath(File(outputDir, "$name.m3u8").toPath())
                    .setCodec(StreamType.VIDEO, "libx264")
                    .setCodec(StreamType.AUDIO, "aac")
            )
            addArguments("-b:a", "128000")
            addArguments("-ac:a", "2")
            setOverwriteOutput(true)
            setProgressListener { progress ->
                if (mutex.isLocked) {
                    val waitForProgress = waitForSegments * segmentTime
                    if (progress.getTime(TimeUnit.SECONDS) >= waitForProgress) {
                        runCatching { mutex.unlock() }
                    }
                }
            }
        }
        val job = scope.launch {
            launch {
                // TODO: Remove delay, address ffmpeg errors
                //  https://github.com/kokorin/Jaffree/issues/178
                delay(10_000)
                if (mutex.isLocked) {
                    mutex.unlock()
                }
            }
            command.executeAwait()
        }
        val session = TranscodeSession(
            token = token,
            name = name,
            mediaPath = mediaFile.absolutePath,
            outputPath = outputDir.absolutePath,
            ffmpegCommand = command.toString(),
            mediaDuration = duration,
            job = job,
        )

        sessionMap[session.token] = session
        mutex.withLock { /* Wait here for progress unlock */ }
        return session
    }

    fun stopSession(token: String, deleteOutput: Boolean) {
        sessionMap.remove(token)?.also { session ->
            session.job?.cancel()
            if (deleteOutput) {
                File(session.outputPath).delete()
            }
        }
    }

    fun createVariantPlaylist(
        name: String,
        mediaFile: File,
        token: String,
        duration: Double,
    ): String {
        val marker = MarkerFactory.getMarker(name).apply { add(classMarker) }

        logger.debug(marker, "Creating variant playlist for $mediaFile")

        val runtime: Duration = Duration.seconds(duration)

        logger.debug(marker, "Container runtime acquired: $runtime")

        val segmentContainer = null ?: "ts"
        val isHlsInFmp4 = segmentContainer.equals("mp4", ignoreCase = true)
        val hlsVersion = if (isHlsInFmp4) "7" else "3"

        val segmentLength = Duration.seconds(6)
        val segmentLengths = getSegmentLengths(runtime, segmentLength)

        logger.debug(marker, "Creating ${segmentLengths.size} segments at $segmentLength")

        val builder = StringBuilder(128)
            .appendLine("#EXTM3U")
            .append("#EXT-X-VERSION:")
            .appendLine(hlsVersion)
            .append("#EXT-X-TARGETDURATION:")
            .appendLine(segmentLengths.maxOrNull() ?: segmentLength)
            .appendLine("#EXT-X-MEDIA-SEQUENCE:0")

        var index = 0
        val segmentExtension = segmentContainer
        val queryString = "?token=$token"

        if (isHlsInFmp4) {
            builder.append("#EXT-X-MAP:URI=\"")
                .append("$name-1.")
                .append(segmentExtension)
                .append(queryString)
                .appendLine('"')
        }

        segmentLengths.forEach { length ->
            builder.append("#EXTINF:")
                .append(length)
                .appendLine(", nodesc")
                .append(name)
                .append(index++)
                .append('.')
                .append(segmentExtension)
                .append(queryString)
                .appendLine()
        }

        builder.appendLine("#EXT-X-ENDLIST")
        return builder.toString()
    }

    private fun getSegmentLengths(runtime: Duration, segmentLength: Duration): List<String> {
        val wholeSegments = runtime / segmentLength
        val remainingMillis = runtime.inWholeMilliseconds % segmentLength.inWholeMilliseconds
        val segmentLengthString = segmentLength.toDouble(DurationUnit.SECONDS)
            .run(segLenFormatter::format)

        val partialEndSegment = if (remainingMillis == 0L) 0 else 1
        val segmentsListLength = (wholeSegments + partialEndSegment).toInt()
        return List(segmentsListLength) { i ->
            if (i == segmentsListLength - 1 && partialEndSegment == 1) {
                Duration.milliseconds(remainingMillis)
                    .toDouble(DurationUnit.SECONDS)
                    .run(segLenFormatter::format)
            } else {
                segmentLengthString
            }
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