/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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

import anystream.data.MetadataDbQueries
import anystream.data.UserSession
import anystream.db.MediaLinkDao
import anystream.db.model.MediaLinkDb
import anystream.media.AddLibraryFolderResult
import anystream.media.LibraryManager
import anystream.models.MediaLink
import anystream.models.api.*
import anystream.util.koinGet
import anystream.util.logger
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.launch
import org.jdbi.v3.core.JdbiException

fun Route.addMediaLinkManageRoutes(
    libraryManager: LibraryManager = koinGet(),
    mediaLinkDao: MediaLinkDao = koinGet(),
) {
    route("/medialink") {
        get {
            val parent = call.parameters["parent"]
            val result = when {
                !parent.isNullOrBlank() -> mediaLinkDao.findByParentGid(parent)
                else -> mediaLinkDao.all()
            }.map { it.toModel() }
                // TODO: Sort in query
                .sortedBy { it.filename }
            call.respond(result)
        }

        route("/libraries") {
            get {
                call.respond(LibraryFolderList(libraryManager.getLibraryFolders()))
            }

            post {
                val (userId) = checkNotNull(call.principal<UserSession>())
                val request = try {
                    call.receive<AddLibraryFolderRequest>()
                } catch (e: ContentTransformationException) {
                    logger.error("Failed to parse request body", e)
                    return@post call.respond(UnprocessableEntity)
                }

                val (path, mediaKind) = request
                val response =
                    when (val result = libraryManager.addLibraryFolder(userId, path, mediaKind)) {
                        is AddLibraryFolderResult.Success -> {
                            application.launch {
                                libraryManager.scan(result.mediaLink)
                                libraryManager.refreshMetadata(userId, result.mediaLink, true)
                            }

                            AddLibraryFolderResponse.Success(result.mediaLink.toModel())
                        }

                        is AddLibraryFolderResult.DatabaseError ->
                            AddLibraryFolderResponse.DatabaseError(result.exception.stackTraceToString())

                        is AddLibraryFolderResult.FileError ->
                            AddLibraryFolderResponse.FileError(
                                exists = result.exists,
                                isDirectory = result.isDirectory,
                            )

                        AddLibraryFolderResult.LinkAlreadyExists -> AddLibraryFolderResponse.LibraryFolderExists
                    }
                call.respond(response)
            }

            post("/unmapped") {
                val import = runCatching { call.receiveNullable<MediaScanRequest>() }
                    .getOrNull() ?: return@post call.respond(UnprocessableEntity)

                call.respond(libraryManager.findUnmappedFiles(import))
            }

            get("/list-files") {
                val root = call.parameters["root"]
                val showFiles = call.parameters["showFiles"]?.toBoolean() ?: false

                call.respond(libraryManager.listFiles(root, showFiles))
            }
        }

        route("/{mediaLinkGid}") {
            route("/matches") {
                get {
                    val mediaLink = mediaLink() ?: return@get call.respond(NotFound)
                    val matches = libraryManager.refreshMetadata(1, mediaLink, false)
                    call.respond(matches)
                }

                put {
                    val mediaLink = mediaLink() ?: return@put call.respond(NotFound)
                    val match = call.receive<MetadataMatch>()
                    libraryManager.matchMediaLink(1, mediaLink, match)
                    call.respond(OK)
                }
            }

            get("/scan") {
                val mediaLink = mediaLink() ?: return@get call.respond(NotFound)
                call.respond(libraryManager.scan(mediaLink))
            }

            delete {
                val mediaLink = mediaLink() ?: return@delete call.respond(NotFound)
                if (libraryManager.removeMediaLink(mediaLink)) {
                    call.respond(OK)
                } else {
                    call.respond(NotFound)
                }
            }

            get("/analyze") {
                val waitForResult = call.parameters["waitForResult"]?.toBoolean() ?: false
                val mediaLink = mediaLink() ?: return@get call.respond(UnprocessableEntity)

                val descriptors = listOf(MediaLink.Descriptor.VIDEO, MediaLink.Descriptor.AUDIO)
                val mediaLinkIds = if (mediaLink.descriptor == MediaLink.Descriptor.ROOT_DIRECTORY) {
                    val basePath = checkNotNull(mediaLink.filePath)
                    mediaLinkDao.findGidsByBasePathAndDescriptors(basePath, descriptors)
                } else {
                    mediaLinkDao
                        .findGidsByMediaLinkGidAndDescriptors(mediaLink.gid, descriptors)
                }.takeIf { it.isNotEmpty() }
                    ?: return@get call.respond(NotFound)

                if (waitForResult) {
                    call.respond(libraryManager.analyzeMediaFiles(mediaLinkIds, overwrite = true))
                } else {
                    application.launch {
                        libraryManager.analyzeMediaFiles(mediaLinkIds, overwrite = true)
                    }
                    call.respond(OK)
                }
            }
        }
    }
}

fun Route.addMediaLinkViewRoutes(
    queries: MetadataDbQueries = koinGet(),
) {
    route("/medialink") {
        route("/{mediaLinkGid}") {
            get {
                val includeMetadata = call.parameters["includeMetadata"]?.toBoolean() ?: false
                val mediaLink = mediaLink() ?: return@get
                val session = call.sessions.get<UserSession>()!!
                val metadataGid = mediaLink.metadataGid
                val metadata = if (includeMetadata && metadataGid != null) {
                    queries.findMediaById(
                        mediaId = metadataGid,
                        includeLinks = false,
                        includePlaybackStateForUser = session.userId,
                    )
                } else {
                    null
                }
                val response = MediaLinkResponse(
                    mediaLink = mediaLink.toModel(),
                    metadata = metadata,
                )
                call.respond(response)
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.mediaLink(
    mediaLinkDao: MediaLinkDao = koinGet(),
): MediaLinkDb? {
    val mediaLinkGid = call.parameters["mediaLinkGid"]
        ?.takeIf(String::isNotBlank)
        ?: return run {
            call.respond(UnprocessableEntity)
            null
        }
    return try {
        mediaLinkDao.findByGid(mediaLinkGid)
    } catch (e: JdbiException) {
        logger.error("Failed to load media link", e)
        call.respond(NotFound)
        null
    }
}
