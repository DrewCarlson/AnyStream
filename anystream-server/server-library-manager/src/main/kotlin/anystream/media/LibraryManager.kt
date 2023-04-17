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
package anystream.media

import anystream.db.MediaLinkDao
import anystream.db.model.MediaLinkDb
import anystream.db.model.StreamEncodingDetailsDb
import anystream.media.util.toStreamEncodingDetails
import anystream.models.MediaKind
import anystream.models.MediaLink
import anystream.models.api.MediaAnalyzerResult
import anystream.models.api.MediaScanRequest
import anystream.models.api.MediaScanResult
import anystream.models.backend.MediaScannerState
import anystream.util.ObjectId
import com.github.kokorin.jaffree.JaffreeException
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffprobe.FFprobe
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jdbi.v3.core.JdbiException
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

internal val VIDEO_EXTENSIONS = listOf(
    "webm", "mpg", "mp2", "mpeg", "mov", "mkv",
    "avi", "m4p", "mp4", "ogg", "mts", "m2ts",
    "ts", "wmv", "mpe", "mpv",
)

internal val AUDIO_EXTENSIONS = listOf(
    "3gp", "aac", "flac", "ogg", "mp3", "opus",
    "wav", "m4b", "oga", "tta",
)

internal val SUBTITLE_EXTENSIONS = listOf(
    "sub",
    "srt",
    "vtt",
    "ass",
    "ssa",
)

class LibraryManager(
    private val ffprobe: () -> FFprobe,
    private val processors: List<MediaFileProcessor>,
    private val mediaLinkDao: MediaLinkDao,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val mediaFileScanner = MediaFileScanner(mediaLinkDao)

    init {
        // scope.launch { watchLibraries() }
    }

    val mediaScannerState: StateFlow<MediaScannerState>
        get() = mediaFileScanner.state

    fun addLibraryFolder(userId: Int, path: String, mediaKind: MediaKind): AddLibraryFolderResult {
        val libraryFile = File(path)
        if (!libraryFile.exists() || !libraryFile.isDirectory) {
            logger.debug("Invalid library folder path '$path'")
            return AddLibraryFolderResult.FileError(
                exists = libraryFile.exists(),
                isDirectory = libraryFile.isDirectory,
            )
        }

        val existingLink = mediaLinkDao.findByFilePath(path)
        if (existingLink != null) {
            logger.debug("MediaLink already exists for '$path'")
            return AddLibraryFolderResult.LinkAlreadyExists
        }
        val newLink = MediaLinkDb(
            id = -1,
            gid = ObjectId.get().toString(),
            addedByUserId = userId,
            descriptor = MediaLink.Descriptor.ROOT_DIRECTORY,
            mediaKind = mediaKind,
            type = MediaLinkDb.Type.LOCAL,
            directory = true,
            filePath = path,
        )

        return try {
            val newId = mediaLinkDao.insertLink(newLink)
            logger.debug("Added new library root MediaLink {}", newLink)
            AddLibraryFolderResult.Success(mediaLink = newLink.copy(id = newId))
        } catch (e: JdbiException) {
            logger.error("Failed to insert new library root MediaLink $newLink", e)
            AddLibraryFolderResult.DatabaseError(e)
        }
    }

    fun removeLibraryFolder(gid: String): Boolean {
        return try {
            val link = mediaLinkDao.findByGid(gid) ?: return false
            mediaLinkDao.deleteByBasePath(checkNotNull(link.filePath))
            true
        } catch (e: JdbiException) {
            logger.error("Failed to remove library folder '$gid'", e)
            false
        }
    }

    fun scanForMedia(
        userId: Int,
        libraryLink: MediaLinkDb,
        childMediaLink: MediaLinkDb? = null,
    ): MediaScanResult {
        return mediaFileScanner.scanForMedia(userId, libraryLink, childMediaLink)
    }

    suspend fun refreshMetadata(userId: Int, mediaLinkGid: String) {
        refreshMetadata(userId, checkNotNull(mediaLinkDao.findByGid(mediaLinkGid)))
    }

    suspend fun refreshMetadata(userId: Int, mediaLink: MediaLinkDb) {
        val processor = processors.firstOrNull { it.mediaKinds.contains(mediaLink.mediaKind) } ?: run {
            logger.error("No processor found for MediaKind '${mediaLink.mediaKind}'")
            return // TODO: return no processor result
        }

        when (mediaLink.descriptor) {
            MediaLink.Descriptor.ROOT_DIRECTORY -> {
                mediaLinkDao.findByBasePathAndDescriptor(
                    mediaLink.filePath.orEmpty(),
                    MediaLink.Descriptor.MEDIA_DIRECTORY,
                )
            }
            MediaLink.Descriptor.MEDIA_DIRECTORY -> listOf(mediaLink)
            MediaLink.Descriptor.CHILD_DIRECTORY ->
                listOfNotNull(mediaLinkDao.findByGid(checkNotNull(mediaLink.parentMediaLinkGid)))
            else -> return
        }.filter { it.mediaKind == mediaLink.mediaKind }
            .forEach { childLink ->
                processor.matchMediaLinkMetadata(childLink, userId)
            }
    }

    suspend fun analyzeMediaFiles(mediaLinkIds: List<String>): List<MediaAnalyzerResult> {
        val mediaLinks = mediaLinkDao.findByGids(mediaLinkIds)

        logger.debug("Importing stream details for ${mediaLinkIds.size} item(s)")
        logger.debug("Ignored ${mediaLinkIds.size - mediaLinks.size} invalid item(s)")

        return mediaLinks
            .filter { mediaLink ->
                // TODO: Support audio files
                val extension = mediaLink.filePath?.substringAfterLast('.', "")?.lowercase()
                !extension.isNullOrBlank() && VIDEO_EXTENSIONS.contains(extension)
            }
            .map { mediaLink ->
                val result = processMediaFileStreams(mediaLink)
                val streamDetails = (result as? MediaAnalyzerResult.Success)?.streams.orEmpty()
                    .map { stream -> StreamEncodingDetailsDb.fromModel(stream) }
                try {
                    if (streamDetails.isNotEmpty()) {
                        mediaLinkDao.insertStreamDetails(streamDetails)
                    }
                    result
                } catch (e: JdbiException) {
                    logger.error("Failed to update stream data", e)
                    MediaAnalyzerResult.ErrorDatabaseException(e.stackTraceToString())
                }
            }
            .also { results ->
                logger.debug("Processed ${results.size} item(s)")
            }
            .ifEmpty { listOf(MediaAnalyzerResult.ErrorNothingToImport) }
    }

    private suspend fun processMediaFileStreams(mediaLink: MediaLinkDb): MediaAnalyzerResult {
        if (!File(mediaLink.filePath.orEmpty()).exists()) {
            logger.error("Media file reference path does not exist: ${mediaLink.id} ${mediaLink.filePath}")
            return MediaAnalyzerResult.ErrorFileNotFound
        }

        return try {
            val streams = awaitAll(
                ffprobe().processStreamsAsync(mediaLink, StreamType.VIDEO_NOT_PICTURE),
                ffprobe().processStreamsAsync(mediaLink, StreamType.AUDIO),
                ffprobe().processStreamsAsync(mediaLink, StreamType.SUBTITLE),
            ).flatten()
            MediaAnalyzerResult.Success(mediaLink.gid, streams.map { it.toModel() })
        } catch (e: JaffreeException) {
            logger.error("FFProbe error, failed to extract stream details", e)
            MediaAnalyzerResult.ProcessError(e.stackTraceToString())
        }
    }

    private fun FFprobe.processStreamsAsync(
        mediaLink: MediaLinkDb,
        streamType: StreamType,
    ): Deferred<List<StreamEncodingDetailsDb>> {
        return scope.async {
            setShowStreams(true)
            setShowFormat(true)
            setSelectStreams(streamType)
            setShowEntries("stream=index:stream_tags=language,LANGUAGE,title")
            setInput(mediaLink.filePath.orEmpty())
            execute().streams.mapNotNull { stream ->
                stream.toStreamEncodingDetails(requireNotNull(mediaLink.id))
            }
        }
    }

    // Within a specified content directory, find all content unknown to anystream
    fun findUnmappedFiles(request: MediaScanRequest): List<String> {
        val contentFile = File(request.filePath)
        if (!contentFile.exists()) {
            return emptyList()
        }

        val mediaLinkPaths = mediaLinkDao.findAllFilePaths()
        return contentFile.listFiles()
            .orEmpty()
            .map(File::getAbsolutePath)
            .filter { filePath ->
                mediaLinkPaths.none { ref ->
                    ref.startsWith(filePath)
                }
            }
    }

    /*private suspend fun watchLibraries() {
        val rootLinks = mediaLinkDao.findByDescriptor(MediaLink.Descriptor.ROOT_DIRECTORY)
        val watchDirs = rootLinks
            .flatMap { rootLink ->
                mediaLinkDao.findByBasePathAndDescriptors(
                    checkNotNull(rootLink.filePath),
                    listOf(
                        MediaLink.Descriptor.MEDIA_DIRECTORY,
                        MediaLink.Descriptor.CHILD_DIRECTORY,
                    ),
                ) + rootLink
            }
            .associateBy { Path(checkNotNull(it.filePath)) }
            .toMutableMap()
        logger.debug("Watching ${watchDirs.size} directories for file changes")
        createLibraryWatcher(watchDirs.keys.toList()) { path, unregister ->
            logger.trace("File changed in library folder ${path.absolutePathString()}")
            if (path.notExists()) {
                logger.trace("Deleting removed path ${path.absolutePathString()}")
                watchDirs.remove(path)
                unregister(path)
                mediaLinkDao.deleteByBasePath(path.absolutePathString())
                return@createLibraryWatcher
            }
            val target = if (path.toFile().isDirectory) path else path.parent
            val rootLink = watchDirs[target.parent] ?: return@createLibraryWatcher
            logger.debug("Scanning media files in folder: $target")
            when (val result = scanForMedia(rootLink.addedByUserId, target.absolutePathString())) {
                is MediaScanResult.Success -> {
                    logger.debug("Refreshing metadata of new media files")
                    refreshMetadata(rootLink.addedByUserId, rootLink)
                }

                else -> {
                    logger.error("Watched directory scan failed: $result")
                }
            }
        }.collect()
    }*/

    private fun createLibraryWatcher(
        directories: List<Path>,
        onEach: suspend (Path, unregister: (Path) -> Unit) -> Unit,
    ): Flow<Path> {
        val watchService = FileSystems.getDefault().newWatchService()
        val watchKeys = mutableMapOf<WatchKey, Path>()
        val unregisterPath = { path: Path ->
            val pathString = path.absolutePathString()
            logger.trace("Stopped watching $pathString")
            val key = watchKeys.entries.firstOrNull { (_, value) ->
                value.absolutePathString() == pathString
            }?.key
            watchKeys.remove(key)
            key?.cancel()
        }
        return callbackFlow<Path> {
            directories.forEach { directory ->
                val watchKey = directory.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                )

                watchKeys[watchKey] = directory
            }
            while (isActive) {
                val key = watchService.poll(2, TimeUnit.SECONDS)
                key?.pollEvents()?.forEach { event ->
                    val targetPath = event.context() as Path
                    send(watchKeys.getValue(key).resolve(targetPath))
                }
                if (key?.reset() == false) break
            }
            awaitClose {
                try {
                    watchService.close()
                } catch (e: IOException) {
                    logger.error("Failed to close File Watch Service", e)
                }
            }
        }.flowOn(Dispatchers.IO)
            .onEach { onEach(it, unregisterPath) }
    }
}

sealed class AddLibraryFolderResult {

    data class Success(
        val mediaLink: MediaLinkDb,
    ) : AddLibraryFolderResult()

    data class FileError(
        val exists: Boolean,
        val isDirectory: Boolean,
    ) : AddLibraryFolderResult()

    object LinkAlreadyExists : AddLibraryFolderResult()

    data class DatabaseError(
        val exception: JdbiException,
    ) : AddLibraryFolderResult()
}
