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
sealed class MediaReference {
    abstract val id: String
    abstract val contentId: String
    abstract val rootContentId: String?
    abstract val added: Long
    abstract val addedByUserId: Int
    abstract val mediaKind: MediaKind
    abstract val streams: List<StreamEncodingDetails>
}

@Serializable
data class LocalMediaReference(
    override val id: String,
    override val contentId: String,
    override val rootContentId: String? = null,
    override val added: Long,
    override val addedByUserId: Int,
    override val mediaKind: MediaKind,
    override val streams: List<StreamEncodingDetails> = emptyList(),
    val filePath: String,
    val directory: Boolean,
) : MediaReference()

@Serializable
data class DownloadMediaReference(
    override val id: String,
    override val contentId: String,
    override val rootContentId: String? = null,
    override val added: Long,
    override val addedByUserId: Int,
    override val mediaKind: MediaKind,
    override val streams: List<StreamEncodingDetails> = emptyList(),
    val hash: String,
    val fileIndex: Int?,
    val filePath: String?
) : MediaReference()
