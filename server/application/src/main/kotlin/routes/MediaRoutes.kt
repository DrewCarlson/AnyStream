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
import anystream.metadata.MetadataService
import anystream.models.api.*
import anystream.util.isRemoteId
import anystream.util.koinGet
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.addMediaManageRoutes() {
    route("/media") {
        route("/{metadataId}") {
        }
    }
}

fun Route.addMediaViewRoutes(
    metadataService: MetadataService = koinGet(),
    queries: MetadataDbQueries = koinGet(),
) {
    route("/media") {
        route("/{metadataId}") {
            get {
                val session = checkNotNull(call.principal<UserSession>())
                val metadataId = call.parameters["metadataId"]
                    ?: return@get call.respond(NotFound)
                val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: true
                val includePlaybackState =
                    call.parameters["includePlaybackStates"]?.toBoolean() ?: true
                val playbackStateUserId = if (includePlaybackState) session.userId else null

                if (!metadataId.isRemoteId) {
                    val response = queries.findMediaById(
                        metadataId = metadataId,
                        includeLinks = includeLinks,
                        includePlaybackStateForUser = playbackStateUserId,
                    )
                    return@get if (response == null) {
                        call.respond(NotFound)
                    } else {
                        call.respond(response)
                    }
                }
                when (val queryResult = metadataService.findByRemoteId(metadataId)) {
                    is QueryMetadataResult.Success -> {
                        if (queryResult.results.isEmpty()) {
                            return@get call.respond(NotFound)
                        }
                        val response = when (val match = queryResult.results.first()) {
                            is MetadataMatch.MovieMatch -> MovieResponse(match.movie)
                            is MetadataMatch.TvShowMatch -> {
                                val tvExtras = queryResult.extras?.asTvShowExtras()
                                when {
                                    tvExtras?.episodeNumber != null ->
                                        EpisodeResponse(match.episodes.first(), match.tvShow)

                                    tvExtras?.seasonNumber != null ->
                                        SeasonResponse(
                                            match.tvShow,
                                            match.seasons.first(),
                                            match.episodes,
                                        )

                                    else -> TvShowResponse(match.tvShow, match.seasons)
                                }
                            }
                        }
                        call.respond(response)
                    }

                    else -> call.respond(NotFound)
                }
            }
        }
    }
}
