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
import anystream.models.*
import anystream.models.Permissions.MANAGE_COLLECTION
import anystream.models.api.*
import anystream.util.withAnyPermission
import com.mongodb.MongoException
import io.ktor.application.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import org.litote.kmongo.*

fun Route.addTvShowRoutes(
    queries: MediaDbQueries,
) {
    route("/tv") {
        get {
            val includeRefs = call.parameters["includeRefs"]?.toBoolean() ?: false
            call.respond(queries.findShows(includeRefs = includeRefs))
        }

        route("/{show_id}") {
            get {
                val showId = call.parameters["show_id"]
                    ?.takeUnless(String::isNullOrBlank)
                    ?: return@get call.respond(NotFound)
                val includeRefs = call.parameters["includeRefs"]?.toBoolean() ?: false
                val response = queries.findShowById(showId, includeRefs = includeRefs)
                    ?: return@get call.respond(NotFound)
                call.respond(response)
            }

            get("/episodes") {
                val showId = call.parameters["show_id"]
                val seasonNumber = call.parameters["season_number"]?.toIntOrNull()
                if (showId.isNullOrBlank()) return@get call.respond(NotFound)

                queries.findShowById(showId, false) ?: return@get call.respond(NotFound)

                val episodes = queries.findEpisodesByShow(showId, seasonNumber = seasonNumber)
                val episodeIds = episodes.map(Episode::id)
                val mediaRefs = queries.findMediaRefsByContentIds(episodeIds)

                call.respond(
                    EpisodesResponse(
                        episodes = episodes,
                        mediaRefs = mediaRefs.associateBy(MediaReference::contentId)
                    )
                )
            }

            withAnyPermission(MANAGE_COLLECTION) {
                delete {
                    val result = call.parameters["show_id"]?.let { showId ->
                        try {
                            queries.deleteTvShow(showId)
                            queries.deleteRefsByRootContentId(showId)
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
            val includeRefs = call.parameters["includeRefs"]?.toBoolean() ?: true
            val response = queries.findEpisodeById(episodeId, includeRefs = includeRefs)
                ?: return@get call.respond(NotFound)
            call.respond(response)
        }

        get("/season/{season_id}") {
            val seasonId = call.parameters["season_id"]
            if (seasonId.isNullOrBlank()) return@get call.respond(NotFound)
            val includeRefs = call.parameters["includeRefs"]?.toBoolean() ?: true
            val response = queries.findSeasonById(seasonId, includeRefs = includeRefs)
                ?: return@get call.respond(NotFound)

            call.respond(response)
        }
    }
}
