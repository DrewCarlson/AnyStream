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
    abstract val codecName: String
    abstract val rawProbeData: String
    abstract val index: Int
    abstract val language: String?

    @Serializable
    data class Audio(
        override val id: Int,
        override val index: Int,
        override val codecName: String,
        override val rawProbeData: String,
        override val language: String?,
        val profile: String?,
        val bitRate: Int?,
        val channels: Int,
    ) : StreamEncodingDetails()

    @Serializable
    data class Video(
        override val id: Int,
        override val index: Int,
        override val codecName: String,
        override val rawProbeData: String,
        override val language: String?,
        val profile: String?,
        val bitRate: Int?,
        val level: Int,
        val height: Int,
        val width: Int,
    ) : StreamEncodingDetails()

    @Serializable
    data class Subtitle(
        override val id: Int,
        override val index: Int,
        override val codecName: String,
        override val rawProbeData: String,
        override val language: String?,
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
