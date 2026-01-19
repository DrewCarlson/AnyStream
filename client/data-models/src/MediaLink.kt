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

import dev.drewhamilton.poko.Poko
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * [Descriptor] identifies the file contents of a [MediaLink].
 */
enum class Descriptor {
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

    fun isMediaFileLink(): Boolean {
        return this == AUDIO || this == VIDEO
    }
}


val MediaLink.filename: String
    get() = (filePath ?: "(no name)")
        .substringAfterLast('/')
        .substringAfterLast('\\')

val MediaLink.fileExtension: String?
    get() = filename
        .substringAfterLast('.', "")
        .takeIf(String::isNotBlank)

fun MediaLink.typed(): MediaLinkTyped {
    return when (type) {
        MediaLinkType.DOWNLOAD -> LocalMediaLink(
            id = id,
            metadataId = metadataId,
            rootMetadataId = rootMetadataId,
            directoryId = directoryId,
            type = type,
            createdAt = createdAt,
            updatedAt = updatedAt,
            mediaKind =  mediaKind,
            descriptor = descriptor,
            filePath = checkNotNull(filePath),
        )
        MediaLinkType.LOCAL -> LocalMediaLink(
            id = id,
            metadataId = metadataId,
            rootMetadataId = rootMetadataId,
            directoryId = directoryId,
            type = type,
            createdAt = createdAt,
            updatedAt = updatedAt,
            mediaKind =  mediaKind,
            descriptor = descriptor,
            filePath = checkNotNull(filePath)
        )
    }
}

sealed class MediaLinkTyped {
    abstract val id: String
    abstract val metadataId: String?
    abstract val rootMetadataId: String?
    abstract val directoryId: String
    abstract val createdAt: Instant
    abstract val mediaKind: MediaKind
    abstract val type: MediaLinkType
    abstract val updatedAt: Instant?
    abstract val filePath: String?
    abstract val descriptor: Descriptor
}

/**
 * A [MediaLink] that refers to a [filePath] on disk or network storage.
 */
@Poko
@Serializable
class LocalMediaLink(
    override val id: String,
    override val metadataId: String?,
    override val rootMetadataId: String? = null,
    override val directoryId: String,
    override val type: MediaLinkType,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    //override val addedByUserId: Int,
    override val mediaKind: MediaKind,
    override val descriptor: Descriptor,
    override val filePath: String,
) : MediaLinkTyped()

/**
 * A [MediaLink] that refers to a [fileIndex] within a
 * torrent, identified by the [infoHash].
 */
@Poko
@Serializable
class DownloadMediaLink(
    override val id: String,
    override val metadataId: String?,
    override val rootMetadataId: String? = null,
    override val directoryId: String,
    override val type: MediaLinkType,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    //override val addedByUserId: Int,
    override val mediaKind: MediaKind,
    override val descriptor: Descriptor,
    val infoHash: String,
    val fileIndex: Int?,
    override val filePath: String?,
) : MediaLinkTyped()
