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
import anystream.models.Library
import anystream.models.MediaLink
import anystream.media.AUDIO_EXTENSIONS
import anystream.media.SUBTITLE_EXTENSIONS
import anystream.media.VIDEO_EXTENSIONS
import anystream.models.MediaKind
import anystream.models.Descriptor
import anystream.models.api.MediaScanResult
import anystream.models.backend.MediaScannerState
import anystream.util.concurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.measureTimedValue

class MediaFileScanner(
    private val mediaLinkDao: MediaLinkDao,
    private val libraryDao: LibraryDao,
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val mediaScannerState = MutableStateFlow<MediaScannerState>(MediaScannerState.Idle)

    val state: StateFlow<MediaScannerState> = mediaScannerState.asStateFlow()

    suspend fun scan(path: Path): MediaScanResult {
        require(path.isAbsolute) {
            "MediaFileScanner.scan(...) only accepts absolute path strings: $path"
        }

        return if (path.isDirectory()) {
            scanDirectory(path)
        } else {
            scanFile(path)
        }
    }

    private suspend fun scanDirectory(path: Path, directory: Directory? = null): MediaScanResult {
        val pathString = path.absolutePathString()
        val directoryDb = directory ?: libraryDao.findByExactPath(pathString)
        return if (directoryDb == null) {
            val parentPathString = path.parent.absolutePathString()
            val parentDirectory = libraryDao.findByExactPath(parentPathString)
            if (parentDirectory == null) {
                MediaScanResult.ErrorNothingToScan
            } else {
                val newDirectory =
                    libraryDao.insertDirectory(parentDirectory.id, parentDirectory.libraryId, path.absolutePathString())
                scanDirectory(path, newDirectory)
            }
        } else {
            val subResults = path
                .listDirectoryEntries()
                .asFlow()
                .concurrentMap(scope, concurrencyLevel = 10) { childPath ->
                    val r = measureTimedValue {
                        if (childPath.isDirectory()) {
                            scanDirectory(childPath)
                        } else {
                            scanFile(childPath)
                        }
                    }
                    println("Scan duration ${r.duration} for '$childPath'")
                    r.value
                }
                .toList()
            MediaScanResult.Success(
                addedIds = emptyList(),
                removedIds = emptyList(),
                existingIds = emptyList()
            )
        }
    }

    private fun scanFile(path: Path): MediaScanResult {
        if (!path.exists() || path.isDirectory()) {
            logger.debug("Media link file is missing {}", path)
            return MediaScanResult.ErrorFileNotFound
        }
        return MediaScanResult.Success(
            addedIds = emptyList(),
            removedIds = emptyList(),
            existingIds = emptyList(),
        )
    }

    private fun scanMediaFolder(
        libraryDb: Library,
        libraryDirectory: Directory,
        directory: Directory
    ): MediaScanResult {
        val targetFolder = Path(directory.filePath)
        if (!targetFolder.exists() || !targetFolder.isDirectory()) {
            logger.debug("Root content directory not found: {}", targetFolder)
            return MediaScanResult.ErrorFileNotFound
        }
        val (existingFilePaths, removedFilePaths) = try {
            mediaLinkDao.findFilePathsByBasePath(targetFolder.absolutePathString())
                .partition { File(it).exists() }
        } catch (e: Throwable) {
            logger.error("Failed to find child media links", e)
            return MediaScanResult.ErrorDatabaseException(e.stackTraceToString())
        }
        val (removedIds, removedCount) = if (removedFilePaths.isNotEmpty()) {
            mediaLinkDao.findIdsByFilePaths(removedFilePaths) to mediaLinkDao.deleteByFilePaths(removedFilePaths)
        } else {
            emptyList<String>() to 0
        }

        logger.debug(
            "Found {} existing media links in {} and removed {} missing links.",
            existingFilePaths.size,
            targetFolder.absolutePathString(),
            removedCount,
        )

        val descriptorFilters = when (libraryDb.mediaKind) {
            MediaKind.MOVIE, MediaKind.TV -> MOVIE_TV_DESCRIPTOR_EXTENSION_MAP
            MediaKind.AUDIOBOOK, MediaKind.MUSIC -> AUDIOBOOK_MUSIC_DESCRIPTOR_EXTENSION_MAP
            else -> {
                logger.warn("No supported file extensions for MediaKind {}", libraryDb.mediaKind)
                return MediaScanResult.ErrorNothingToScan
            }
        }
        val allFilterExtensions = descriptorFilters.keys.flatten()
        val processor = Processor(libraryDb, libraryDirectory, descriptorFilters, existingFilePaths)
        val mediaFileLinks = targetFolder.walk()
            .mapNotNull { path ->
                if (path.isDirectory()) {
                    processor.processDirectory(path)
                    null
                } else {
                    path
                }
            }
            .filter { file ->
                // Ignore files with existing links or unrecognized file types
                !existingFilePaths.contains(file.absolutePathString()) &&
                        file.extension.lowercase().run(allFilterExtensions::contains)
            }
            .groupBy { file ->
                if (targetFolder == file.parent) {
                    targetFolder
                } else {
                    var selectedRoot = file
                    while (selectedRoot.parent != targetFolder) {
                        selectedRoot = selectedRoot.parent
                    }
                    selectedRoot
                }
            }
            .flatMap { (parent, files) ->
                processor.processFiles(files, parent)
            }
            .toList()

        return try {
            logger.debug("Inserting ${mediaFileLinks.size} media file links.")
            mediaLinkDao.insertLinks(mediaFileLinks)
            val existing = if (existingFilePaths.isNotEmpty()) {
                mediaLinkDao.findIdsByFilePaths(existingFilePaths)
            } else {
                emptyList()
            }
            val addedIds = processor.newMediaLinkIds + mediaFileLinks.map(MediaLink::id)
            return MediaScanResult.Success(
                addedIds = addedIds,
                removedIds = removedIds,
                existingIds = existing,
            )
        } catch (e: Throwable) {
            logger.error("Failed to insert new media links", e)
            MediaScanResult.ErrorDatabaseException(e.stackTraceToString())
        }
    }

    inner class Processor(
        private val libraryDb: Library,
        private val libraryDirectory: Directory,
        private val descriptorFilters: Map<List<String>, Descriptor>,
        private val existingFilePaths: List<String>,
    ) {
        private val _newDirectoryIds = mutableListOf<String>()
        private val _newMediaLinkIds = mutableListOf<String>()
        private val directoryCache = mutableMapOf<Path, Directory>()
        private val mediaLinkCache = mutableMapOf<Path, MediaLink>()
        val newDirectoryIds: List<String>
            get() = _newDirectoryIds.toList()
        val newMediaLinkIds: List<String>
            get() = _newMediaLinkIds.toList()

        fun processFiles(
            files: List<Path>,
            parent: Path
        ): List<MediaLink> {
            val parentDirectory = directoryCache.getValue(parent)
            return files.map { path ->
                val actualParentDirectory = if (path.parent == parent) {
                    parentDirectory
                } else {
                    directoryCache.getValue(path.parent)
                }
                val descriptor = descriptorFilters.firstNotNullOf { (extensions, descriptor) ->
                    descriptor.takeIf { path.extension.lowercase().run(extensions::contains) }
                }
                path.toMediaLink(
                    libraryDb.mediaKind,
                    descriptor,
                    actualParentDirectory.id
                )
            }
        }

        fun processDirectory(path: Path) {
            val pathString = path.absolutePathString()
            directoryCache.getOrPut(path) {
                if (existingFilePaths.contains(pathString)) {
                    checkNotNull(libraryDao.findByExactPath(pathString))
                } else {
                    val parentLink = directoryCache[checkNotNull(path.parent)] ?: libraryDirectory
                    libraryDao.insertDirectory(parentLink.id, libraryDb.id, pathString)
                        .also { _newDirectoryIds.add(it.id) }
                }
            }
        }
    }

    private fun addGidOrSetActiveState(mediaLinkGid: String) {
        mediaScannerState.update { state ->
            (state as? MediaScannerState.Active ?: MediaScannerState.Active()).run {
                copy(mediaLinkGids = mediaLinkGids + mediaLinkGid)
            }
        }
    }

    private fun removeGidOrIdleState(mediaLinkGid: String) {
        mediaScannerState.update { state ->
            if (state is MediaScannerState.Active) {
                val updatedGids = state.mediaLinkGids - mediaLinkGid
                if (updatedGids.isEmpty()) {
                    MediaScannerState.Idle
                } else {
                    state.copy(mediaLinkGids = updatedGids)
                }
            } else {
                MediaScannerState.Idle
            }
        }
    }
}
