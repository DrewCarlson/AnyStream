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
package anystream.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
sealed class MediaReference {
    @SerialName("_id")
    abstract val id: String
    abstract val contentId: String
    abstract val rootContentId: String?
    abstract val added: Long
    abstract val addedByUserId: String
    abstract val mediaKind: MediaKind
}

@Serializable
data class LocalMediaReference(
    @SerialName("_id")
    override val id: String,
    override val contentId: String,
    override val rootContentId: String? = null,
    override val added: Long,
    override val addedByUserId: String,
    override val mediaKind: MediaKind,
    val filePath: String,
    val directory: Boolean,
) : MediaReference()

@Serializable
data class DownloadMediaReference(
    @SerialName("_id")
    override val id: String,
    override val contentId: String,
    override val rootContentId: String? = null,
    override val added: Long,
    override val addedByUserId: String,
    override val mediaKind: MediaKind,
    val hash: String,
    val fileIndex: Int?,
    val filePath: String?
) : MediaReference()
