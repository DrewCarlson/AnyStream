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
import anystream.media.LibraryService
import anystream.models.Descriptor
import anystream.models.MediaLink
import anystream.models.api.*
import anystream.models.filename
import anystream.util.koinGet
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun Route.addMediaLinkManageRoutes(
    libraryService: LibraryService = koinGet(),
    mediaLinkDao: MediaLinkDao = koinGet(),
) {
    route("/medialink") {
        get {
            val parent = call.parameters["parent"]
            val result = when {
                !parent.isNullOrBlank() -> mediaLinkDao.findByDirectoryId(parent)
                else -> mediaLinkDao.all()
            }
                // TODO: Sort in query
                .sortedBy { it.filename }
            call.respond(result)
        }

        route("/libraries") {
            get {
                call.respond(LibraryFolderList(libraryService.getLibraryFolders()))
            }

            post("/unmapped") {
                val import = runCatching { call.receiveNullable<MediaScanRequest>() }
                    .getOrNull() ?: return@post call.respond(UnprocessableEntity)
                call.respond(libraryService.findUnmappedFiles(import))
            }

            get("/list-files") {
                val root = call.parameters["root"]
                val showFiles = call.parameters["showFiles"]?.toBoolean() ?: false

                call.respond(libraryService.listFiles(root, showFiles))
            }
        }

        route("/{mediaLinkId}") {
            route("/matches") {
                get {
                    val mediaLink = mediaLink() ?: return@get call.respond(NotFound)
                    val matches = libraryService.refreshMetadata(mediaLink, import = false)
                    call.respond(matches)
                }

                put {
                    val mediaLink = mediaLink() ?: return@put call.respond(NotFound)
                    val body = call.receive<JsonObject>()
                    val remoteId = body["remoteId"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?: return@put call.respond(UnprocessableEntity)
                    libraryService.matchMediaLink(mediaLink, remoteId)
                    call.respond(OK)
                }
            }

            get("/scan") {
                val mediaLink = mediaLink() ?: return@get call.respond(NotFound)
                //call.respond(libraryManager.scan(mediaLink))
                // TODO: Restore scan endpoint
            }

            delete {
                val mediaLink = mediaLink() ?: return@delete call.respond(NotFound)
                if (libraryService.removeMediaLink(mediaLink)) {
                    call.respond(OK)
                } else {
                    call.respond(NotFound)
                }
            }

            get("/analyze") {
                val waitForResult = call.parameters["waitForResult"]?.toBoolean() ?: false
                val mediaLink = mediaLink() ?: return@get call.respond(UnprocessableEntity)

                val descriptors = listOf(Descriptor.VIDEO, Descriptor.AUDIO)
                val mediaLinkIds = mediaLinkDao
                    .findIdsByMediaLinkIdAndDescriptors(mediaLink.id, descriptors)
                    .takeIf { it.isNotEmpty() }
                    ?: return@get call.respond(NotFound)

                if (waitForResult) {
                    call.respond(libraryService.analyzeMediaFiles(mediaLinkIds, overwrite = true))
                } else {
                    application.launch {
                        libraryService.analyzeMediaFiles(mediaLinkIds, overwrite = true)
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
        route("/{mediaLinkId}") {
            get {
                val includeMetadata = call.parameters["includeMetadata"]?.toBoolean() ?: false
                val mediaLink = mediaLink() ?: return@get
                val session = call.sessions.get<UserSession>()!!
                val metadataId = mediaLink.metadataId
                val metadata = if (includeMetadata && metadataId != null) {
                    queries.findMediaById(
                        metadataId = metadataId,
                        includeLinks = false,
                        includePlaybackStateForUser = session.userId,
                    )
                } else {
                    null
                }
                val response = MediaLinkResponse(
                    mediaLink = mediaLink,
                    metadata = metadata,
                )
                call.respond(response)
            }
        }
    }
}

private suspend fun RoutingContext.mediaLink(
    mediaLinkDao: MediaLinkDao = koinGet(),
): MediaLink? {
    val mediaLinkId = call.parameters["mediaLinkId"]
        ?.takeIf(String::isNotBlank)
        ?: return run {
            call.respond(UnprocessableEntity)
            null
        }
    return mediaLinkDao.findById(mediaLinkId)
}
