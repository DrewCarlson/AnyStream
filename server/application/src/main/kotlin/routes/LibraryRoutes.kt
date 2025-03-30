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

import anystream.media.AddLibraryFolderResult
import anystream.media.LibraryService
import anystream.models.MediaKind
import anystream.models.Permission
import anystream.models.api.AddLibraryFolderRequest
import anystream.models.api.AddLibraryFolderResponse
import anystream.models.api.LibraryFolderList
import anystream.models.api.MediaScanResult
import anystream.util.koinGet
import anystream.util.logger
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import org.drewcarlson.ktor.permissions.withPermission
import kotlin.io.path.Path

fun Route.addLibraryViewRoutes(
    libraryService: LibraryService = koinGet()
) {
    route("/library") {
        get {
            call.respond(libraryService.getLibraries())
        }

        withPermission(Permission.ManageCollection) {
            route("/{libraryId}") {
                route("/directories") {
                    get {
                        val libraryId = call.parameters["libraryId"] ?: return@get call.respond(UnprocessableEntity)
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

                libraryService.scan(Path(directory.filePath))

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
                val response = when (val result = libraryService.addLibraryFolder(libraryId, request.path)) {
                    is AddLibraryFolderResult.Success -> {
                        val (library, directory) = result

                        when (val scanResult = libraryService.scan(Path(directory.filePath))) {
                            is MediaScanResult.Success -> {
                                when (library.mediaKind) {
                                    MediaKind.MOVIE -> {
                                        scanResult.mediaLinks.addedIds.forEach { mediaLinkId ->
                                            libraryService.refreshMetadata(mediaLinkId, import = true)
                                        }
                                    }
                                    MediaKind.TV -> {
                                        libraryService.refresh(directory)
                                    }
                                    else -> TODO("Implement metadata refresh for ${library.mediaKind}")
                                }
                            }
                            else -> Unit // TODO: Handle errors
                        }

                        AddLibraryFolderResponse.Success(library, directory)
                    }

                    is AddLibraryFolderResult.DatabaseError ->
                        AddLibraryFolderResponse.DatabaseError(result.exception.stackTraceToString())

                    is AddLibraryFolderResult.FileError ->
                        AddLibraryFolderResponse.FileError(
                            exists = result.exists,
                            isDirectory = result.isDirectory,
                        )

                    AddLibraryFolderResult.NoLibrary, // TODO:
                    AddLibraryFolderResult.LinkAlreadyExists ->
                        AddLibraryFolderResponse.LibraryFolderExists
                }
                call.respond(response)
            }

            get("/scan") {
                val libraryId = call.parameters["libraryId"]
                    ?: return@get call.respond(UnprocessableEntity)
                val directories = libraryService.getLibraryDirectories(libraryId)

                directories.forEach { directory ->
                    libraryService.scan(Path(directory.filePath))
                }

                call.respond(OK)
            }
        }
    }
}