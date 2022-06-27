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

import anystream.models.*
import anystream.util.ObjectId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

data class MediaLinkDb(
    val id: Int?,
    val gid: String,
    val metadataId: Int? = null,
    val metadataGid: String? = null,
    val rootMetadataId: Int? = null,
    val rootMetadataGid: String? = null,
    val parentMediaLinkId: Int? = null,
    val parentMediaLinkGid: String? = null,
    val addedAt: Instant = Clock.System.now(),
    val updatedAt: Instant = addedAt,
    val addedByUserId: Int,
    val mediaKind: MediaKind,
    val type: Type,
    val filePath: String? = null,
    val directory: Boolean = false,
    val fileIndex: Int? = null,
    val hash: String? = null,
    val descriptor: MediaLink.Descriptor,
    val streams: List<StreamEncodingDetailsDb> = emptyList(),
) {
    enum class Type {
        DOWNLOAD, LOCAL,
    }

    fun toModel(): MediaLink {
        return when (type) {
            Type.DOWNLOAD -> DownloadMediaLink(
                id = id ?: -1,
                gid = gid,
                metadataId = metadataId,
                metadataGid = metadataGid,
                rootMetadataId = rootMetadataId,
                rootMetadataGid = rootMetadataGid,
                parentMediaLinkId = parentMediaLinkId,
                parentMediaLinkGid = parentMediaLinkGid,
                addedAt = addedAt,
                updatedAt = updatedAt,
                addedByUserId = addedByUserId,
                mediaKind = mediaKind,
                streams = streams.map(StreamEncodingDetailsDb::toModel),
                infoHash = checkNotNull(hash),
                fileIndex = fileIndex,
                filePath = filePath,
                descriptor = descriptor,
            )
            Type.LOCAL -> LocalMediaLink(
                id = id ?: -1,
                gid = gid,
                metadataId = metadataId,
                metadataGid = metadataGid,
                rootMetadataId = rootMetadataId,
                rootMetadataGid = rootMetadataGid,
                parentMediaLinkId = parentMediaLinkId,
                parentMediaLinkGid = parentMediaLinkGid,
                addedAt = addedAt,
                updatedAt = updatedAt,
                addedByUserId = addedByUserId,
                mediaKind = mediaKind,
                streams = streams.map(StreamEncodingDetailsDb::toModel),
                filePath = checkNotNull(filePath),
                directory = directory,
                descriptor = descriptor,
            )
        }
    }

    companion object {
        fun fromFile(
            file: File,
            mediaKind: MediaKind,
            userId: Int,
            descriptor: MediaLink.Descriptor,
            parentMediaLink: MediaLinkDb? = null,
        ): MediaLinkDb {
            return MediaLinkDb(
                id = null,
                gid = ObjectId.get().toString(),
                parentMediaLinkId = parentMediaLink?.id,
                parentMediaLinkGid = parentMediaLink?.gid,
                fileIndex = null,
                hash = null,
                addedByUserId = userId,
                directory = file.isDirectory,
                filePath = file.absolutePath,
                mediaKind = mediaKind,
                type = Type.LOCAL,
                descriptor = descriptor,
            )
        }

        fun fromModel(mediaLink: MediaLink): MediaLinkDb {
            return when (mediaLink) {
                is DownloadMediaLink -> MediaLinkDb(
                    id = mediaLink.id,
                    gid = mediaLink.gid,
                    metadataId = mediaLink.metadataId,
                    metadataGid = mediaLink.metadataGid,
                    rootMetadataId = mediaLink.rootMetadataId,
                    rootMetadataGid = mediaLink.rootMetadataGid,
                    parentMediaLinkId = mediaLink.parentMediaLinkId,
                    parentMediaLinkGid = mediaLink.parentMediaLinkGid,
                    addedAt = mediaLink.addedAt,
                    updatedAt = mediaLink.updatedAt,
                    addedByUserId = mediaLink.addedByUserId,
                    mediaKind = mediaLink.mediaKind,
                    type = Type.LOCAL,
                    directory = false,
                    filePath = mediaLink.filePath,
                    fileIndex = mediaLink.fileIndex,
                    hash = mediaLink.infoHash,
                    descriptor = mediaLink.descriptor,
                    streams = mediaLink.streams.map(StreamEncodingDetailsDb::fromModel),
                )
                is LocalMediaLink -> MediaLinkDb(
                    id = mediaLink.id,
                    gid = mediaLink.gid,
                    metadataId = mediaLink.metadataId,
                    metadataGid = mediaLink.metadataGid,
                    rootMetadataId = mediaLink.rootMetadataId,
                    rootMetadataGid = mediaLink.rootMetadataGid,
                    parentMediaLinkId = mediaLink.parentMediaLinkId,
                    parentMediaLinkGid = mediaLink.parentMediaLinkGid,
                    addedAt = mediaLink.addedAt,
                    updatedAt = mediaLink.updatedAt,
                    addedByUserId = mediaLink.addedByUserId,
                    mediaKind = mediaLink.mediaKind,
                    type = Type.LOCAL,
                    directory = mediaLink.directory,
                    filePath = mediaLink.filePath,
                    fileIndex = null,
                    hash = null,
                    descriptor = mediaLink.descriptor,
                    streams = mediaLink.streams.map(StreamEncodingDetailsDb::fromModel),
                )
            }
        }
    }
}
