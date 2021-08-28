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

import anystream.data.MediaDbQueries
import anystream.data.asApiResponse
import anystream.data.asCompleteTvSeries
import anystream.models.*
import anystream.models.Permissions.MANAGE_COLLECTION
import anystream.models.api.*
import anystream.util.logger
import anystream.util.withAnyPermission
import com.mongodb.MongoException
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.TmdbTV.TvMethod
import io.ktor.application.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase

fun Route.addTvShowRoutes(
    tmdb: TmdbApi,
    mongodb: CoroutineDatabase,
    queries: MediaDbQueries,
) {
    val tvShowDb = mongodb.getCollection<TvShow>()
    val episodeDb = mongodb.getCollection<Episode>()
    val mediaRefsDb = mongodb.getCollection<MediaReference>()
    route("/tv") {
        get {
            val tvShows = tvShowDb.find().toList()
            call.respond(
                TvShowsResponse(
                    tvShows = tvShows,
                    mediaRefs = emptyList(),
                )
            )
        }

        route("/tmdb") {
            get("/popular") {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                try {
                    val tmdbShows = tmdb.tvSeries.getPopular("en", page)
                    call.respond(tmdbShows.asApiResponse(emptyList()))
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
                        call.respond(shows.asApiResponse(emptyList()))
                    } catch (e: Throwable) {
                        // TODO: Decompose this exception and retry where possible
                        logger.error("Error searching TMDB - page=$page, query='$query'", e)
                        call.respond(InternalServerError)
                    }
                }
            }
        }

        route("/{show_id}") {
            get {
                val showId = call.parameters["show_id"]
                    ?.takeUnless(String::isNullOrBlank)
                    ?: return@get call.respond(NotFound)
                val response = queries.findShowAndMediaRefs(showId)
                    ?: return@get call.respond(NotFound)
                call.respond(response)
            }

            get("/episodes") {
                val showId = call.parameters["show_id"]
                val seasonNumber = call.parameters["season_number"]?.toIntOrNull()
                if (showId.isNullOrBlank()) return@get call.respond(NotFound)

                tvShowDb.findOneById(showId) ?: return@get call.respond(NotFound)

                val episodes = episodeDb
                    .find(
                        Episode::showId eq showId,
                        Episode::seasonNumber eq seasonNumber,
                    )
                    .toList()
                val episodeIds = episodes.map(Episode::id)
                val mediaRefs = mediaRefsDb
                    .find(MediaReference::contentId `in` episodeIds)
                    .toList()

                call.respond(
                    EpisodesResponse(
                        episodes = if (seasonNumber == null) {
                            episodes
                        } else {
                            episodes.filter { it.seasonNumber == seasonNumber }
                        },
                        mediaRefs = mediaRefs.associateBy(MediaReference::contentId)
                    )
                )
            }

            withAnyPermission(MANAGE_COLLECTION) {
                delete {
                    val result = call.parameters["show_id"]?.let { showId ->
                        try {
                            tvShowDb.deleteOneById(showId)
                            episodeDb.deleteMany(Episode::showId eq showId)
                            mediaRefsDb.deleteMany(MediaReference::rootContentId eq showId)
                            OK
                        } catch (e: MongoException) {
                            InternalServerError
                        }
                    }
                    call.respond(result ?: NotFound)
                }
            }
        }

        get("/episode/{episode_id}") {
            val episodeId = call.parameters["episode_id"]
            if (episodeId.isNullOrBlank()) return@get call.respond(NotFound)
            val response = queries.findEpisodeAndMediaRefs(episodeId)
                ?: return@get call.respond(NotFound)
            call.respond(response)
        }

        get("/season/{season_id}") {
            val seasonId = call.parameters["season_id"]
            if (seasonId.isNullOrBlank()) return@get call.respond(NotFound)
            val response = queries.findSeasonAndMediaRefs(seasonId)
                ?: return@get call.respond(NotFound)

            call.respond(response)
        }
    }
}
