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


/*

        fun fromFile(
            file: File,
            mediaKind: MediaKind,
            userId: Int,
            descriptor: Descriptor,
            parentMediaLink: MediaLink? = null,
        ): MediaLink {
            return MediaLink(
                id = null,
                gid = ObjectId.get().toString(),
                parentMediaLinkId = parentMediaLink?.id,
                parentMediaLinkGid = parentMediaLink?.gid,
                hash = null,
                addedByUserId = userId,
                directory = file.isDirectory,
                filePath = file.absolutePath,
                mediaKind = mediaKind,
                type = Type.LOCAL,
                descriptor = descriptor,
            )
        }

        fun fromModel(mediaLink: MediaLink): MediaLink {
            return when (mediaLink) {
                is DownloadMediaLink -> MediaLink(
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
                    hash = mediaLink.infoHash,
                    descriptor = mediaLink.descriptor,
                    streams = mediaLink.streams.map(StreamEncodingDetailsDb::fromModel),
                )

                is LocalMediaLink -> MediaLink(
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
                    hash = null,
                    descriptor = mediaLink.descriptor,
                    streams = mediaLink.streams.map(StreamEncodingDetailsDb::fromModel),
                )
            }
        }
 */
