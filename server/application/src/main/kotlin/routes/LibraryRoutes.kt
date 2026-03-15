/*
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
import anystream.di.ServerScope
import anystream.media.LibraryService
import anystream.models.MediaKind
import anystream.models.Permission
import anystream.models.api.AddLibraryFolderRequest
import anystream.models.api.AddLibraryFolderResponse
import anystream.models.api.MediaScanResult
import anystream.util.logger
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.drewcarlson.ktor.permissions.withAnyPermission
import org.drewcarlson.ktor.permissions.withPermission

@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<RoutingController>(),
)
@Inject
class LibraryRoutes(
    private val libraryService: LibraryService,
    private val queries: MetadataDbQueries,
    private val scope: CoroutineScope,
) : RoutingController {
    override fun init(parent: Route) {
        parent.route("/library") {
            authenticate {
                withAnyPermission(Permission.ViewCollection) {
                    get { getLibraries() }

                    route("/{libraryId}") {
                        get { getLibrary() }

                        withPermission(Permission.ManageCollection) {
                            route("/directories") {
                                get { getDirectories() }
                            }
                        }
                    }
                }
                withAnyPermission(Permission.ManageCollection) {
                    route("/directory/{directoryId}") {
                        get("/scan") { getScanLibraryDirectory() }
                        delete { deleteLibraryDirectory() }
                    }

                    route("/{libraryId}") {
                        put { addLibraryFolder() }

                        get("/scan") { getScanLibrary() }
                    }
                }
            }
        }
    }

    suspend fun RoutingContext.getLibraries() {
        call.respond(libraryService.getLibraries())
    }

    suspend fun RoutingContext.getLibrary() {
        val libraryId = call.parameters["libraryId"] ?: return call.respond(NotFound)
        val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: false
        val library =
            libraryService.getLibrary(libraryId) ?: return call.respond(NotFound)

        when (library.mediaKind) {
            MediaKind.MOVIE -> {
                val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 0
                val response = queries.findMovies(
                    includeLinks = includeLinks,
                    offset = offset,
                    limit = limit,
                )
                call.respond(response)
            }

            MediaKind.TV -> {
                call.respond(queries.findShows(includeLinks = includeLinks))
            }

            MediaKind.MUSIC -> {
                TODO()
            }

            else -> {
                call.respond(NotFound)
            }
        }
    }

    suspend fun RoutingContext.getDirectories() {
        val libraryId =
            call.parameters["libraryId"] ?: return call.respond(NotFound)
        call.respond(libraryService.getLibraryRootDirectories(libraryId))
    }

    suspend fun RoutingContext.getScanLibraryDirectory() {
        val directoryId = call.parameters["directoryId"]
            ?: return call.respond(UnprocessableEntity)
        val directory = libraryService.getDirectory(directoryId)
            ?: return call.respond(NotFound)

        scope.launch {
            when (val result = libraryService.scan(directory)) {
                is MediaScanResult.Success -> {
                    // todo: New media links under existing directories will not get a metadata refresh
                    result.directories.addedIds.forEach { id ->
                        libraryService.refreshMetadata(id)
                    }
                }

                else -> {
                    // TODO: Handle errors
                }
            }
        }

        call.respond(OK)
    }

    suspend fun RoutingContext.deleteLibraryDirectory() {
        val directoryId = call.parameters["directoryId"]
            ?: return call.respond(UnprocessableEntity)

        val result = if (libraryService.removeDirectory(directoryId)) OK else NotFound
        call.respond(result)
    }

    suspend fun RoutingContext.addLibraryFolder() {
        val libraryId = call.parameters["libraryId"]
            ?: return call.respond(UnprocessableEntity)
        val request = try {
            call.receive<AddLibraryFolderRequest>()
        } catch (e: ContentTransformationException) {
            logger.error("Failed to parse request body", e)
            return call.respond(AddLibraryFolderResponse.RequestError(e.stackTraceToString()))
        }

        call.respond(libraryService.addLibraryFolderAndScan(libraryId, request.path))
    }

    suspend fun RoutingContext.getScanLibrary() {
        val libraryId = call.parameters["libraryId"]
            ?: return call.respond(UnprocessableEntity)
        val directories = libraryService.getLibraryDirectories(libraryId)

        scope.launch {
            directories.forEach { directory ->
                libraryService.scan(directory)
            }
        }

        call.respond(OK)
    }
}
