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
import anystream.sql.codegen.GenerateSqlSelect
import anystream.sql.codegen.JoinTable
import anystream.util.ObjectId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

/**
 * A data class representing the links to various media files, which may exist locally or be downloaded from
 * external sources. This class is used as a database model to store and manage information related to media files
 * and their associations with metadata entries.
 *
 * MediaLinkDb instances are organized in a hierarchical structure, with root directories containing media files
 * and directories, and child directories containing a subset of files and directories belonging to a specific
 * root metadata entry.
 *
 * @property id Unique identifier for the media link entry.
 * @property gid Globally unique identifier for the media link entry.
 * @property metadataId Unique identifier for the associated metadata entry.
 * @property metadataGid Globally unique identifier for the associated metadata entry.
 * @property rootMetadataId Unique identifier for the root metadata entry in the hierarchy (e.g., the TV show for a season or episode).
 * @property rootMetadataGid Globally unique identifier for the root metadata entry in the hierarchy.
 * @property parentMediaLinkId Unique identifier for the parent media link entry in the hierarchy (e.g., the season directory for an episode file).
 * @property parentMediaLinkGid Globally unique identifier for the parent media link entry in the hierarchy.
 * @property addedAt Timestamp of when the media link entry was added.
 * @property updatedAt Timestamp of when the media link entry was last updated.
 * @property addedByUserId Unique identifier of the user who added the media link entry.
 * @property mediaKind The kind of media (e.g., video, audio) the file belongs to.
 * @property type The type of the media link (e.g., DOWNLOAD, LOCAL).
 * @property filePath Path to the media file.
 * @property directory Flag indicating whether the media link represents a directory.
 * @property fileIndex Index or position of the file within its parent container (e.g., episode index within a season directory).
 * @property hash Hash of the media file, used for integrity checks and deduplication.
 * @property descriptor Descriptor indicating the type of media file or directory (e.g., VIDEO, AUDIO, SUBTITLE, IMAGE).
 * @property streams List of stream encoding details associated with the media file.
 */
@GenerateSqlSelect
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
    @JoinTable("streamEncoding")
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
                infoHash = requireNotNull(hash),
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
                filePath = requireNotNull(filePath),
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
