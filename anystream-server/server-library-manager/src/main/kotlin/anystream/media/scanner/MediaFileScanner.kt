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

import anystream.db.MediaLinkDao
import anystream.db.model.MediaLinkDb
import anystream.media.AUDIO_EXTENSIONS
import anystream.media.SUBTITLE_EXTENSIONS
import anystream.media.VIDEO_EXTENSIONS
import anystream.models.MediaKind
import anystream.models.MediaLink.Descriptor
import anystream.models.api.MediaScanResult
import anystream.models.backend.MediaScannerMessage
import anystream.models.backend.MediaScannerState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
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

    private val _messages = MutableSharedFlow<MediaScannerMessage>(
        replay = 0,
        extraBufferCapacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: Flow<MediaScannerMessage> = _messages.asSharedFlow()
    val state: StateFlow<MediaScannerState> = mediaScannerState.asStateFlow()

    fun scan(mediaLink: MediaLinkDb): MediaScanResult {
        return try {
            addGidOrSetActiveState(mediaLink.gid)
            when (mediaLink.descriptor) {
                Descriptor.ROOT_DIRECTORY -> scanRootDirectoryLink(mediaLink)
                Descriptor.MEDIA_DIRECTORY -> scanMediaDirectoryLink(mediaLink)
                Descriptor.CHILD_DIRECTORY -> scanChildDirectoryLink(mediaLink)
                Descriptor.VIDEO,
                Descriptor.AUDIO,
                Descriptor.SUBTITLE,
                Descriptor.IMAGE,
                -> scanFileLink(mediaLink)
            }
        } finally {
            removeGidOrIdleState(mediaLink.gid)
        }
    }

    private fun scanRootDirectoryLink(mediaLink: MediaLinkDb): MediaScanResult {
        require(mediaLink.descriptor == Descriptor.ROOT_DIRECTORY)
        return scanMediaFolder(mediaLink, 1)
    }

    private fun scanMediaDirectoryLink(mediaLink: MediaLinkDb): MediaScanResult {
        require(mediaLink.descriptor == Descriptor.MEDIA_DIRECTORY)
        return scanMediaFolder(mediaLink, 1)
    }

    private fun scanChildDirectoryLink(mediaLink: MediaLinkDb): MediaScanResult {
        require(mediaLink.descriptor == Descriptor.CHILD_DIRECTORY)
        return scanMediaFolder(mediaLink, 1)
    }

    private fun scanFileLink(mediaLink: MediaLinkDb): MediaScanResult {
        require(!mediaLink.descriptor.isDirectoryLink())
        val mediaFile = File(checkNotNull(mediaLink.filePath))
        if (!mediaFile.exists() || mediaFile.isDirectory) {
            logger.debug("Media link file is missing {}", mediaLink)
            return MediaScanResult.ErrorFileNotFound
        }
        return MediaScanResult.Success(
            parentMediaLinkGid = mediaLink.parentMediaLinkGid,
            addedMediaLinkGids = emptyList(),
            removedMediaLinkGids = emptyList(),
            existingMediaLinkGids = listOf(mediaLink.gid),
        )
    }

    private fun scanMediaFolder(mediaLink: MediaLinkDb, userId: Int): MediaScanResult {
        val targetFolder = File(checkNotNull(mediaLink.filePath))
        if (!targetFolder.exists() || targetFolder.isFile) {
            logger.debug("Root content directory not found: ${targetFolder.absolutePath}")
            return MediaScanResult.ErrorFileNotFound
        }
        val (existingFilePaths, removedFilePaths) = try {
            mediaLinkDao.findFilePathsByBasePath(targetFolder.absolutePath)
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
            targetFolder.absolutePath,
            removedCount,
        )

        val descriptorFilters = when (mediaLink.mediaKind) {
            MediaKind.MOVIE, MediaKind.TV -> MOVIE_TV_DESCRIPTOR_EXTENSION_MAP
            MediaKind.AUDIOBOOK, MediaKind.MUSIC -> AUDIOBOOK_MUSIC_DESCRIPTOR_EXTENSION_MAP
            else -> {
                logger.warn("No supported file extensions for MediaKind ${mediaLink.mediaKind}")
                return MediaScanResult.ErrorNothingToScan
            }
        }
        val allFilterExtensions = descriptorFilters.keys.flatten()
        val processor = Processor(userId, mediaLink, descriptorFilters, existingFilePaths)
        val mediaFileLinks = targetFolder.walk()
            .onEnter { file ->
                processor.processDirectory(file)
                true
            }
            .filter { file ->
                // Ignore files with existing links or unrecognized file types
                file.isFile && !existingFilePaths.contains(file.absolutePath) &&
                        file.extension.lowercase().run(allFilterExtensions::contains)
            }
            .groupBy { file ->
                if (targetFolder == file.parentFile) {
                    targetFolder.absolutePath
                } else {
                    var selectedRoot = file
                    while (selectedRoot.parentFile != targetFolder) {
                        selectedRoot = selectedRoot.parentFile
                    }
                    selectedRoot.absolutePath
                }
            }
            .flatMap { (parent, files) ->
                processor.processFiles(files, parent)
            }
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
                parentMediaLinkGid = mediaLink.gid,
                addedMediaLinkGids = addedGids,
                removedMediaLinkGids = removedGids,
                existingMediaLinkGids = existing,
            )
        } catch (e: JdbiException) {
            logger.error("Failed to insert new media links", e)
            MediaScanResult.ErrorDatabaseException(e.stackTraceToString())
        } finally {
            removeGidOrIdleState(mediaLink.gid)
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
            return files.map { file ->
                val actualParentLink = if (file.parent == parent) {
                    parentLink
                } else {
                    mediaDirectoryLinks.getValue(file.parent)
                }
                val descriptor = descriptorFilters.firstNotNullOf { (extensions, descriptor) ->
                    descriptor.takeIf { file.extension.lowercase().run(extensions::contains) }
                }
                MediaLinkDb.fromFile(file, libraryLink.mediaKind, userId, descriptor, actualParentLink)
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
