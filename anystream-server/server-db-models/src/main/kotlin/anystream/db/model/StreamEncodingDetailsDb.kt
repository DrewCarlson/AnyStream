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
package anystream.db.model

import anystream.models.StreamEncodingDetails

data class StreamEncodingDetailsDb(
    val id: Int,
    val streamId: Int?,
    val codecName: String,
    val codecLongName: String,
    val index: Int,
    val language: String?,
    val profile: String?,
    val bitRate: Int?,
    val channels: Int?,
    val channelLayout: String?,
    val level: Int?,
    val height: Int?,
    val width: Int?,
    val type: Type,
    val title: String?,
    val colorSpace: String?,
    val colorRange: String?,
    val colorTransfer: String?,
    val colorPrimaries: String?,
    val pixFmt: String?,
    val fieldOrder: String?,
    val sampleFmt: String?,
    val sampleRate: Int?,
    val duration: Float?,
    val mediaLinkId: Int,
    val default: Boolean,
) {
    enum class Type {
        AUDIO, VIDEO, SUBTITLE
    }

    fun toModel(): StreamEncodingDetails {
        return when (type) {
            Type.AUDIO -> StreamEncodingDetails.Audio(
                id = id,
                streamId = streamId,
                codecName = codecName,
                codecLongName = codecLongName,
                index = index,
                language = language,
                profile = profile,
                bitRate = bitRate,
                channels = checkNotNull(channels),
                channelLayout = channelLayout,
                title = title,
                mediaLinkId = mediaLinkId,
                sampleRate = sampleRate,
                sampleFmt = sampleFmt,
                duration = duration,
                default = default,
            )
            Type.VIDEO -> StreamEncodingDetails.Video(
                id = id,
                streamId = streamId,
                codecName = codecName,
                codecLongName = codecLongName,
                index = index,
                language = language,
                profile = profile,
                bitRate = bitRate,
                level = checkNotNull(level),
                height = checkNotNull(height),
                width = checkNotNull(width),
                title = title,
                mediaLinkId = mediaLinkId,
                colorSpace = colorSpace,
                colorRange = colorRange,
                colorTransfer = colorTransfer,
                colorPrimaries = colorPrimaries,
                pixFmt = pixFmt,
                fieldOrder = fieldOrder,
                duration = duration,
                default = default,
            )
            Type.SUBTITLE -> StreamEncodingDetails.Subtitle(
                id = id,
                streamId = streamId,
                codecName = codecName,
                codecLongName = codecLongName,
                index = index,
                language = language,
                title = title,
                mediaLinkId = mediaLinkId,
                duration = duration,
                default = default,
            )
        }
    }

    companion object {
        fun fromModel(stream: StreamEncodingDetails): StreamEncodingDetailsDb {
            val audio = (stream as? StreamEncodingDetails.Audio)
            val video = (stream as? StreamEncodingDetails.Video)
            val subtitle = (stream as? StreamEncodingDetails.Subtitle)
            val type = checkNotNull(
                audio?.let { Type.AUDIO }
                    ?: video?.let { Type.VIDEO }
                    ?: subtitle?.let { Type.SUBTITLE }
            )
            return StreamEncodingDetailsDb(
                id = stream.id,
                streamId = stream.streamId,
                codecName = stream.codecName,
                codecLongName = stream.codecLongName,
                index = stream.index,
                language = stream.language,
                profile = audio?.profile ?: video?.profile,
                bitRate = audio?.bitRate ?: video?.bitRate,
                channels = audio?.channels,
                channelLayout = audio?.channelLayout,
                level = video?.level,
                height = video?.height,
                width = video?.width,
                type = type,
                title = stream.title,
                mediaLinkId = stream.mediaLinkId,
                colorSpace = video?.colorSpace,
                colorRange = video?.colorRange,
                colorPrimaries = video?.colorPrimaries,
                colorTransfer = video?.colorTransfer,
                fieldOrder = video?.fieldOrder,
                pixFmt = video?.pixFmt,
                sampleFmt = audio?.sampleFmt,
                sampleRate = audio?.sampleRate,
                duration = stream.duration,
                default = stream.default,
            )
        }
    }
}
