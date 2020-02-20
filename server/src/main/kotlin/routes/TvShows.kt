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

import anystream.data.asApiResponse
import anystream.data.asCompleteTvSeries
import anystream.models.Episode
import anystream.models.MediaReference
import anystream.models.TvShow
import anystream.models.api.TmdbTvShowResponse
import anystream.util.logger
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.TmdbTV.TvMethod
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.*
import org.litote.kmongo.coroutine.CoroutineDatabase

fun Route.addTvShowRoutes(
    tmdb: TmdbApi,
    mongodb: CoroutineDatabase,
) {
    val tvShowDb = mongodb.getCollection<TvShow>()
    val episodeDb = mongodb.getCollection<Episode>()
    val mediaRefs = mongodb.getCollection<MediaReference>()
    route("/tv") {
        get {
            call.respond(tvShowDb.find().toList())
        }

        route("/tmdb") {
            get("/popular") {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                runCatching {
                    tmdb.tvSeries.getPopular("en", page)
                }.onSuccess { tmdbShows ->
                    call.respond(tmdbShows.asApiResponse())
                }.onFailure { e ->
                    // TODO: Decompose this exception and retry where possible
                    logger.error("Error fetching popular series from TMDB - page=$page", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            get("/{tmdb_id}") {
                val tmdbId = call.parameters["tmdb_id"]?.toIntOrNull()

                if (tmdbId == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    runCatching {
                        tmdb.tvSeries.getSeries(
                            tmdbId,
                            null,
                            TvMethod.keywords
                        )
                    }.onSuccess { tmdbSeries ->
                        call.respond(tmdbSeries.asCompleteTvSeries())
                    }.onFailure { e ->
                        // TODO: Decompose this exception and retry where possible
                        logger.error("Error fetching series from TMDB - tmdb=$tmdbId", e)
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }

            get("/search") {
                val query = call.request.queryParameters["query"]
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

                if (query.isNullOrBlank()) {
                    call.respond(TmdbTvShowResponse())
                } else {
                    runCatching {
                        tmdb.search.searchTv(query, null, page)
                    }.onSuccess { tmdbShows ->
                        call.respond(tmdbShows.asApiResponse())
                    }.onFailure { e ->
                        // TODO: Decompose this exception and retry where possible
                        logger.error("Error searching TMDB - page=$page, query='$query'", e)
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }
        }

        get("/{show_id}") {
            val showId = call.parameters["show_id"] ?: ""
        }
    }
}
