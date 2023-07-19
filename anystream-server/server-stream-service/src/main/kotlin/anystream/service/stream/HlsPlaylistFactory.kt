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

import org.slf4j.LoggerFactory
import java.io.File
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

internal class HlsPlaylistFactory {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val segLenFormatter = DecimalFormat().apply {
        minimumFractionDigits = 4
        maximumFractionDigits = 4
    }

    fun createVariantPlaylist(
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
            appendLine(segmentDuration.toInt(DurationUnit.SECONDS))
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
                    append(segLenFormatter.format(finalSegLength.toDouble(DurationUnit.SECONDS)))
                } else {
                    append(segLenFormatter.format(segmentDuration.toDouble(DurationUnit.SECONDS)))
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

    fun getSegmentCountAndFinalLength(
        runtime: Duration,
        segmentLength: Duration,
    ): Pair<Int, Duration> {
        val segments = ceil(runtime / segmentLength).toInt()
        val lastPartialSegmentLength =
            (runtime.inWholeMilliseconds % segmentLength.inWholeMilliseconds).milliseconds
        return if (lastPartialSegmentLength == Duration.ZERO) {
            segments to segmentLength
        } else {
            segments to lastPartialSegmentLength
        }
    }
}
