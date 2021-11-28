/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
import anystream.models.Permissions.MANAGE_COLLECTION
import org.drewcarlson.ktor.permissions.withAnyPermission
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.routing.*

fun Route.addMovieRoutes(
    queries: MediaDbQueries,
) {
    route("/movies") {
        get {
            val includeRefs = call.parameters["includeRefs"]?.toBoolean() ?: true
            call.respond(queries.findMovies(includeRefs = includeRefs))
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

            get("/refs") {
                val movieId = call.parameters["movie_id"]
                    ?.takeUnless(String::isNullOrBlank)
                    ?: return@get call.respond(NotFound)

                call.respond(queries.findMediaRefsByContentId(movieId))
            }

            withAnyPermission(MANAGE_COLLECTION) {
                delete {
                    val movieId = call.parameters["movie_id"]
                        ?.takeUnless(String::isNullOrBlank)
                        ?: return@delete call.respond(NotFound)
                    val deleteRefs = call.parameters["deleteRefs"]?.toBoolean() ?: true

                    if (deleteRefs) {
                        queries.deleteRefsByContentId(movieId)
                    }

                    call.respond(if (queries.deleteMovie(movieId)) OK else NotFound)
                }
            }
        }
    }
}
