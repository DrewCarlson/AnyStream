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
import anystream.models.Permissions.GLOBAL
import anystream.models.Permissions.MANAGE_COLLECTION
import anystream.models.TvShow
import anystream.models.api.TmdbTvShowResponse
import anystream.util.logger
import anystream.util.withAnyPermission
import com.mongodb.MongoException
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.TmdbTV.TvMethod
import io.ktor.application.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq

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
                try {
                    val tmdbShows = tmdb.tvSeries.getPopular("en", page)
                    call.respond(tmdbShows.asApiResponse())
                } catch (e: Throwable) {
                    // TODO: Decompose this exception and retry where possible
                    logger.error("Error fetching popular series from TMDB - page=$page", e)
                    call.respond(InternalServerError)
                }
            }

            get("/{tmdb_id}") {
                val tmdbId = call.parameters["tmdb_id"]?.toIntOrNull()
                    ?: return@get call.respond(NotFound)

                try {
                    val tmdbSeries = tmdb.tvSeries.getSeries(
                        tmdbId,
                        null,
                        TvMethod.keywords
                    )
                    call.respond(tmdbSeries.asCompleteTvSeries())
                } catch (e: Throwable) {
                    // TODO: Decompose this exception and retry where possible
                    logger.error("Error fetching series from TMDB - tmdb=$tmdbId", e)
                    call.respond(InternalServerError)
                }
            }

            get("/search") {
                val query = call.request.queryParameters["query"]
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

                if (query.isNullOrBlank()) {
                    call.respond(TmdbTvShowResponse())
                } else {
                    try {
                        val shows = tmdb.search.searchTv(query, null, page)
                        call.respond(shows.asApiResponse())
                    } catch (e: Throwable) {
                        // TODO: Decompose this exception and retry where possible
                        logger.error("Error searching TMDB - page=$page, query='$query'", e)
                        call.respond(InternalServerError)
                    }
                }
            }
        }

        route("/{show_id}") {
            fun PipelineContext<Unit, ApplicationCall>.showId() = call.parameters["show_id"]
            get {
                showId()
                    ?.let { showId -> tvShowDb.findOneById(showId) }
                    ?.let { tvShow -> call.respond(tvShow) }
                    ?: call.respond(NotFound)
            }
            withAnyPermission(GLOBAL, MANAGE_COLLECTION) {
                delete {
                    val result = showId()?.let { showId ->
                        try {
                            tvShowDb.deleteOneById(showId)
                            episodeDb.deleteMany(Episode::showId eq showId)
                            mediaRefs.deleteMany(MediaReference::rootContentId eq showId)
                            OK
                        } catch (e: MongoException) {
                            InternalServerError
                        }
                    }
                    call.respond(result ?: NotFound)
                }
            }
        }
    }
}
