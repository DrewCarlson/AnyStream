/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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
package anystream.media.util

import anystream.models.StreamEncoding
import anystream.models.StreamEncodingType
import anystream.util.ObjectId
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffprobe.Stream
import kotlin.time.Duration.Companion.seconds

internal fun Stream.toStreamEncoding(mediaLinkId: String): StreamEncoding? {
    if (codecType == StreamType.DATA || codecType == StreamType.ATTACHMENT) {
        return null
    }
    val title = getTag("title")
    val language = getTag("language") ?: getTag("LANGUAGE")
    return StreamEncoding(
        id = ObjectId.next(),
        streamId = id,
        index = index,
        codecName = codecName.orEmpty(),
        codecLongName = codecLongName.orEmpty(),
        profile = profile,
        bitRate = bitRate,
        level = level,
        height = height,
        width = width,
        language = language,
        title = title,
        mediaLinkId = mediaLinkId,
        colorPrimaries = colorPrimaries,
        colorRange = colorRange,
        colorSpace = colorSpace,
        colorTransfer = colorTransfer,
        pixFmt = pixFmt,
        fieldOrder = fieldOrder,
        duration = duration?.toDouble()?.seconds,
        default = disposition.default,
        channels = channels,
        channelLayout = channelLayout,
        sampleFmt = sampleFmt,
        sampleRate = sampleRate,
        type = when (codecType) {
            StreamType.VIDEO, StreamType.VIDEO_NOT_PICTURE -> StreamEncodingType.VIDEO
            StreamType.AUDIO -> StreamEncodingType.AUDIO
            StreamType.SUBTITLE -> StreamEncodingType.SUBTITLE
            else -> error("Unsupported codecType '$codecType'")
        },
    )
}
