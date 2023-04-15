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
package anystream.models

import kotlinx.serialization.Serializable

@Serializable
sealed class StreamEncodingDetails {
    abstract val id: Int
    abstract val streamId: Int?
    abstract val codecName: String
    abstract val codecLongName: String
    abstract val index: Int
    abstract val language: String?
    abstract val duration: Float?
    abstract val title: String?
    abstract val mediaLinkId: Int
    abstract val default: Boolean

    @Serializable
    data class Audio(
        override val id: Int,
        override val streamId: Int?,
        override val index: Int,
        override val codecName: String,
        override val codecLongName: String,
        override val language: String?,
        override val duration: Float?,
        override val title: String?,
        override val mediaLinkId: Int,
        override val default: Boolean,
        val profile: String?,
        val bitRate: Int?,
        val channels: Int,
        val channelLayout: String?,
        val sampleFmt: String?,
        val sampleRate: Int?,
    ) : StreamEncodingDetails()

    @Serializable
    data class Video(
        override val id: Int,
        override val streamId: Int?,
        override val index: Int,
        override val codecName: String,
        override val codecLongName: String,
        override val language: String?,
        override val duration: Float?,
        override val title: String?,
        override val mediaLinkId: Int,
        override val default: Boolean,
        val profile: String?,
        val bitRate: Int?,
        val level: Int,
        val height: Int,
        val width: Int,
        val colorSpace: String?,
        val colorRange: String?,
        val colorTransfer: String?,
        val colorPrimaries: String?,
        val pixFmt: String?,
        val fieldOrder: String?,
    ) : StreamEncodingDetails()

    @Serializable
    data class Subtitle(
        override val id: Int,
        override val streamId: Int?,
        override val index: Int,
        override val codecName: String,
        override val codecLongName: String,
        override val language: String?,
        override val duration: Float?,
        override val title: String?,
        override val mediaLinkId: Int,
        override val default: Boolean,
    ) : StreamEncodingDetails()

    val languageName: String
        get() {
            return language
                ?.let(IsoLanguageCodes::by639_2)
                ?.name
                ?.replaceFirstChar(Char::titlecaseChar)
                ?: "Unknown"
        }
}
