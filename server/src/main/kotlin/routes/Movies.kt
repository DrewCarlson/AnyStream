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
import anystream.data.asApiResponse
import anystream.data.asMovie
import anystream.data.asPartialMovie
import anystream.models.MediaReference
import anystream.models.Movie
import anystream.models.Permissions.GLOBAL
import anystream.models.Permissions.MANAGE_COLLECTION
import anystream.models.api.ImportMedia
import anystream.models.api.MoviesResponse
import anystream.models.api.TmdbMoviesResponse
import anystream.media.MediaImporter
import anystream.models.api.MovieResponse
import anystream.util.logger
import anystream.util.withAnyPermission
import drewcarlson.torrentsearch.Category
import drewcarlson.torrentsearch.TorrentSearch
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.TmdbMovies.MovieMethod
import info.movito.themoviedbapi.model.MovieDb
import io.ktor.application.call
import io.ktor.application.log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase

fun Route.addMovieRoutes(
    tmdb: TmdbApi,
    mongodb: CoroutineDatabase,
) {
    val moviesDb = mongodb.getCollection<Movie>()
    val mediaRefsDb = mongodb.getCollection<MediaReference>()
    route("/movies") {
        get {
            call.respond(
                MoviesResponse(
                    movies = moviesDb.find().toList(),
                    mediaReferences = mediaRefsDb.find().toList()
                )
            )
        }

        route("/tmdb") {
            get("/popular") {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                runCatching {
                    tmdb.movies.getPopularMovies("en", page)
                }.onSuccess { tmdbMovies ->
                    val ids = tmdbMovies.map(MovieDb::getId)
                    val existingIds = moviesDb
                        .find(Movie::tmdbId `in` ids)
                        .toList()
                        .map(Movie::tmdbId)

                    call.respond(tmdbMovies.asApiResponse(existingIds))
                }.onFailure { e ->
                    // TODO: Decompose this exception and retry where possible
                    logger.error("Error fetching popular movies from TMDB - page=$page", e)
                    call.respond(InternalServerError)
                }
            }

            get("/search") {
                val query = call.request.queryParameters["query"]
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

                if (query.isNullOrBlank()) {
                    call.respond(TmdbMoviesResponse())
                } else {
                    runCatching {
                        tmdb.search.searchMovie(
                            query.encodeURLQueryComponent(),
                            0,
                            null,
                            false,
                            page
                        )
                    }.onSuccess { tmdbMovies ->
                        val ids = tmdbMovies.map(MovieDb::getId)
                        val existingIds = moviesDb
                            .find(Movie::tmdbId `in` ids)
                            .toList()
                            .map(Movie::tmdbId)
                        call.respond(tmdbMovies.asApiResponse(existingIds))
                    }.onFailure { e ->
                        // TODO: Decompose this exception and retry where possible
                        logger.error("Error searching TMDB - page=$page, query='$query'", e)
                        call.respond(InternalServerError)
                    }
                }
            }

            route("/{tmdb_id}") {
                get {
                    val tmdbId = call.parameters["tmdb_id"]?.toIntOrNull()

                    if (tmdbId == null) {
                        call.respond(NotFound)
                    } else {
                        runCatching {
                            tmdb.movies.getMovie(
                                tmdbId,
                                null,
                                MovieMethod.keywords,
                                MovieMethod.images,
                                MovieMethod.alternative_titles
                            )
                        }.onSuccess { tmdbMovie ->
                            call.respond(tmdbMovie.asPartialMovie())
                        }.onFailure { e ->
                            // TODO: Decompose this exception and retry where possible
                            logger.error("Error fetching movie from TMDB - tmdb=$tmdbId", e)
                            call.respond(InternalServerError)
                        }
                    }
                }

                withAnyPermission(GLOBAL, MANAGE_COLLECTION) {
                    get("/add") {
                        val session = call.principal<UserSession>()!!
                        val tmdbId = call.parameters["tmdb_id"]?.toIntOrNull()

                        when {
                            tmdbId == null -> {
                                call.respond(NotFound)
                            }
                            moviesDb.findOne(Movie::tmdbId eq tmdbId) != null -> {
                                call.respond(HttpStatusCode.Conflict)
                            }
                            else -> {
                                runCatching {
                                    tmdb.movies.getMovie(
                                        tmdbId,
                                        null,
                                        MovieMethod.images,
                                        MovieMethod.release_dates,
                                        MovieMethod.alternative_titles,
                                        MovieMethod.keywords
                                    )
                                }.onSuccess { tmdbMovie ->
                                    val id = ObjectId.get().toString()
                                    moviesDb.insertOne(tmdbMovie.asMovie(id, session.userId))
                                    call.respond(OK)
                                }.onFailure { e ->
                                    logger.error(
                                        "Error fetching movie from TMDB - tmdbId=$tmdbId",
                                        e
                                    )
                                    call.respond(InternalServerError)
                                }
                            }
                        }
                    }
                }
            }
        }

        route("/{movie_id}") {
            get {
                val movieId = call.parameters["movie_id"]
                    ?.takeUnless(String::isNullOrBlank)
                val movie = movieId?.let { moviesDb.findOneById(it) }
                val mediaRefs = movieId?.let {
                    mediaRefsDb.find(MediaReference::contentId eq it)
                }?.toList()

                if (movie == null) {
                    call.respond(NotFound)
                } else {
                    call.respond(
                        MovieResponse(
                            movie = movie,
                            mediaRefs = mediaRefs
                        )
                    )
                }
            }

            get("/refs") {
                val mediaRefs = call.parameters["movie_id"]
                    ?.takeUnless(String::isNullOrBlank)
                    ?.let { mediaRefsDb.find(MediaReference::contentId eq it) }
                    ?.toList()
                if (mediaRefs == null) {
                    call.respond(NotFound)
                } else {
                    call.respond(mediaRefs)
                }
            }

            withAnyPermission(GLOBAL, MANAGE_COLLECTION) {
                delete {
                    val movieId = call.parameters["movie_id"]
                        ?.takeUnless(String::isNullOrBlank)
                        ?: return@delete call.respond(NotFound)
                    val result = moviesDb.deleteOneById(movieId)
                    if (result.deletedCount == 0L) {
                        call.respond(NotFound)
                    } else {
                        mediaRefsDb.deleteMany(MediaReference::contentId eq movieId)
                        call.respond(OK)
                    }
                }
            }
        }
    }
}

fun <T, R> Flow<T>.concurrentMap(
    scope: CoroutineScope,
    concurrencyLevel: Int,
    transform: suspend (T) -> R
): Flow<R> = this
    .map { scope.async { transform(it) } }
    .buffer(concurrencyLevel)
    .map { it.await() }
