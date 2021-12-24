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
package anystream.db.model

import anystream.models.DownloadMediaReference
import anystream.models.LocalMediaReference
import anystream.models.MediaKind
import anystream.models.MediaReference
import kotlinx.datetime.Instant

data class MediaReferenceDb(
    val id: Int,
    val gid: String,
    val contentGid: String,
    val rootContentGid: String?,
    val addedAt: Instant,
    val addedByUserId: Int,
    val updatedAt: Instant,
    val mediaKind: MediaKind,
    val type: Type,
    val filePath: String?,
    val directory: Boolean,
    val fileIndex: Int?,
    val hash: String?,
) {
    enum class Type {
        DOWNLOAD, LOCAL,
    }

    fun toMediaRefModel(): MediaReference {
        // TODO: Restore streams details
        return when (type) {
            Type.DOWNLOAD -> DownloadMediaReference(
                id = gid,
                contentId = contentGid,
                rootContentId = rootContentGid,
                added = addedAt.epochSeconds,
                addedByUserId = addedByUserId,
                mediaKind = mediaKind,
                streams = emptyList(),
                hash = checkNotNull(hash),
                fileIndex = fileIndex,
                filePath = filePath,
            )
            Type.LOCAL -> LocalMediaReference(
                id = gid,
                contentId = contentGid,
                rootContentId = rootContentGid,
                added = addedAt.epochSeconds,
                addedByUserId = addedByUserId,
                mediaKind = mediaKind,
                streams = emptyList(),
                filePath = checkNotNull(filePath),
                directory = directory
            )
        }
    }

    companion object {
        fun fromRefModel(mediaReference: MediaReference): MediaReferenceDb {
            // TODO: Store stream details
            return when (mediaReference) {
                is DownloadMediaReference -> MediaReferenceDb(
                    id = -1,
                    gid = mediaReference.id,
                    contentGid = mediaReference.contentId,
                    rootContentGid = mediaReference.rootContentId,
                    addedAt = Instant.fromEpochSeconds(mediaReference.added),
                    addedByUserId = mediaReference.addedByUserId,
                    updatedAt = Instant.fromEpochSeconds(mediaReference.added),
                    mediaKind = mediaReference.mediaKind,
                    type = Type.LOCAL,
                    directory = false,
                    filePath = mediaReference.filePath,
                    fileIndex = mediaReference.fileIndex,
                    hash = mediaReference.hash,
                )
                is LocalMediaReference -> MediaReferenceDb(
                    id = -1,
                    gid = mediaReference.id,
                    contentGid = mediaReference.contentId,
                    rootContentGid = mediaReference.rootContentId,
                    addedAt = Instant.fromEpochSeconds(mediaReference.added),
                    addedByUserId = mediaReference.addedByUserId,
                    updatedAt = Instant.fromEpochSeconds(mediaReference.added),
                    mediaKind = mediaReference.mediaKind,
                    type = Type.LOCAL,
                    directory = mediaReference.directory,
                    filePath = mediaReference.filePath,
                    fileIndex = null,
                    hash = null,
                )
            }
        }
    }
}
