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

import anystream.db.*
import anystream.media.analyzer.MediaFileAnalyzer
import anystream.media.processor.MediaFileProcessor
import anystream.media.scanner.MediaFileScanner
import anystream.models.*
import anystream.models.api.*
import anystream.models.backend.MediaScannerMessage
import anystream.models.backend.MediaScannerState
import anystream.util.toHumanReadableSize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

internal val VIDEO_EXTENSIONS = listOf(
    "webm", "mpg", "mp2", "mpeg", "mov", "mkv",
    "avi", "m4p", "mp4", "mts", "m2ts",
    "ts", "wmv", "mpe", "mpv", "m4v"
)

internal val AUDIO_EXTENSIONS = listOf(
    "3gp", "aac", "flac", "ogg", "mp3", "opus",
    "wav", "m4b", "oga", "tta",
)

internal val SUBTITLE_EXTENSIONS = listOf(
    "sub", "srt", "vtt", "ass", "ssa",
)

class LibraryService(
    private val mediaFileAnalyzer: MediaFileAnalyzer,
    private val processors: List<MediaFileProcessor>,
    private val mediaLinkDao: MediaLinkDao,
    private val libraryDao: LibraryDao,
    private val fs: FileSystem,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mediaFileScanner = MediaFileScanner(mediaLinkDao, libraryDao, fs)

    val mediaScannerState: StateFlow<MediaScannerState> = mediaFileScanner.state
    val mediaScannerMessages: Flow<MediaScannerMessage> = mediaFileScanner.messages

    suspend fun initializeLibraries(
        preconfiguredDirectories: Map<MediaKind, List<String>>
    ) {
        if (!libraryDao.insertDefaultLibraries()) {
            return
        }
        libraryDao.all().forEach { library ->
            val directories = preconfiguredDirectories[library.mediaKind] ?: return@forEach
            directories.forEach { directory ->
                addLibraryFolderAndScan(library.id, directory)
            }
        }
    }

    suspend fun getLibrary(libraryId: String): Library? {
        return try {
            libraryDao.fetchLibrary(libraryId)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun getLibraries(): List<Library> {
        return try {
            libraryDao.all()
        } catch (e: Throwable) {
            logger.error("Failed to fetch libraries", e)
            emptyList()
        }
    }

    suspend fun getLibraryDirectories(libraryId: String): List<Directory> {
        return libraryDao.fetchDirectoriesByLibrary(libraryId)
    }

    suspend fun getLibraryRootDirectories(libraryId: String): List<Directory> {
        return libraryDao.fetchLibraryRootDirectories(libraryId)
    }

    suspend fun getDirectory(directoryId: String): Directory? {
        return libraryDao.fetchDirectory(directoryId)
    }

    /**
     * Remove the directory with [directoryId] and all [MediaLink]s contained within.
     */
    suspend fun removeDirectory(directoryId: String): Boolean {
        val directory = libraryDao.fetchDirectory(directoryId) ?: return false

        // TODO: cleanup abandoned media links and directories since they
        //      are not be updated with correct parents when re-added.
        mediaLinkDao.deleteByBasePath(directory.filePath)

        libraryDao.deleteDirectoriesByParent(directory.id)

        return libraryDao.deleteDirectory(directoryId)
    }

    suspend fun addLibraryFolderAndScan(libraryId: String, path: String): AddLibraryFolderResponse {
        val result = addLibraryFolder(libraryId, path)
        if (result is AddLibraryFolderResponse.Success) {
            val directory = result.directory

            scope.launch(Dispatchers.Default) {
                var scanning = true
                mediaScannerMessages
                    .takeWhile { scanning }
                    .filterIsInstance<MediaScannerMessage.ScanDirectoryCompleted>()
                    .filter { it.directory.id == directory.id }
                    .onEach { message ->
                        val child = message.child
                        if (child == null) {
                            scanning = false
                        } else {
                            refreshMetadata(child)
                        }
                    }
                    .launchIn(this)
                scan(directory)
            }
        }
        return result
    }

    suspend fun addLibraryFolder(libraryId: String, path: String): AddLibraryFolderResponse {
        val libraryFile = Path(path)
        if (!libraryFile.exists() || !libraryFile.isDirectory()) {
            logger.debug("Invalid library folder path '{}'", path)
            return AddLibraryFolderResponse.FileError(
                exists = libraryFile.exists(),
                isDirectory = false,
            )
        }

        val library = libraryDao.fetchLibrary(libraryId)
            ?: return AddLibraryFolderResponse.LibraryFolderExists

        if (libraryDao.fetchDirectoryByPath(path) != null) {
            logger.debug("Library already exists with directory '{}'", path)
            return AddLibraryFolderResponse.LibraryFolderExists
        }

        return try {
            val directory = libraryDao.insertDirectory(null, library.id, path)
            logger.debug("Added directory {} to library {}", directory, library)
            AddLibraryFolderResponse.Success(library, directory)
        } catch (e: Throwable) {
            logger.error("Failed to insert new library for $path", e)
            AddLibraryFolderResponse.DatabaseError(e.stackTraceToString())
        }
    }

    suspend fun removeMediaLink(mediaLink: MediaLink): Boolean {
        return try {
            when (mediaLink.descriptor) {
                Descriptor.VIDEO,
                Descriptor.AUDIO,
                Descriptor.SUBTITLE,
                Descriptor.IMAGE,
                    -> {
                    mediaLinkDao.deleteById(mediaLink.id)
                }
            }

            true
        } catch (e: Throwable) {
            logger.error("Database error while removing media link ${mediaLink.id}", e)
            false
        }
    }

    suspend fun listFiles(root: String?, showFiles: Boolean): ListFilesResponse {
        val folders: List<String>
        var files = emptyList<String>()
        if (root.isNullOrBlank()) {
            val rootDirectories = libraryDao.fetchLibraryRootDirectories()
                .map(Directory::filePath)
            val (foldersList, filesList) = fs.rootDirectories.partition { it.isDirectory() }
            folders = rootDirectories + foldersList.map(Path::absolutePathString).filterNot(rootDirectories::contains)
            files = filesList.map(Path::absolutePathString)
        } else {
            val rootDir = try {
                fs.getPath(root)
            } catch (e: InvalidPathException) {
                null
            }
            if (rootDir == null || rootDir.notExists()) {
                return ListFilesResponse()
            }
            if (rootDir.isDirectory()) {
                val (folderPaths, filePaths) = withContext(Dispatchers.IO) {
                    Files.newDirectoryStream(rootDir).use { stream ->
                        stream.partition { it.isDirectory() }
                    }
                }
                folders = folderPaths.map { it.absolutePathString() }
                files = filePaths.map { it.absolutePathString() }
            } else {
                folders = listOf(rootDir.absolutePathString())
            }
        }
        return ListFilesResponse(folders, if (showFiles) files else emptyList())
    }

    suspend fun scan(directory: Directory): MediaScanResult {
        return mediaFileScanner.scan(directory)
    }

    suspend fun refreshMetadata(directoryId: String): List<MediaLinkMatchResult> {
        val directory = libraryDao.fetchDirectory(directoryId)
            ?: return emptyList()// TODO: return no directory error
        return refreshMetadata(directory)
    }

    suspend fun refreshMetadata(directory: Directory): List<MediaLinkMatchResult> {
        val library = libraryDao.fetchLibraryForDirectory(directory.id)
            ?: return emptyList() // TODO: return no library error

        val processor = processors.firstOrNull { it.mediaKinds.contains(library.mediaKind) } ?: run {
            logger.error("No processor found for MediaKind '{}'", library.mediaKind)
            return emptyList() // TODO: return no processor result
        }

        return processor.findMetadataMatches(directory, import = true)
    }

    suspend fun refreshMetadata(mediaLink: MediaLink, import: Boolean): MediaLinkMatchResult {
        val processor = processors.firstOrNull { it.mediaKinds.contains(mediaLink.mediaKind) } ?: run {
            logger.error("No processor found for MediaKind '{}'", mediaLink.mediaKind)
            TODO("return no processor result")
        }

        return processor.findMetadataMatches(mediaLink, import)
    }

    suspend fun matchMediaLink(mediaLink: MediaLink, remoteId: String) {
        val processor = processors.firstOrNull { it.mediaKinds.contains(mediaLink.mediaKind) } ?: run {
            logger.error("No processor found for MediaKind '{}'", mediaLink.mediaKind)
            return // TODO: return no processor result
        }
        val metadataMatch = processor.findMetadata(mediaLink, remoteId) ?: run {
            logger.warn("Metadata not found for remoteId: {}", remoteId)
            return
        }
        matchMediaLink(mediaLink, metadataMatch)
    }

    suspend fun matchMediaLink(mediaLink: MediaLink, metadataMatch: MetadataMatch) {
        val processor = processors.firstOrNull { it.mediaKinds.contains(mediaLink.mediaKind) } ?: run {
            logger.error("No processor found for MediaKind '{}'", mediaLink.mediaKind)
            return // TODO: return no processor result
        }

        when (mediaLink.descriptor) {
            Descriptor.VIDEO -> listOf(mediaLink)
            else -> {
                // TODO: return unhandled files result
                return
            }
        }.filter { it.mediaKind == mediaLink.mediaKind }
            .map { childLink ->
                processor.importMetadataMatch(childLink, metadataMatch)
            }
    }

    suspend fun getLibraryFolders(): List<LibraryFolderList.RootFolder> {
        return try {
            libraryDao.fetchLibrariesAndRootDirectories()
        } catch (e: Throwable) {
            logger.error("Failed to load ROOT_DIRECTORY media links", e)
            emptyMap()
        }.map { (library, directory) ->
            val filePath = directory?.filePath?.run(fs::getPath)
            // TODO: Use count query to get matched/unmatched numbers
            //val (matched, unmatched) = links.partition { it.metadataId != null || it.rootMetadataId != null }
            val matched = emptyList<String>()
            val unmatched = emptyList<String>()
            val fileStore = try {
                filePath?.fileStore()
            } catch (e: NoSuchFileException) {
                null
            }
            val freeSpace = try {
                fileStore?.usableSpace?.toHumanReadableSize()
            } catch (e: FileSystemException) {
                e.printStackTrace()
                null
            }
            val sizeOnDisk: String? = try {
                // TODO: calculate this elsewhere or get the size estimate from OS apis
                /*filePath
                    .walk()
                    .filter(Path::isRegularFile)
                    .sumOf { it.fileSize() }
                    .toHumanReadableSize()*/
                null
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }

            LibraryFolderList.RootFolder(
                libraryId = library.id,
                path = filePath?.absolutePathString() ?: "",
                mediaKind = library.mediaKind,
                mediaMatchCount = matched.size,
                unmatchedCount = unmatched.size,
                sizeOnDisk = sizeOnDisk,
                freeSpace = freeSpace,
            )
        }
    }

    suspend fun analyzeMediaFiles(
        mediaLinkIds: List<String>,
        overwrite: Boolean = false,
    ): List<MediaAnalyzerResult> {
        return mediaFileAnalyzer.analyzeMediaFiles(mediaLinkIds, overwrite)
    }

    // Within a specified content directory, find all content unknown to anystream
    suspend fun findUnmappedFiles(request: MediaScanRequest): List<String> {
        val contentFile = Path(request.filePath)
        if (!contentFile.exists() || !contentFile.isDirectory()) {
            return emptyList()
        }

        val mediaLinkPaths = mediaLinkDao.findAllFilePaths()
        return contentFile.listDirectoryEntries()
            .map(Path::absolutePathString)
            .filter { filePath ->
                mediaLinkPaths.none { ref ->
                    ref.startsWith(filePath)
                }
            }
    }

    /*private suspend fun watchLibraries() {
        val rootLinks = mediaLinkDao.findByDescriptor(Descriptor.ROOT_DIRECTORY)
        val watchDirs = rootLinks
            .flatMap { rootLink ->
                mediaLinkDao.findByBasePathAndDescriptors(
                    checkNotNull(rootLink.filePath),
                    listOf(
                        Descriptor.MEDIA_DIRECTORY,
                        Descriptor.CHILD_DIRECTORY,
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
            when (val result = scanForMedia(target.absolutePathString())) {
                is MediaScanResult.Success -> {
                    logger.debug("Refreshing metadata of new media files")
                    refreshMetadata(rootLink)
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
        val watchService = fs.newWatchService()
        val watchKeys = mutableMapOf<WatchKey, Path>()
        val unregisterPath: (Path) -> Unit = { path: Path ->
            val pathString = path.absolutePathString()
            logger.trace("Stopped watching {}", pathString)
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
