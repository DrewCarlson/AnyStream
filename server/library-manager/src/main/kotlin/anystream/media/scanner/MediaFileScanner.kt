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
package anystream.media.scanner

import anystream.db.LibraryDao
import anystream.db.MediaLinkDao
import anystream.db.pojos.toMediaLink
import anystream.models.Directory
import anystream.media.AUDIO_EXTENSIONS
import anystream.media.SUBTITLE_EXTENSIONS
import anystream.media.VIDEO_EXTENSIONS
import anystream.models.MediaKind
import anystream.models.Descriptor
import anystream.models.api.ContentIdContainer
import anystream.models.api.MediaScanResult
import anystream.models.backend.MediaScannerState
import anystream.util.concurrentMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.*

class MediaFileScanner(
    private val mediaLinkDao: MediaLinkDao,
    private val libraryDao: LibraryDao,
    private val fs: FileSystem,
) {

    companion object {
        private val MOVIE_TV_DESCRIPTOR_EXTENSION_MAP = mapOf(
            VIDEO_EXTENSIONS to Descriptor.VIDEO,
            SUBTITLE_EXTENSIONS to Descriptor.SUBTITLE,
        )
        private val AUDIOBOOK_MUSIC_DESCRIPTOR_EXTENSION_MAP = mapOf(
            AUDIO_EXTENSIONS to Descriptor.AUDIO,
        )
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val mediaScannerState = MutableStateFlow<MediaScannerState>(MediaScannerState.Idle)

    val state: StateFlow<MediaScannerState> = mediaScannerState.asStateFlow()

    /**
     * Scan the file or directory at the [path] to add [Directory] and
     * [MediaLink] records for all files that can be tracked.
     *
     * @param path An absolute file path to a media directory or file.
     */
    //TODO: Still used for tests, but should be removed in favor of typed directory and media link params.
    suspend fun scan(path: Path): MediaScanResult {
        if (!path.isAbsolute) {
            return MediaScanResult.ErrorAbsolutePathRequired
        }

        return if (path.isDirectory()) {
            scanDirectory(path, directory = null, parentDirectory = null)
        } else {
            scanFile(path, directory = null)
        }
    }

    /**
     * Scan the directory to update all nested [Directory] and [MediaLink]
     * records for all files that can be tracked.
     *
     * @param directory An existing [Directory] record.
     */
    suspend fun scan(directory: Directory): MediaScanResult {
        return scanDirectory(
            path = fs.getPath(directory.filePath),
            directory = directory,
            parentDirectory = null
        )
    }

    /**
     * Scan the directory and it's contents at [path] with an optional
     * [directory] record if the directory itself is already tracked.
     *
     * @param path An absolute path string that points to a directory.
     * @param directory An optional database record if the directory is already tracked.
     * @param parentDirectory An optional database record for the parent directory of [path].
     */
    private suspend fun scanDirectory(
        path: Path,
        directory: Directory?,
        parentDirectory: Directory?
    ): MediaScanResult {
        require(path.isAbsolute) { "scanDirectory path must be absolute: $path" }

        if (path.exists()) {
            require(path.isDirectory()) { "scanDirectory path must be directory: $path" }
        } else {
            return pruneAllPathRecords(path)
        }

        val pathString = path.absolutePathString()
        var resultIdContainer = ContentIdContainer.EMPTY
        val directoryDb = directory
            ?: libraryDao.fetchDirectoryByPath(pathString)
            ?: run {
                val parentDirectoryDb = parentDirectory
                    ?: libraryDao.fetchDirectoryByPath(path.parent.absolutePathString())
                    ?: return MediaScanResult.ErrorNotInLibrary

                val newDirectory = libraryDao.insertDirectory(
                    parentId = parentDirectoryDb.id,
                    libraryId = parentDirectoryDb.libraryId,
                    path = pathString
                )
                resultIdContainer = resultIdContainer.copy(
                    addedIds = listOf(newDirectory.id)
                )
                newDirectory
            }

        // TODO: currently only returns information when sub results are successful.
        //  modify this to propagate any errors and include any affected ids in error cases.
        //  this also applies to the pruning process which is done above.
        val subResults = coroutineScope {
            path.listDirectoryEntries()
                .asFlow()
                .concurrentMap(this, concurrencyLevel = 10) { childPath ->
                    if (childPath.isDirectory()) {
                        scanDirectory(childPath, null, directoryDb)
                    } else {
                        scanFile(childPath, directoryDb)
                    }
                }
                .toList()
        }.filterIsInstance<MediaScanResult.Success>()

        return MediaScanResult.Success(
            directories = resultIdContainer,
            mediaLinks = ContentIdContainer.EMPTY
        ).merge(subResults)
    }

    private suspend fun pruneAllPathRecords(path: Path): MediaScanResult.Success {
        val pathString = path.absolutePathString()
        val deletedMediaLinks = mediaLinkDao.deleteByBasePath(pathString)
        val deletedIds = libraryDao.fetchDirectoryByPath(pathString)
            ?.run {
                libraryDao.deleteDirectoriesByParent(id)
                    .plus(listOfNotNull(id.takeIf { libraryDao.deleteDirectory(id) }))
            }
            .orEmpty()

        return MediaScanResult.Success(
            directories = ContentIdContainer(removedIds = deletedIds),
            mediaLinks = ContentIdContainer(removedIds = deletedMediaLinks)
        )
    }

    private suspend fun scanFile(
        path: Path,
        directory: Directory?,
    ): MediaScanResult {
        val existingMediaLink = mediaLinkDao.findByFilePath(path.absolutePathString())
        if (existingMediaLink != null) {
            if (!path.exists()) {
                return pruneAllPathRecords(path)
            }

            return MediaScanResult.Success(
                directories = ContentIdContainer.EMPTY,
                mediaLinks = ContentIdContainer(
                    existingIds = listOf(existingMediaLink.id)
                )
            )
        }

        if (!path.exists() || path.isDirectory()) {
            logger.debug("Media link file is missing {}", path)
            return MediaScanResult.ErrorFileNotFound
        }

        val directoryDb = directory
            ?: libraryDao.fetchDirectoryByPath(path.parent.absolutePathString())
            ?: return MediaScanResult.ErrorNotInLibrary

        val library = libraryDao.fetchLibraryForDirectory(directoryDb.id)
            ?: return MediaScanResult.ErrorNotInLibrary

        val descriptorFilters = descriptorExtensionMapFor(library.mediaKind)
            ?: return MediaScanResult.ErrorNothingToScan

        val descriptor = descriptorFilters.firstNotNullOfOrNull { (extensions, descriptor) ->
            descriptor.takeIf { path.extension.lowercase().run(extensions::contains) }
        } ?: return MediaScanResult.ErrorNothingToScan

        val mediaLink = path.toMediaLink(library.mediaKind, descriptor, directoryDb.id)
        try {
            check(mediaLinkDao.insertLink(mediaLink))
        } catch (e: Throwable) {
            logger.error("Failed to insert media link", e)
            return MediaScanResult.ErrorDatabaseException(e.stackTraceToString())
        }

        return MediaScanResult.Success(
            directories = ContentIdContainer.EMPTY,
            mediaLinks = ContentIdContainer(
                addedIds = listOf(mediaLink.id)
            )
        )
    }

    private fun descriptorExtensionMapFor(mediaKind: MediaKind) = when (mediaKind) {
        MediaKind.MOVIE, MediaKind.TV -> MOVIE_TV_DESCRIPTOR_EXTENSION_MAP
        MediaKind.AUDIOBOOK, MediaKind.MUSIC -> AUDIOBOOK_MUSIC_DESCRIPTOR_EXTENSION_MAP
        else -> {
            logger.warn("No supported file extensions for MediaKind {}", mediaKind)
            null
        }
    }

    private fun addIdOrSetActiveState(mediaLinkId: String) {
        mediaScannerState.update { state ->
            (state as? MediaScannerState.Active ?: MediaScannerState.Active()).run {
                copy(mediaLinkIds = mediaLinkIds + mediaLinkId)
            }
        }
    }

    private fun removeIdOrIdleState(mediaLinkId: String) {
        mediaScannerState.update { state ->
            if (state is MediaScannerState.Active) {
                val updatedIds = state.mediaLinkIds - mediaLinkId
                if (updatedIds.isEmpty()) {
                    MediaScannerState.Idle
                } else {
                    state.copy(mediaLinkIds = updatedIds)
                }
            } else {
                MediaScannerState.Idle
            }
        }
    }
}
