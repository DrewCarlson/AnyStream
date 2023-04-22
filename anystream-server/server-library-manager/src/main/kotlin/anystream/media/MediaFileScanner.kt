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
package anystream.media

import anystream.db.MediaLinkDao
import anystream.db.model.MediaLinkDb
import anystream.models.MediaKind
import anystream.models.MediaLink.Descriptor
import anystream.models.api.MediaScanResult
import anystream.models.backend.MediaScannerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jdbi.v3.core.JdbiException
import org.slf4j.LoggerFactory
import java.io.File

class MediaFileScanner(
    private val mediaLinkDao: MediaLinkDao,
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

    fun scanForMedia(userId: Int, libraryLink: MediaLinkDb, childLink: MediaLinkDb?): MediaScanResult {
        check(libraryLink.descriptor == Descriptor.ROOT_DIRECTORY)
        logger.debug("Media scan requested by userId=$userId: ${childLink?.filePath ?: libraryLink.filePath}")
        mediaScannerState.value = MediaScannerState.Starting
        val rootFolder = File(checkNotNull(libraryLink.filePath))
        if (!rootFolder.exists() || rootFolder.isFile) {
            logger.debug("Root content directory not found: ${rootFolder.absolutePath}")
            mediaScannerState.value = MediaScannerState.Idle
            return MediaScanResult.ErrorFileNotFound
        }

        mediaScannerState.value = MediaScannerState.Active(
            libraryLink = libraryLink.toModel(),
            currentLink = childLink?.toModel(),
        )
        val targetFile = File(childLink?.filePath ?: checkNotNull(libraryLink.filePath))
        return if (targetFile.isDirectory) {
            if (childLink == null) {
                scanMediaFolder(targetFile, libraryLink, userId)
            } else {
                scanMediaFolder(targetFile, childLink, userId)
            }
        } else {
            checkNotNull(childLink)
            if (targetFile.exists()) {
                MediaScanResult.ErrorNothingToScan
            } else {
                try {
                    mediaLinkDao.deleteByGid(childLink.gid)
                } catch (e: JdbiException) {
                    logger.error("Failed to delete stale MediaLink '${childLink.gid}'", e)
                }
                MediaScanResult.ErrorFileNotFound
            }
        }.also {
            mediaScannerState.value = MediaScannerState.Idle
        }
    }

    private fun scanMediaFolder(targetFile: File, libraryLink: MediaLinkDb, userId: Int): MediaScanResult {
        val (existingFilePaths, removedFilePaths) = try {
            mediaLinkDao.findFilePathsByBasePath(targetFile.absolutePath)
                .partition { File(it).exists() }
        } catch (e: JdbiException) {
            logger.error("Failed to find child media links", e)
            return MediaScanResult.ErrorDatabaseException(e.stackTraceToString())
        }
        val (removedGids, removedCount) = if (removedFilePaths.isNotEmpty()) {
            mediaLinkDao.findGidsByFilePaths(removedFilePaths) to mediaLinkDao.deleteByFilePaths(removedFilePaths)
        } else {
            emptyList<String>() to 0
        }

        logger.debug(
            "Found {} existing media links in {} and removed {} missing links.",
            existingFilePaths.size,
            targetFile.absolutePath,
            removedCount,
        )

        val descriptorFilters = when (libraryLink.mediaKind) {
            MediaKind.MOVIE, MediaKind.TV -> MOVIE_TV_DESCRIPTOR_EXTENSION_MAP
            MediaKind.AUDIOBOOK, MediaKind.MUSIC -> AUDIOBOOK_MUSIC_DESCRIPTOR_EXTENSION_MAP
            else -> {
                logger.warn("No supported file extensions for MediaKind ${libraryLink.mediaKind}")
                return MediaScanResult.ErrorNothingToScan
            }
        }
        val allFilterExtensions = descriptorFilters.keys.flatten()
        val processor = Processor(userId, libraryLink, descriptorFilters, existingFilePaths)
        val mediaFileLinks = targetFile.walk()
            .onEnter { file ->
                processor.processDirectory(file)
                true
            }
            .filter { file ->
                // Ignore files with existing links or unrecognized file types
                file.isFile && !existingFilePaths.contains(file.absolutePath) &&
                    file.extension.lowercase().run(allFilterExtensions::contains)
            }
            .groupBy(File::getParent)
            .flatMap { (parent, files) -> processor.processFiles(files, parent) }
            .toList()

        return try {
            logger.debug("Inserting ${mediaFileLinks.size} media file links.")
            mediaLinkDao.insertLink(mediaFileLinks)
            val existing = if (existingFilePaths.isNotEmpty()) {
                mediaLinkDao.findGidsByFilePaths(existingFilePaths)
            } else {
                emptyList()
            }
            val addedGids = processor.newDirectoryLinkGids + mediaFileLinks.map(MediaLinkDb::gid)
            return MediaScanResult.Success(
                parentMediaLinkGid = libraryLink.gid,
                addedMediaLinkGids = addedGids,
                removedMediaLinkGids = removedGids,
                existingMediaLinkGids = existing,
            )
        } catch (e: JdbiException) {
            logger.error("Failed to insert new media links", e)
            MediaScanResult.ErrorDatabaseException(e.stackTraceToString())
        }
    }

    inner class Processor(
        private val userId: Int,
        private val libraryLink: MediaLinkDb,
        private val descriptorFilters: Map<List<String>, Descriptor>,
        private val existingFilePaths: List<String>,
    ) {
        private val _newDirectoryLinkGids = mutableListOf<String>()
        private val mediaDirectoryLinks = mutableMapOf<String, MediaLinkDb>()
        val newDirectoryLinkGids: List<String>
            get() = _newDirectoryLinkGids.toList()

        fun processFiles(files: List<File>, parent: String): List<MediaLinkDb> {
            val parentLink = mediaDirectoryLinks.getValue(parent)
            when (parentLink.descriptor) {
                Descriptor.MEDIA_DIRECTORY ->
                    updateActiveState { copy(currentLink = parentLink.toModel()) }

                Descriptor.CHILD_DIRECTORY -> {
                    val parentFile = File(checkNotNull(parentLink.filePath))
                    val rootLink = mediaDirectoryLinks.getValue(parentFile.parent)
                    updateActiveState { copy(currentLink = rootLink.toModel()) }
                }

                else -> Unit
            }
            return files.map { file ->
                val descriptor = descriptorFilters.firstNotNullOf { (extensions, descriptor) ->
                    descriptor.takeIf { file.extension.lowercase().run(extensions::contains) }
                }
                MediaLinkDb.fromFile(file, libraryLink.mediaKind, userId, descriptor, parentLink)
            }
        }

        fun processDirectory(file: File) {
            val path = file.absolutePath
            mediaDirectoryLinks.getOrPut(path) {
                if (existingFilePaths.contains(path)) {
                    checkNotNull(mediaLinkDao.findByFilePath(path))
                } else {
                    val parentLink = mediaDirectoryLinks[checkNotNull(file.parent)] ?: libraryLink
                    val folderDescriptor = when (parentLink.descriptor) {
                        Descriptor.ROOT_DIRECTORY -> Descriptor.MEDIA_DIRECTORY
                        else -> Descriptor.CHILD_DIRECTORY
                    }
                    val link = MediaLinkDb.fromFile(file, libraryLink.mediaKind, userId, folderDescriptor, parentLink)
                    _newDirectoryLinkGids.add(link.gid)
                    link.copy(id = mediaLinkDao.insertLink(link))
                }
            }
        }
    }

    private fun updateActiveState(copyFunc: MediaScannerState.Active.() -> MediaScannerState.Active) {
        mediaScannerState.update { copyFunc(it as MediaScannerState.Active) }
    }
}
