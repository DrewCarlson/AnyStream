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

import anystream.data.UserSession
import anystream.models.MediaReference
import anystream.models.Movie
import anystream.models.Permissions.MANAGE_COLLECTION
import anystream.models.api.ImportMedia
import anystream.media.MediaImporter
import anystream.util.logger
import anystream.util.withAnyPermission
import drewcarlson.torrentsearch.Category
import drewcarlson.torrentsearch.TorrentSearch
import info.movito.themoviedbapi.TmdbApi
import io.ktor.application.call
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase

fun Route.addMediaRoutes(
    tmdb: TmdbApi,
    mongodb: CoroutineDatabase,
    torrentSearch: TorrentSearch,
    importer: MediaImporter,
) {
    val moviesDb = mongodb.getCollection<Movie>()
    val mediaRefsDb = mongodb.getCollection<MediaReference>()
    withAnyPermission(MANAGE_COLLECTION) {
        route("/media") {
            route("/refs") {
                get {
                    call.respond(mediaRefsDb.find().toList())
                }
                get("/{ref_id}") {
                    val refId = call.parameters["ref_id"] ?: ""
                    val ref = mediaRefsDb.findOneById(refId)
                    if (ref == null) {
                        call.respond(NotFound)
                    } else {
                        call.respond(ref)
                    }
                }
            }

            post("/import") {
                val session = call.principal<UserSession>()!!
                val import = call.receiveOrNull<ImportMedia>()
                    ?: return@post call.respond(UnprocessableEntity)
                val importAll = call.parameters["importAll"]?.toBoolean() ?: false

                call.respond(
                    if (importAll) {
                        val list = importer.importAll(session.userId, import).toList()
                        // TODO: List with different types cannot be serialized yet.
                        buildJsonArray {
                            list.forEach {
                                add(anystream.json.encodeToJsonElement(it))
                            }
                        }
                    } else {
                        importer.import(session.userId, import)
                    }
                )
            }

            post("/unmapped") {
                val session = call.principal<UserSession>()!!
                val import = call.receiveOrNull<ImportMedia>()
                    ?: return@post call.respond(UnprocessableEntity)

                call.respond(importer.findUnmappedFiles(session.userId, import))
            }

            route("/tmdb") {
                route("/{tmdb_id}") {
                    get("/sources") {
                        val tmdbId = call.parameters["tmdb_id"]?.toIntOrNull()

                        if (tmdbId == null) {
                            call.respond(NotFound)
                        } else {
                            runCatching {
                                tmdb.movies.getMovie(tmdbId, null)
                            }.onSuccess { tmdbMovie ->
                                call.respond(
                                    torrentSearch.search(tmdbMovie.title, Category.MOVIES, 100)
                                        // TODO: API or client sort+filter
                                        .sortedByDescending { it.seeds }
                                )
                            }.onFailure { e ->
                                logger.error("Error fetching movie from TMDB - tmdbId=$tmdbId", e)
                                call.respond(InternalServerError)
                            }
                        }
                    }
                }
            }

            route("/movie/{movie_id}") {
                get("/sources") {
                    val movieId = call.parameters["movie_id"] ?: ""

                    val movie = moviesDb.findOneById(movieId)
                    if (movie == null) {
                        call.respond(NotFound)
                    } else {
                        call.respond(
                            torrentSearch.search(movie.title, Category.MOVIES, 100)
                                // TODO: API or client sort+filter
                                .sortedByDescending { it.seeds }
                        )
                    }
                }
            }
        }
    }
}
