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
import anystream.metadata.MetadataManager
import anystream.models.api.*
import anystream.util.isRemoteId
import anystream.util.koinGet
import anystream.util.logger
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.addMediaManageRoutes(
    queries: MetadataDbQueries = koinGet(),
) {
    route("/media") {
        route("/{metadataGid}") {
            get("/refresh-metadata") {
                val metadataGid = call.parameters["metadataGid"] ?: ""
                val result = queries.findMediaById(metadataGid)

                if (result.hasResult()) {
                    logger.warn("No media found for $metadataGid")
                    return@get call.respond(MediaLookupResponse())
                }
            }
        }
    }
}

fun Route.addMediaViewRoutes(
    metadataManager: MetadataManager = koinGet(),
    queries: MetadataDbQueries = koinGet(),
) {
    route("/media") {
        route("/{metadataGid}") {
            get {
                val session = checkNotNull(call.principal<UserSession>())
                val metadataGid = call.parameters["metadataGid"]
                    ?: return@get call.respond(NotFound)
                val includeLinks = call.parameters["includeLinks"]?.toBoolean() ?: true
                val includePlaybackState =
                    call.parameters["includePlaybackStates"]?.toBoolean() ?: true
                val playbackStateUserId = if (includePlaybackState) session.userId else null

                return@get if (metadataGid.isRemoteId) {
                    when (val queryResult = metadataManager.findByRemoteId(metadataGid)) {
                        is QueryMetadataResult.Success -> {
                            if (queryResult.results.isEmpty()) {
                                return@get call.respond(MediaLookupResponse())
                            }
                            val response = when (val match = queryResult.results.first()) {
                                is MetadataMatch.MovieMatch -> {
                                    MediaLookupResponse(movie = MovieResponse(match.movie))
                                }

                                is MetadataMatch.TvShowMatch -> {
                                    val tvExtras = queryResult.extras?.asTvShowExtras()
                                    when {
                                        tvExtras?.episodeNumber != null -> MediaLookupResponse(
                                            episode = EpisodeResponse(match.episodes.first(), match.tvShow),
                                        )

                                        tvExtras?.seasonNumber != null -> MediaLookupResponse(
                                            season = SeasonResponse(
                                                match.tvShow,
                                                match.seasons.first(),
                                                match.episodes,
                                            ),
                                        )

                                        else -> MediaLookupResponse(
                                            tvShow = TvShowResponse(match.tvShow, match.seasons),
                                        )
                                    }
                                }
                            }
                            call.respond(response)
                        }

                        else -> call.respond(MediaLookupResponse())
                    }
                } else {
                    call.respond(
                        queries.findMediaById(
                            metadataGid,
                            includeLinks = includeLinks,
                            includePlaybackStateForUser = playbackStateUserId,
                        ),
                    )
                }
            }
        }
    }
}
