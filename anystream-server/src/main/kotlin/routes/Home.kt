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
import anystream.data.UserSession
import anystream.data.asMovie
import anystream.data.asTvShow
import anystream.models.*
import anystream.models.api.HomeResponse
import anystream.util.toRemoteId
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.model.MovieDb
import info.movito.themoviedbapi.model.tv.TvSeries
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

private const val CURRENTLY_WATCHING_ITEM_LIMIT = 10
private const val POPULAR_MOVIES_REFRESH = 86_400_000L // 24 hours

fun Route.addHomeRoutes(
    tmdb: TmdbApi,
    queries: MediaDbQueries,
) {
    val popularMoviesFlow = flow {
        while (true) {
            emit(tmdb.movies.getPopularMovies("en", 1))
            delay(POPULAR_MOVIES_REFRESH)
        }
    }.stateIn(application, SharingStarted.Eagerly, null)
    val popularTvShowsFlow = flow {
        while (true) {
            emit(tmdb.tvSeries.getPopular("en", 1))
            delay(POPULAR_MOVIES_REFRESH)
        }
    }.stateIn(application, SharingStarted.Eagerly, null)
    route("/home") {
        get {
            val session = call.principal<UserSession>()!!

            // Currently watching
            val (playbackStates, playbackStateMovies, playbackStateTv) =
                queries.findCurrentlyWatching(session.userId, CURRENTLY_WATCHING_ITEM_LIMIT)

            // Recently Added
            val recentlyAddedMovies = queries.findRecentlyAddedMovies(20)
            val recentlyAddedTvShows = queries.findRecentlyAddedTv(20)

            // Popular movies
            val tmdbPopular = popularMoviesFlow.filterNotNull().first()
            val existingMovies = queries
                .findMoviesByTmdbId(tmdbPopular.map(MovieDb::getId))
                .toMutableList()
            val popularMovies = tmdbPopular.results
                .map { dbMovie ->
                    val existingIndex = existingMovies.indexOfFirst { it.tmdbId == dbMovie.id }
                    if (existingIndex == -1) {
                        dbMovie.asMovie(dbMovie.toRemoteId())
                    } else {
                        existingMovies.removeAt(existingIndex)
                    }
                }
            val popularMediaRefs = queries.findMediaRefsByContentIds(popularMovies.map(Movie::id))
            val popularMoviesMap = popularMovies.associateWith { m ->
                popularMediaRefs.find { it.contentId == m.id }
            }

            val tmdbPopularShows = popularTvShowsFlow.filterNotNull().first()
            val existingShows = queries
                .findTvShowsByTmdbId(tmdbPopularShows.map(TvSeries::getId))
                .toMutableList()
            val popularTvShows = tmdbPopularShows
                .map { series ->
                    val existingIndex = existingShows.indexOfFirst { it.tmdbId == series.id }
                    if (existingIndex == -1) {
                        series.asTvShow(emptyList(), series.toRemoteId())
                    } else {
                        existingShows.removeAt(existingIndex)
                    }
                }

            call.respond(
                HomeResponse(
                    playbackStates = playbackStates,
                    currentlyWatchingMovies = playbackStateMovies,
                    currentlyWatchingTv = playbackStateTv,
                    recentlyAdded = recentlyAddedMovies,
                    popularMovies = popularMoviesMap,
                    popularTvShows = popularTvShows,
                    recentlyAddedTv = recentlyAddedTvShows,
                )
            )
        }
    }
}
