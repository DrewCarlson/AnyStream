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

/**
 * A data class representing the encoding details of a media file associated with a media link. This class is used
 * as a database model to store and manage information related to the encoding properties of media files, such as
 * codec, resolution, and bitrate.
 *
 * @property id Unique identifier for the stream encoding details entry.
 * @property streamId Unique identifier for the associated stream.
 * @property codecName Short name of the codec used for encoding the media stream.
 * @property codecLongName Long name of the codec used for encoding the media stream.
 * @property index Index of the stream within the media file.
 * @property language Language code of the stream (e.g., 'en' for English).
 * @property profile Codec profile used for encoding the media stream.
 * @property bitRate Bitrate of the encoded media stream in bits per second.
 * @property channels Number of audio channels in the media stream.
 * @property channelLayout Layout of the audio channels in the media stream.
 * @property level Level of the codec used for encoding the media stream.
 * @property height Height of the video stream in pixels.
 * @property width Width of the video stream in pixels.
 * @property type Type of the media stream (e.g., video, audio, subtitle).
 * @property title Title of the media stream.
 * @property colorSpace Color space used in the video stream.
 * @property colorRange Color range used in the video stream.
 * @property colorTransfer Color transfer function used in the video stream.
 * @property colorPrimaries Color primaries used in the video stream.
 * @property pixFmt Pixel format used in the video stream.
 * @property fieldOrder Field order used in the video stream.
 * @property sampleFmt Audio sample format used in the audio stream.
 * @property sampleRate Audio sample rate used in the audio stream, in samples per second.
 * @property duration Duration of the media stream in seconds.
 * @property mediaLinkId Unique identifier for the associated media link entry.
 * @property default Flag indicating whether the stream is the default choice for its type within the media file.
 */
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
                    ?: subtitle?.let { Type.SUBTITLE },
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
