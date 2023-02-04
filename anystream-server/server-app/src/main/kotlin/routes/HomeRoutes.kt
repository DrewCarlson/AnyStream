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
import anystream.data.UserSession
import anystream.data.asMovie
import anystream.data.asTvShow
import anystream.db.model.MetadataDb
import anystream.models.*
import anystream.models.api.HomeResponse
import anystream.util.koinGet
import anystream.util.toRemoteId
import app.moviebase.tmdb.Tmdb3
import app.moviebase.tmdb.discover.DiscoverCategory
import app.moviebase.tmdb.model.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

private const val CURRENTLY_WATCHING_ITEM_LIMIT = 10
private const val POPULAR_MOVIES_REFRESH = 86_400_000L // 24 hours

fun Route.addHomeRoutes(
    tmdb: Tmdb3 = koinGet(),
    queries: MetadataDbQueries = koinGet(),
) {
    val popularMoviesFlow = callbackFlow<List<TmdbMovieDetail>> {
        val category = DiscoverCategory.Popular(TmdbMediaType.MOVIE)
        while (true) {
            val result = tmdb.discover
                .discoverByCategory(1, language = "en", category = category)
                .results
                .filterIsInstance<TmdbMovie>()
                .map {
                    tmdb.movies.getDetails(
                        it.id,
                        null,
                        listOf(
                            AppendResponse.EXTERNAL_IDS,
                            AppendResponse.CREDITS,
                            AppendResponse.RELEASES_DATES,
                            AppendResponse.IMAGES,
                            AppendResponse.MOVIE_CREDITS,
                        )
                    )
                }
            trySend(result)
            delay(POPULAR_MOVIES_REFRESH)
        }
    }.stateIn(application, SharingStarted.Eagerly, null)
    val popularTvShowsFlow = callbackFlow<List<TmdbShowDetail>> {
        val category = DiscoverCategory.Popular(TmdbMediaType.SHOW)
        while (true) {
            val result = tmdb.discover
                .discoverByCategory(1, language = "en", category = category)
                .results
                .filterIsInstance<TmdbShow>()
                .map {
                    tmdb.show.getDetails(
                        it.id,
                        null,
                        listOf(
                            AppendResponse.EXTERNAL_IDS,
                            AppendResponse.CREDITS,
                            AppendResponse.RELEASES_DATES,
                            AppendResponse.IMAGES,
                            AppendResponse.TV_CREDITS,
                        )
                    )
                }
            trySend(result)
            delay(POPULAR_MOVIES_REFRESH)
        }
    }.stateIn(application, SharingStarted.Eagerly, null)
    route("/home") {
        get {
            val session = checkNotNull(call.principal<UserSession>())

            // Currently watching
            val (playbackStates, playbackStateMovies, playbackStateTv) =
                queries.findCurrentlyWatching(session.userId, CURRENTLY_WATCHING_ITEM_LIMIT)

            // Recently Added
            val recentlyAddedMovies = queries.findRecentlyAddedMovies(20)
            val recentlyAddedTvShows = queries.findRecentlyAddedTv(20)

            // Popular movies
            val tmdbPopular = popularMoviesFlow.filterNotNull().first()
            val existingMovies = if (tmdbPopular.isNotEmpty()) {
                queries
                    .findMoviesByTmdbId(tmdbPopular.map(TmdbMovieDetail::id))
                    .toMutableList()
            } else {
                mutableListOf()
            }
            val popularMovies = tmdbPopular.map { dbMovie ->
                val existingIndex = existingMovies.indexOfFirst { it.tmdbId == dbMovie.id }
                if (existingIndex == -1) {
                    dbMovie.asMovie(dbMovie.toRemoteId())
                } else {
                    existingMovies.removeAt(existingIndex)
                }
            }
            val popularMediaLinks = queries.findMediaLinksByMetadataIds(popularMovies.map(Movie::gid))
            val popularMoviesMap = popularMovies.associateWith { m ->
                popularMediaLinks.find { it.metadataId == m.id }
            }

            val tmdbPopularShows = popularTvShowsFlow.filterNotNull().first()
            val existingShows = queries
                .findTvShowsByTmdbId(tmdbPopularShows.map(TmdbShowDetail::id))
                .toMutableList()
            val popularTvShows = tmdbPopularShows
                .map { series ->
                    val existingIndex = existingShows.indexOfFirst { it.tmdbId == series.id }
                    if (existingIndex == -1) {
                        series.asTvShow(series.toRemoteId()).toTvShowModel()
                    } else {
                        existingShows.removeAt(existingIndex)
                    }
                }

            val tvSeasonIds = playbackStateTv.values.map { (episode, _) -> episode.seasonId }.distinct()
            val tvSeasons = queries.findTvSeasonsByIds(tvSeasonIds).map(MetadataDb::toTvSeasonModel)

            call.respond(
                HomeResponse(
                    playbackStates = playbackStates,
                    currentlyWatchingMovies = playbackStateMovies,
                    currentlyWatchingTv = playbackStateTv,
                    recentlyAdded = recentlyAddedMovies,
                    popularMovies = popularMoviesMap,
                    popularTvShows = popularTvShows,
                    recentlyAddedTv = recentlyAddedTvShows,
                    tvSeasons = tvSeasons,
                )
            )
        }
    }
}
