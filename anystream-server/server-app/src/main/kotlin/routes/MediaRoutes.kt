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
package anystream.routes

import anystream.data.*
import anystream.db.model.MediaLinkDb
import anystream.media.AddLibraryFolderResult
import anystream.media.LibraryManager
import anystream.metadata.MetadataManager
import anystream.models.LocalMediaLink
import anystream.models.MediaLink
import anystream.models.api.*
import anystream.util.isRemoteId
import anystream.util.logger
import anystream.util.toHumanReadableSize
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdbi.v3.core.JdbiException
import java.io.File
import java.nio.file.*
import kotlin.io.path.*

fun Route.addMediaManageRoutes(
    libraryManager: LibraryManager,
    queries: MetadataDbQueries,
) {
    route("/media") {
        route("/library-folders") {
            get {
                val folderList = try {
                    queries.mediaLinkDao.findByDescriptor(MediaLink.Descriptor.ROOT_DIRECTORY)
                } catch (e: JdbiException) {
                    e.printStackTrace()
                    emptyList()
                }.map { mediaLink ->
                    val filePath = checkNotNull(mediaLink.filePath)
                    val (matched, unmatched) = queries.mediaLinkDao
                        .findByBasePathAndDescriptor(filePath, MediaLink.Descriptor.MEDIA_DIRECTORY)
                        .partition { it.metadataId != null || it.rootMetadataId != null }
                    val freeSpace = try {
                        Path(filePath).toFile().freeSpace.toHumanReadableSize()
                    } catch (e: FileSystemException) {
                        e.printStackTrace()
                        null
                    }
                    LibraryFolderList.RootFolder(
                        mediaLink = mediaLink.toModel() as LocalMediaLink,
                        mediaMatchCount = matched.size,
                        unmatchedFileCount = 0,
                        unmatchedFolderCount = unmatched.size,
                        sizeOnDisk = null,
                        freeSpace = freeSpace,
                    )
                }
                call.respond(LibraryFolderList(folderList))
            }
            post {
                val (userId) = checkNotNull(call.principal<UserSession>())
                val request = runCatching { call.receiveNullable<AddLibraryFolderRequest>() }
                    .getOrNull() ?: return@post call.respond(UnprocessableEntity)

                val (path, mediaKind) = request
                val response = when (val result = libraryManager.addLibraryFolder(userId, path, mediaKind)) {
                    is AddLibraryFolderResult.Success -> {
                        application.launch {
                            when (libraryManager.scanForMedia(userId, result.mediaLink)) {
                                is MediaScanResult.Success -> {
                                    libraryManager.refreshMetadata(userId, result.mediaLink)
                                }
                                is MediaScanResult.ErrorDatabaseException -> TODO()
                                MediaScanResult.ErrorFileNotFound -> TODO()
                                MediaScanResult.ErrorInvalidConfiguration -> TODO()
                                MediaScanResult.ErrorNothingToScan -> TODO()
                            }
                            // launch(Default) { libraryManager.refreshMetadata(session.userId, result.mediaLink) }
                            // if (scanResult.addedMediaLinkGids.isNotEmpty()) {
                            // launch(Default) { libraryManager.analyzeMediaFiles(scanResult.addedMediaLinkGids) }
                            // }
                        }
                        AddLibraryFolderResponse.Success(result.mediaLink.toModel())
                    }
                    is AddLibraryFolderResult.DatabaseError -> {
                        AddLibraryFolderResponse.DatabaseError(result.exception.stackTraceToString())
                    }
                    is AddLibraryFolderResult.FileError -> {
                        AddLibraryFolderResponse.FileError(result.exists, result.isDirectory)
                    }
                    AddLibraryFolderResult.LinkAlreadyExists -> AddLibraryFolderResponse.LibraryFolderExists
                }
                call.respond(response)
            }

            delete("/{libraryGid}") {
                val libraryGid = call.parameters["libraryGid"]
                    ?: return@delete call.respond(UnprocessableEntity)

                if (libraryManager.removeLibraryFolder(libraryGid)) {
                    call.respond(OK)
                } else {
                    call.respond(NotFound)
                }
            }

            get("/list-files") {
                val root = call.parameters["root"]
                val showFiles = call.parameters["showFiles"]?.toBoolean() ?: false
                val folders: List<String>
                var files = emptyList<String>()
                if (root.isNullOrBlank()) {
                    val rootLinks = queries.mediaLinkDao
                        .findByDescriptor(MediaLink.Descriptor.ROOT_DIRECTORY)
                        .mapNotNull(MediaLinkDb::filePath)
                    val (foldersList, filesList) = File.listRoots().orEmpty().partition(File::isDirectory)
                    folders = rootLinks + foldersList.map(File::getAbsolutePath).filterNot(rootLinks::contains)
                    files = filesList.map(File::getAbsolutePath)
                } else {
                    val rootDir = try {
                        FileSystems.getDefault().getPath(root)
                    } catch (e: InvalidPathException) {
                        null
                    }
                    if (rootDir == null || rootDir.notExists()) {
                        return@get call.respond(NotFound)
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
                call.respond(ListFilesResponse(folders, if (showFiles) files else emptyList()))
            }

            post("/unmapped") {
                val import = runCatching { call.receiveNullable<MediaScanRequest>() }
                    .getOrNull() ?: return@post call.respond(UnprocessableEntity)

                call.respond(libraryManager.findUnmappedFiles(import))
            }
        }

        route("/{metadataGid}") {
            get("/analyze") {
                val metadataGid = call.parameters["metadataGid"] ?: ""

                val mediaLinkIds = queries.mediaLinkDao.findGidsByMetadataGid(metadataGid)
                if (mediaLinkIds.isEmpty()) {
                    call.respond(emptyList<MediaAnalyzerResult>())
                } else {
                    call.respond(libraryManager.analyzeMediaFiles(mediaLinkIds))
                }
            }
            get("/refresh-metadata") {
                val metadataGid = call.parameters["metadataGid"] ?: ""
                val result = queries.findMediaById(metadataGid)

                if (result.hasResult()) {
                    logger.warn("No media found for $metadataGid")
                    return@get call.respond(MediaLookupResponse())
                }
            }
        }

        route("/links") {
            get {
                call.respond(queries.findAllMediaLinks())
            }
            get("/{link_id}") {
                val linkId = call.parameters["link_id"] ?: ""
                val link = queries.findMediaLinkByGid(linkId)
                if (link == null) {
                    call.respond(NotFound)
                } else {
                    call.respond(link)
                }
            }
        }
    }
}

fun Route.addMediaViewRoutes(
    metadataManager: MetadataManager,
    queries: MetadataDbQueries,
) {
    route("/media") {
        route("/{metadataGid}") {
            get {
                val session = checkNotNull(call.principal<UserSession>())
                val metadataGid = call.parameters["metadataGid"]
                    ?: return@get call.respond(NotFound)
                val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: true
                val includePlaybackState =
                    call.parameters["includePlaybackStates"]?.toBoolean() ?: true
                val playbackStateUserId = if (includePlaybackState) session.userId else null

                return@get if (metadataGid.isRemoteId) {
                    when (val queryResult = metadataManager.findByRemoteId(metadataGid)) {
                        is QueryMetadataResult.Success -> {
                            if (queryResult.results.isEmpty()) {
                                return@get call.respond(MediaLookupResponse())
                            }
                            val response = when (val match = queryResult.results.first()) {
                                is MetadataMatch.MovieMatch -> {
                                    MediaLookupResponse(movie = MovieResponse(match.movie))
                                }

                                is MetadataMatch.TvShowMatch -> {
                                    val tvExtras = queryResult.extras?.asTvShowExtras()
                                    when {
                                        tvExtras?.episodeNumber != null -> MediaLookupResponse(
                                            episode = EpisodeResponse(match.episodes.first(), match.tvShow)
                                        )

                                        tvExtras?.seasonNumber != null -> MediaLookupResponse(
                                            season = SeasonResponse(match.tvShow, match.seasons.first(), match.episodes)
                                        )

                                        else -> MediaLookupResponse(
                                            tvShow = TvShowResponse(match.tvShow, match.seasons)
                                        )
                                    }
                                }
                            }
                            call.respond(response)
                        }

                        else -> call.respond(MediaLookupResponse())
                    }
                } else {
                    call.respond(
                        queries.findMediaById(
                            metadataGid,
                            includeLinks = includeLinks,
                            includePlaybackStateForUser = playbackStateUserId,
                        )
                    )
                }
            }
        }

        get("/by-link/{linkGid}") {
            val linkId = call.parameters["linkGid"]
                ?: return@get call.respond(MediaLookupResponse())
            val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: false

            val metadataGid = queries.findMetadataByLinkGid(linkId)
                ?: return@get call.respond(MediaLookupResponse())

            call.respond(
                MediaLookupResponse(
                    movie = queries.findMovieById(metadataGid, includeLinks = includeLinks),
                    tvShow = queries.findShowById(metadataGid, includeLinks = includeLinks),
                    episode = queries.findEpisodeById(metadataGid, includeLinks = includeLinks),
                    season = queries.findSeasonById(metadataGid, includeLinks = includeLinks),
                )
            )
        }
    }
}
