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
    val codecName: String,
    val index: Int,
    val language: String?,
    val profile: String?,
    val bitRate: Int?,
    val channels: Int?,
    val level: Int?,
    val height: Int?,
    val width: Int?,
    val type: Type,
    val title: String?,
) {
    enum class Type {
        AUDIO, VIDEO, SUBTITLE
    }

    fun toModel(): StreamEncodingDetails {
        return when (type) {
            Type.AUDIO -> StreamEncodingDetails.Audio(
                id = id,
                codecName = codecName,
                index = index,
                language = language,
                profile = profile,
                bitRate = bitRate,
                channels = checkNotNull(channels),
                title = title,
            )
            Type.VIDEO -> StreamEncodingDetails.Video(
                id = id,
                codecName = codecName,
                index = index,
                language = language,
                profile = profile,
                bitRate = bitRate,
                level = checkNotNull(level),
                height = checkNotNull(height),
                width = checkNotNull(width),
                title = title,
            )
            Type.SUBTITLE -> StreamEncodingDetails.Subtitle(
                id = id,
                codecName = codecName,
                index = index,
                language = language,
                title = title,
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
                stream.id,
                stream.codecName,
                stream.index,
                stream.language,
                audio?.profile ?: video?.profile,
                audio?.bitRate ?: video?.bitRate,
                audio?.channels,
                video?.level,
                video?.height,
                video?.height,
                type,
                stream.title
            )
        }
    }
}
