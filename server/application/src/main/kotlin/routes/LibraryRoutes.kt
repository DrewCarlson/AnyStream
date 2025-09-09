/**
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
import anystream.media.AddLibraryFolderResult
import anystream.media.LibraryService
import anystream.models.MediaKind
import anystream.models.Permission
import anystream.models.api.AddLibraryFolderRequest
import anystream.models.api.AddLibraryFolderResponse
import anystream.models.api.MediaScanResult
import anystream.models.backend.MediaScannerMessage
import anystream.util.koinGet
import anystream.util.logger
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.drewcarlson.ktor.permissions.withPermission

fun Route.addLibraryViewRoutes(
    libraryService: LibraryService = koinGet(),
    queries: MetadataDbQueries = koinGet(),
) {
    route("/library") {
        get {
            call.respond(libraryService.getLibraries())
        }

        route("/{libraryId}") {
            get {
                val libraryId = call.parameters["libraryId"] ?: return@get call.respond(NotFound)
                val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: false
                val library = libraryService.getLibrary(libraryId) ?: return@get call.respond(NotFound)

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

                    MediaKind.TV -> call.respond(queries.findShows(includeLinks = includeLinks))
                    MediaKind.MUSIC -> TODO()
                    else -> call.respond(NotFound)
                }
            }

            withPermission(Permission.ManageCollection) {
                route("/directories") {
                    get {
                        val libraryId = call.parameters["libraryId"] ?: return@get call.respond(NotFound)
                        call.respond(libraryService.getLibraryRootDirectories(libraryId))
                    }
                }
            }
        }
    }
}

fun Route.addLibraryModifyRoutes(
    libraryService: LibraryService = koinGet()
) {
    route("/library") {

        route("/directory/{directoryId}") {
            get("/scan") {

                val directoryId = call.parameters["directoryId"]
                    ?: return@get call.respond(UnprocessableEntity)
                val directory = libraryService.getDirectory(directoryId)
                    ?: return@get call.respond(NotFound)

                application.launch {
                    when (val result = libraryService.scan(directory)) {
                        is MediaScanResult.Success -> {
                            // todo: New media links under existing directories will not get a metadata refresh
                            result.directories.addedIds.forEach { id ->
                                libraryService.refreshMetadata(id)
                            }
                        }

                        else -> Unit // TODO: Handle errors
                    }
                }

                call.respond(OK)
            }
            delete {
                val directoryId = call.parameters["directoryId"] ?: return@delete call.respond(UnprocessableEntity)

                val result = if (libraryService.removeDirectory(directoryId)) OK else NotFound
                call.respond(result)
            }
        }

        route("/{libraryId}") {
            put {
                val libraryId = call.parameters["libraryId"]
                    ?: return@put call.respond(UnprocessableEntity)
                val request = try {
                    call.receive<AddLibraryFolderRequest>()
                } catch (e: ContentTransformationException) {
                    logger.error("Failed to parse request body", e)
                    return@put call.respond(AddLibraryFolderResponse.RequestError(e.stackTraceToString()))
                }

                val result = libraryService.addLibraryFolder(libraryId, request.path)
                if (result is AddLibraryFolderResult.Success) {
                    val (_, directory) = result

                    application.launch(Dispatchers.Default) {
                        var scanning = true
                        libraryService.mediaScannerMessages
                            .takeWhile { scanning }
                            .filterIsInstance<MediaScannerMessage.ScanDirectoryCompleted>()
                            .filter { it.directory.id == directory.id }
                            .onEach { message ->
                                val child = message.child
                                if (child == null) {
                                    scanning = false
                                } else {
                                    libraryService.refreshMetadata(child)
                                }
                            }
                            .launchIn(this)
                        libraryService.scan(directory)
                    }
                }

                call.respond(result)
            }

            get("/scan") {
                val libraryId = call.parameters["libraryId"]
                    ?: return@get call.respond(UnprocessableEntity)
                val directories = libraryService.getLibraryDirectories(libraryId)

                application.launch {
                    directories.forEach { directory ->
                        libraryService.scan(directory)
                    }
                }

                call.respond(OK)
            }
        }
    }
}