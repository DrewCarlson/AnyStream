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

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed class MediaLink {
    /**
     * The database id or -1 if it is not stored in the database.
     */
    abstract val id: Int

    /**
     * The database id string, though it may not exist in the
     * database until [id] > 0.
     */
    abstract val gid: String

    /**
     * The metadata id or null if the file has no metadata.
     */
    abstract val metadataId: Int?

    /**
     * The content gid or null if the file has no metadata.
     */
    abstract val metadataGid: String?

    /**
     * The root metadata gid or null if the file has no metadata
     * or [metadataId] has no parent metadata records.
     */
    abstract val rootMetadataId: Int?

    /**
     * The root metadata gid or null if the file has no metadata
     * or [metadataGid] has no parent metadata records.
     */
    abstract val rootMetadataGid: String?

    /**
     * The root media link id or null if this file is the root
     * media for a single metadata entry.
     */
    abstract val parentMediaLinkId: Int?

    abstract val parentMediaLinkGid: String?

    /**
     * The time the media file was scanned.
     */
    abstract val addedAt: Instant

    /**
     * The time the media file was last scanned.
     */
    abstract val updatedAt: Instant

    /**
     * The user id that created this link.
     */
    abstract val addedByUserId: Int

    /**
     * The [MediaKind] associated with processor that identified
     * this file.
     */
    abstract val mediaKind: MediaKind

    /**
     * The [Descriptor] of
     */
    abstract val descriptor: Descriptor

    /**
     * A list of [StreamEncodingDetails] that provide rich
     * information based on the [MediaKind] of the file.
     */
    abstract val streams: List<StreamEncodingDetails>

    abstract val filePath: String?

    /**
     * [Descriptor] identifies the file contents of a [MediaLink].
     */
    enum class Descriptor {
        /**
         * A directory known to contain any number of files and
         * directories of media matching [mediaKind].
         */
        ROOT_DIRECTORY,

        /**
         * A directory containing any
         */
        MEDIA_DIRECTORY,

        /**
         * A directory which contains a subset of files and
         * directories of media belonging to the [rootMetadataGid].
         */
        CHILD_DIRECTORY,

        /**
         * A video file of any type, identified as usable based
         * on the file extension and known video file extensions.
         */
        VIDEO,

        /**
         * A video file of any type, identified as usable based
         * on the file extension and known audio file extensions.
         */
        AUDIO,

        /**
         * A subtitle file of any type, identified as usable based
         * on the file extension and known subtitle file extensions.
         */
        SUBTITLE,

        /**
         * An image file of any type, identified as usable based
         * on the file extension and known image file extensions.
         */
        IMAGE,

        ;

        fun isDirectoryLink(): Boolean {
            return name.endsWith("DIRECTORY")
        }

        fun isMediaFileLink(): Boolean {
            return this == AUDIO || this == VIDEO
        }
    }

    open val filename: String
        get() = (filePath ?: "(no name)")
            .substringAfterLast('/')
            .substringAfterLast('\\')

    open val fileExtension: String?
        get() = filename
            .substringAfterLast('.', "")
            .takeIf(String::isNotBlank)
}

/**
 * A [MediaLink] that refers to a [filePath] on disk or network storage.
 */
@Serializable
data class LocalMediaLink(
    override val id: Int,
    override val gid: String,
    override val metadataId: Int?,
    override val metadataGid: String?,
    override val rootMetadataId: Int? = null,
    override val rootMetadataGid: String? = null,
    override val parentMediaLinkId: Int? = null,
    override val parentMediaLinkGid: String? = null,
    override val addedAt: Instant,
    override val updatedAt: Instant,
    override val addedByUserId: Int,
    override val mediaKind: MediaKind,
    override val descriptor: Descriptor,
    override val streams: List<StreamEncodingDetails> = emptyList(),
    override val filePath: String,
    val directory: Boolean,
) : MediaLink()

/**
 * A [MediaLink] that refers to a [fileIndex] within a
 * torrent, identified by the [infoHash].
 */
@Serializable
data class DownloadMediaLink(
    override val id: Int,
    override val gid: String,
    override val metadataId: Int?,
    override val metadataGid: String?,
    override val rootMetadataId: Int? = null,
    override val rootMetadataGid: String? = null,
    override val parentMediaLinkId: Int? = null,
    override val parentMediaLinkGid: String? = null,
    override val addedAt: Instant,
    override val updatedAt: Instant,
    override val addedByUserId: Int,
    override val mediaKind: MediaKind,
    override val descriptor: Descriptor,
    override val streams: List<StreamEncodingDetails> = emptyList(),
    val infoHash: String,
    val fileIndex: Int?,
    override val filePath: String?,
) : MediaLink()
