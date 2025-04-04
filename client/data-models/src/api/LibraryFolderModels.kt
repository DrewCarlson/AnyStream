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
package anystream.models.api

import anystream.models.Directory
import anystream.models.Library
import anystream.models.MediaKind
import kotlinx.serialization.Serializable

@Serializable
data class AddLibraryFolderRequest(
    val path: String,
)

@Serializable
sealed class AddLibraryFolderResponse {

    @Serializable
    data class Success(
        val library: Library,
        val directory: Directory,
    ) : AddLibraryFolderResponse()

    @Serializable
    data object LibraryFolderExists : AddLibraryFolderResponse()

    @Serializable
    data class FileError(
        val exists: Boolean,
        val isDirectory: Boolean,
    ) : AddLibraryFolderResponse()

    @Serializable
    data class DatabaseError(
        val stacktrace: String?,
    ) : AddLibraryFolderResponse()

    @Serializable
    data class RequestError(
        val stacktrace: String?,
    ) : AddLibraryFolderResponse()
}

@Serializable
data class LibraryFolderList(
    val folders: List<RootFolder>,
) {
    @Serializable
    data class RootFolder(
        val libraryId: String,
        val path: String,
        val mediaKind: MediaKind,
        val mediaMatchCount: Int,
        val unmatchedCount: Int,
        val sizeOnDisk: String? = null,
        val freeSpace: String? = null,
    )
}
