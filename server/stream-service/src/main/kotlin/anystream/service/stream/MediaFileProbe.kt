/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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

import anystream.models.TranscodeDecision
import com.github.kokorin.jaffree.LogLevel
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffprobe.FFprobe
import com.github.kokorin.jaffree.ffprobe.FFprobeResult
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Analyzes media files to determine their codecs and container format.
 */
class MediaFileProbe(private val ffprobe: () -> FFprobe) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Represents the format and codec information of a media file.
     */
    data class MediaInfo(
        val container: String,
        val videoCodec: String,
        val audioCodec: String,
    ) {
        val isHlsInFmp4: Boolean
            get() = videoCodec.startsWith("hevc") || videoCodec.startsWith("h265")
    }

    /**
     * Analyzes a media file to determine its codecs and container format.
     *
     * @param path The path to the media file.
     * @return The [MediaInfo] containing the container format and codecs, or null if analysis fails.
     */
    suspend fun probeFile(path: Path): MediaInfo? {
        return try {
            val probeResult = withContext(IO) {
                ffprobe()
                    .setLogLevel(LogLevel.QUIET)
                    .setInput(path)
                    .setShowFormat(true)
                    .setShowStreams(true)
                    .execute()
            }

            extractMediaInfo(probeResult)
        } catch (e: Exception) {
            logger.error("Failed to analyze media file: ${path.fileName}", e)
            null
        }
    }

    suspend fun getFileDuration(mediaFile: Path): Duration {
        return withContext(IO) {
            ffprobe()
                .setLogLevel(LogLevel.QUIET)
                .setShowEntries("format=duration")
                .setInput(mediaFile)
                .execute()
                .data
                .getSubDataDouble("format", "duration")
                .seconds
        }
    }

    /**
     * Extracts media information from FFprobe result.
     *
     * @param probeResult The FFprobe result.
     * @return The [MediaInfo] containing the container format and codecs.
     */
    private fun extractMediaInfo(probeResult: FFprobeResult): MediaInfo {
        val format = probeResult.format
        val container = format.formatName.split(",").firstOrNull() ?: "unknown"

        var videoCodec = "unknown"
        var audioCodec = "unknown"

        probeResult.streams.forEach { stream ->
            when (stream.codecType) {
                StreamType.VIDEO -> videoCodec = stream.codecName
                StreamType.AUDIO -> audioCodec = stream.codecName
                else -> Unit // Ignore other stream types
            }
        }

        return MediaInfo(container, videoCodec, audioCodec)
    }

    /**
     * Determines if transcoding is needed based on client capabilities and media info.
     *
     * @param mediaInfo The media information.
     * @param supportedVideoCodecs List of video codecs supported by the client.
     * @param supportedAudioCodecs List of audio codecs supported by the client.
     * @param supportedContainers List of container formats supported by the client.
     * @return A [TranscodeDecision] indicating what needs to be transcoded.
     */
    fun determineTranscodeNeeds(
        mediaInfo: MediaInfo,
        supportedVideoCodecs: List<String>,
        supportedAudioCodecs: List<String>,
        supportedContainers: List<String>
    ): TranscodeDecision {
        val normalizedVideoCodec = normalizeCodecName(mediaInfo.videoCodec)
        val normalizedAudioCodec = normalizeCodecName(mediaInfo.audioCodec)

        val videoSupported = supportedVideoCodecs.any { normalizeCodecName(it) == normalizedVideoCodec }
        val audioSupported = supportedAudioCodecs.any { normalizeCodecName(it) == normalizedAudioCodec }
        val containerSupported = supportedContainers.contains(mediaInfo.container)

        logger.debug(
            "Media info: video={}, audio={}, container={}",
            mediaInfo.videoCodec,
            mediaInfo.audioCodec,
            mediaInfo.container
        )
        logger.debug("Normalized: video={}, audio={}", normalizedVideoCodec, normalizedAudioCodec)
        logger.debug("Supported: video={}, audio={}, container={}", videoSupported, audioSupported, containerSupported)

        return when {
            videoSupported && audioSupported -> TranscodeDecision.DIRECT
            videoSupported && !audioSupported -> TranscodeDecision.AUDIO_ONLY
            !videoSupported && audioSupported -> TranscodeDecision.VIDEO_ONLY
            else -> TranscodeDecision.FULL
        }
    }

    /**
     * Normalizes codec names for comparison.
     * This handles variations in how codecs are named between ffprobe and client capabilities.
     */
    private fun normalizeCodecName(codecName: String): String {
        return when {
            codecName.startsWith("hevc") || codecName.startsWith("h265") -> "hevc"
            codecName.startsWith("h264") || codecName.startsWith("avc") -> "h264"
            codecName.startsWith("vp8") -> "vp8"
            codecName.startsWith("vp9") -> "vp9"
            codecName.startsWith("av1") -> "av1"
            codecName.startsWith("aac") -> "aac"
            codecName.startsWith("mp3") -> "mp3"
            codecName.startsWith("opus") -> "opus"
            codecName.startsWith("flac") -> "flac"
            codecName.startsWith("vorbis") -> "vorbis"
            else -> codecName.lowercase()
        }
    }
}
