/*
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
import anystream.di.ServerScope
import anystream.jobs.GenerateVideoPreviewJob
import anystream.media.LibraryService
import anystream.models.Descriptor
import anystream.models.MediaLink
import anystream.models.Permission
import anystream.models.api.*
import anystream.models.filename
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.drewcarlson.ktor.permissions.withAnyPermission

@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<RoutingController>(),
)
@Inject
class MediaLinkRoutes(
    private val libraryService: LibraryService,
    private val mediaLinkDao: MediaLinkDao,
    private val queries: MetadataDbQueries,
    private val generateVideoPreviewJob: GenerateVideoPreviewJob,
    private val scope: CoroutineScope,
) : RoutingController {
    override fun init(parent: Route) {
        parent.route("/medialink") {
            authenticate {
                withAnyPermission(Permission.ViewCollection) {
                    route("/medialink") {
                        route("/{mediaLinkId}") {
                            get { getMediaLink() }
                        }
                    }
                }
                withAnyPermission(Permission.ManageCollection) {
                    get { getMediaLinks() }

                    route("/libraries") {
                        get { getLibraryFolders() }

                        post("/unmapped") { getUnmappedFiles() }

                        get("/list-files") { getListFiles() }
                    }

                    route("/{mediaLinkId}") {
                        get("/generate-preview") { getGeneratePreview() }

                        route("/matches") {
                            get { getMatches() }

                            put { putMediaLinkMatch() }
                        }

                        delete { deleteMediaLink() }

                        get("/analyze") { getAnalyzeMediaLink() }
                    }
                }
            }
        }
    }

    suspend fun RoutingContext.getMediaLinks() {
        val parent = call.parameters["parent"]
        val result = when {
            !parent.isNullOrBlank() -> mediaLinkDao.findByDirectoryId(parent)
            else -> mediaLinkDao.all()
        }
            // TODO: Sort in query
            .sortedBy { it.filename }
        call.respond(result)
    }

    suspend fun RoutingContext.getLibraryFolders() {
        call.respond(LibraryFolderList(libraryService.getLibraryFolders()))
    }

    suspend fun RoutingContext.getUnmappedFiles() {
        val import = runCatching { call.receiveNullable<MediaScanRequest>() }
            .getOrNull() ?: return call.respond(UnprocessableEntity)
        call.respond(libraryService.findUnmappedFiles(import))
    }

    suspend fun RoutingContext.getListFiles() {
        val root = call.parameters["root"]
        val showFiles = call.parameters["showFiles"]?.toBoolean() == true

        call.respond(libraryService.listFiles(root, showFiles))
    }

    suspend fun RoutingContext.getGeneratePreview() {
        val mediaLink = mediaLink() ?: return call.respond(NotFound)
        scope.launch { generateVideoPreviewJob.execute(mediaLink.id) }
        call.respond(OK)
    }

    suspend fun RoutingContext.getMatches() {
        val mediaLink = mediaLink() ?: return call.respond(NotFound)
        val matches = libraryService.refreshMetadata(mediaLink, import = false)
        call.respond(matches)
    }

    suspend fun RoutingContext.putMediaLinkMatch() {
        val mediaLink = mediaLink() ?: return call.respond(NotFound)
        val body = call.receive<JsonObject>()
        val remoteId = body["remoteId"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return call.respond(UnprocessableEntity)
        libraryService.matchMediaLink(mediaLink, remoteId)
        call.respond(OK)
    }

    suspend fun RoutingContext.deleteMediaLink() {
        val mediaLink = mediaLink() ?: return call.respond(NotFound)
        if (libraryService.removeMediaLink(mediaLink)) {
            call.respond(OK)
        } else {
            call.respond(NotFound)
        }
    }

    suspend fun RoutingContext.getAnalyzeMediaLink() {
        val waitForResult = call.parameters["waitForResult"]?.toBoolean() == true
        val mediaLink = mediaLink() ?: return call.respond(UnprocessableEntity)

        val descriptors = listOf(Descriptor.VIDEO, Descriptor.AUDIO)
        val mediaLinkIds = mediaLinkDao
            .findIdsByMediaLinkIdAndDescriptors(mediaLink.id, descriptors)
            .takeIf { it.isNotEmpty() }
            ?: return call.respond(NotFound)

        if (waitForResult) {
            call.respond(libraryService.analyzeMediaFiles(mediaLinkIds, overwrite = true))
        } else {
            scope.launch {
                libraryService.analyzeMediaFiles(mediaLinkIds, overwrite = true)
            }
            call.respond(OK)
        }
    }

    suspend fun RoutingContext.getMediaLink() {
        val includeMetadata = call.parameters["includeMetadata"]?.toBoolean() == true
        val mediaLink = mediaLink() ?: return call.respond(NotFound)
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

    private suspend fun RoutingContext.mediaLink(): MediaLink? {
        val mediaLinkId = call.parameters["mediaLinkId"]
            ?.takeIf(String::isNotBlank)
            ?: return run {
                call.respond(UnprocessableEntity)
                null
            }
        return mediaLinkDao.findById(mediaLinkId)
    }
}
