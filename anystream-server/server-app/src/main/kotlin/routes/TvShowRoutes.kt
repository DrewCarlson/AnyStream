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

import anystream.data.MetadataDbQueries
import anystream.models.*
import anystream.models.api.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.drewcarlson.ktor.permissions.withAnyPermission
import org.jdbi.v3.core.JdbiException

fun Route.addTvShowRoutes(
    queries: MetadataDbQueries,
) {
    route("/tv") {
        get {
            val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: false
            call.respond(queries.findShows(includeLinks = includeLinks))
        }

        route("/{show_id}") {
            get {
                val showId = call.parameters["show_id"]
                    ?.takeUnless(String::isNullOrBlank)
                    ?: return@get call.respond(NotFound)
                val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: false
                val response = queries.findShowById(showId, includeLinks = includeLinks)
                    ?: return@get call.respond(NotFound)
                call.respond(response)
            }

            get("/episodes") {
                val showId = call.parameters["show_id"]
                val seasonNumber = call.parameters["season_number"]?.toIntOrNull()
                if (showId.isNullOrBlank()) return@get call.respond(NotFound)

                queries.findShowById(showId, false) ?: return@get call.respond(NotFound)

                val episodes = queries.findEpisodesByShow(showId, seasonNumber = seasonNumber)
                val episodeIds = episodes.map(Episode::gid)
                val mediaLinks = queries.findMediaLinksByMetadataIds(episodeIds)

                call.respond(
                    EpisodesResponse(
                        episodes = episodes,
                        mediaLinks = mediaLinks.filter { !it.metadataGid.isNullOrBlank() }
                            .associateBy { it.metadataGid!! }
                    )
                )
            }

            withAnyPermission(Permission.ManageCollection) {
                delete {
                    val result = call.parameters["show_id"]?.let { showId ->
                        try {
                            queries.deleteTvShow(showId)
                            queries.deleteLinksByRootContentId(showId)
                            OK
                        } catch (e: JdbiException) {
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
            val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: true
            val response = queries.findEpisodeById(episodeId, includeLinks = includeLinks)
                ?: return@get call.respond(NotFound)
            call.respond(response)
        }

        get("/season/{season_id}") {
            val seasonId = call.parameters["season_id"]
            if (seasonId.isNullOrBlank()) return@get call.respond(NotFound)
            val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: true
            val response = queries.findSeasonById(seasonId, includeLinks = includeLinks)
                ?: return@get call.respond(NotFound)

            call.respond(response)
        }
    }
}
