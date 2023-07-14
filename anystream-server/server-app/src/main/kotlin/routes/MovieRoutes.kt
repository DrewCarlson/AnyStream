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
import anystream.models.Permission
import anystream.util.koinGet
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.drewcarlson.ktor.permissions.withAnyPermission

fun Route.addMovieRoutes(
    queries: MetadataDbQueries = koinGet(),
) {
    route("/movies") {
        get {
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 0
            val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: true
            call.respond(
                queries.findMovies(
                    includeLinks = includeLinks,
                    offset = offset,
                    limit = limit,
                )
            )
        }

        route("/{movie_id}") {
            get {
                val movieId = call.parameters["movie_id"]
                    ?.takeUnless(String::isNullOrBlank)
                    ?: return@get call.respond(NotFound)
                val response = queries.findMovieById(movieId)
                    ?: return@get call.respond(NotFound)

                call.respond(response)
            }

            get("/links") {
                val movieId = call.parameters["movie_id"]
                    ?.takeUnless(String::isNullOrBlank)
                    ?: return@get call.respond(NotFound)

                call.respond(queries.findMediaLinksByMetadataId(movieId))
            }

            withAnyPermission(Permission.ManageCollection) {
                delete {
                    val movieId = call.parameters["movie_id"]
                        ?.takeUnless(String::isNullOrBlank)
                        ?: return@delete call.respond(NotFound)
                    val deleteLinks = call.parameters["deleteLinks"]?.toBoolean() ?: true

                    if (deleteLinks) {
                        queries.deleteLinksByContentId(movieId)
                    }

                    call.respond(if (queries.deleteMovie(movieId)) OK else NotFound)
                }
            }
        }
    }
}
