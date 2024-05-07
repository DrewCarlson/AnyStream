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
import anystream.models.api.AddLibraryFolderRequest
import anystream.models.api.AddLibraryFolderResponse
import anystream.util.koinGet
import anystream.util.logger
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.addLibraryViewRoutes(
    libraryService: LibraryService = koinGet()
) {
    route("/library") {
        get {
            call.respond(libraryService.getLibraries())
        }

        route("/{libraryId}") {
        }
    }
}

fun Route.addLibraryModifyRoutes(
    libraryService: LibraryService = koinGet()
) {
    route("/library") {

        route("/{libraryId}") {
            put {
                val libraryId = call.parameters["libraryId"]
                    ?: return@put call.respond(UnprocessableEntity)
                val request = try {
                    call.receive<AddLibraryFolderRequest>()
                } catch (e: ContentTransformationException) {
                    logger.error("Failed to parse request body", e)
                    return@put call.respond(UnprocessableEntity)
                }
                when (val result = libraryService.addLibraryFolder(libraryId, request.path, request.mediaKind)) {
                    is AddLibraryFolderResult.Success -> {
                        /*application.launch(Dispatchers.IO) {
                            logger.error("Started")
                            val r = measureTimedValue {
                                libraryService.scan(Path(result.directory.filePath))
                            }
                            logger.error("Ended ${r.duration}: \n${r.value}")
                            //libraryManager.refreshMetadata(result.mediaLink, true)
                        }*/

                        AddLibraryFolderResponse.Success(
                            libraryGid = result.library.id,
                            mediaKind = result.library.mediaKind,
                            libraryPath = result.directory.filePath
                        )
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
            }
        }
    }
}