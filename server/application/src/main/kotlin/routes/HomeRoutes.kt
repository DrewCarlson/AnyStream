/*
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
import anystream.data.UserSession
import anystream.di.ServerScope
import anystream.models.*
import anystream.models.api.CurrentlyWatching
import anystream.models.api.HomeResponse
import anystream.models.api.Popular
import anystream.models.api.RecentlyAdded
import anystream.models.toTvSeasonModel
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val CURRENTLY_WATCHING_ITEM_LIMIT = 10

@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<RoutingController>(),
)
@Inject
class HomeRoutes(
    private val queries: MetadataDbQueries,
) : RoutingController {
    override fun init(parent: Route) {
        parent.route("/home") {
            authenticate {
                get { getHome() }
                get("watching") { getWatching() }
                get("popular") { getPopular() }
            }
        }
    }

    suspend fun RoutingContext.getHome() {
        val session = checkNotNull(call.principal<UserSession>())

        // Currently watching
        val (playbackStates, playbackStateMovies, playbackStateTv) =
            queries.findCurrentlyWatching(session.userId, CURRENTLY_WATCHING_ITEM_LIMIT)

        val tvSeasonIds = playbackStateTv.values.map { (episode, _) -> episode.seasonId }.distinct()
        val tvSeasons = queries.findTvSeasonsByIds(tvSeasonIds).map(Metadata::toTvSeasonModel)

        // Recently Added
        val recentlyAddedMovies = queries.findRecentlyAddedMovies(20)
        val recentlyAddedTvShows = queries.findRecentlyAddedTv(20)

        /*// Popular movies
        val (popularMoviesMap, popularTvShows) = loadPopularMovies(
            queries,
            popularMoviesFlow,
            popularTvShowsFlow,
        )*/

        call.respond(
            HomeResponse(
                currentlyWatching = CurrentlyWatching(
                    playbackStates = playbackStates,
                    tvShows = playbackStateTv,
                    movies = playbackStateMovies,
                    tvSeasons = tvSeasons,
                ),
                recentlyAdded = RecentlyAdded(
                    movies = recentlyAddedMovies,
                    tvShows = recentlyAddedTvShows,
                ),
                popular = Popular(
                    movies = emptyMap(), // popularMoviesMap,
                    tvShows = emptyList(), // popularTvShows,
                ),
            ),
        )
    }

    suspend fun RoutingContext.getWatching() {
        val session = checkNotNull(call.principal<UserSession>())
        val (playbackStates, playbackStateMovies, playbackStateTv) =
            queries.findCurrentlyWatching(session.userId, CURRENTLY_WATCHING_ITEM_LIMIT)
        val tvSeasonIds = playbackStateTv.values.map { (episode, _) -> episode.seasonId }.distinct()
        val tvSeasons = queries.findTvSeasonsByIds(tvSeasonIds).map(Metadata::toTvSeasonModel)
        call.respond(
            CurrentlyWatching(
                playbackStates,
                playbackStateMovies,
                playbackStateTv,
                tvSeasons,
            ),
        )
    }

    suspend fun RoutingContext.getPopular() {
        call.respond(Popular(emptyMap(), emptyList()))
    }
}
