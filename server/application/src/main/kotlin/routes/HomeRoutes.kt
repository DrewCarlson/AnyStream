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
import anystream.models.api.CurrentlyWatching
import anystream.models.api.HomeResponse
import anystream.models.api.Popular
import anystream.models.api.RecentlyAdded
import anystream.util.koinGet
import anystream.util.toRemoteId
import app.moviebase.tmdb.Tmdb3
import app.moviebase.tmdb.model.*
import com.ibm.icu.util.ULocale
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val CURRENTLY_WATCHING_ITEM_LIMIT = 10
private val POPULAR_MOVIES_REFRESH = 24.hours

fun Route.addHomeRoutes(
    tmdb: Tmdb3 = koinGet(),
    queries: MetadataDbQueries = koinGet(),
) {
    val locale = ULocale.getDefault()
    val language = locale.language
    val region = "$language-${locale.country}"
    val popularMoviesFlow = callbackFlow<List<TmdbMovieDetail>> {
        while (true) {
            val result = tmdb.trending
                .getTrendingMovies(TmdbTimeWindow.WEEK, page = 1, language = language, region = region)
                .results
                .map {
                    tmdb.movies.getDetails(
                        it.id,
                        language,
                        listOf(
                            AppendResponse.EXTERNAL_IDS,
                            AppendResponse.CREDITS,
                            AppendResponse.RELEASES_DATES,
                            AppendResponse.IMAGES,
                            AppendResponse.MOVIE_CREDITS,
                        ),
                    )
                }
            trySend(result)
            delay(POPULAR_MOVIES_REFRESH)
        }
    }.stateIn(application, SharingStarted.Eagerly, null)
    val popularTvShowsFlow = callbackFlow<List<TmdbShowDetail>> {
        while (true) {
            val result = tmdb.trending
                .getTrendingShows(TmdbTimeWindow.WEEK, page = 1, language = language, region = region)
                .results
                .map {
                    tmdb.show.getDetails(
                        it.id,
                        language,
                        listOf(
                            AppendResponse.EXTERNAL_IDS,
                            AppendResponse.CREDITS,
                            AppendResponse.RELEASES_DATES,
                            AppendResponse.IMAGES,
                            AppendResponse.TV_CREDITS,
                        ),
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

            val tvSeasonIds = playbackStateTv.values.map { (episode, _) -> episode.seasonId }.distinct()
            val tvSeasons = queries.findTvSeasonsByIds(tvSeasonIds).map(MetadataDb::toTvSeasonModel)

            // Recently Added
            val recentlyAddedMovies = queries.findRecentlyAddedMovies(20)
            val recentlyAddedTvShows = queries.findRecentlyAddedTv(20)

            // Popular movies
            val (popularMoviesMap, popularTvShows) = loadPopularMovies(
                queries,
                popularMoviesFlow,
                popularTvShowsFlow,
            )

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
                        movies = popularMoviesMap,
                        tvShows = popularTvShows,
                    ),
                ),
            )
        }

        get("watching") {
            val session = checkNotNull(call.principal<UserSession>())
            val (playbackStates, playbackStateMovies, playbackStateTv) =
                queries.findCurrentlyWatching(session.userId, CURRENTLY_WATCHING_ITEM_LIMIT)
            val tvSeasonIds = playbackStateTv.values.map { (episode, _) -> episode.seasonId }.distinct()
            val tvSeasons = queries.findTvSeasonsByIds(tvSeasonIds).map(MetadataDb::toTvSeasonModel)
            call.respond(CurrentlyWatching(playbackStates, playbackStateMovies, playbackStateTv, tvSeasons))
        }

        get("recent") {
            val recentlyAddedMovies = queries.findRecentlyAddedMovies(20)
            val recentlyAddedTvShows = queries.findRecentlyAddedTv(20)
            call.respond(RecentlyAdded(recentlyAddedMovies, recentlyAddedTvShows))
        }

        get("popular") {
            val (popularMoviesMap, popularTvShows) = loadPopularMovies(
                queries,
                popularMoviesFlow,
                popularTvShowsFlow,
            )
            call.respond(Popular(popularMoviesMap, popularTvShows))
        }
    }
}

private suspend fun loadPopularMovies(
    queries: MetadataDbQueries,
    popularMoviesFlow: StateFlow<List<TmdbMovieDetail>?>,
    popularTvShowsFlow: StateFlow<List<TmdbShowDetail>?>,
): Pair<Map<Movie, MediaLink?>, List<TvShow>> {
    val tmdbPopular = withTimeoutOrNull(5.seconds) {
        popularMoviesFlow.filterNotNull().first()
    } ?: return Pair(emptyMap(), emptyList())
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
            dbMovie.asMovie(-1, dbMovie.toRemoteId())
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
                series.asTvShow(-1, series.toRemoteId()).toTvShowModel()
            } else {
                existingShows.removeAt(existingIndex)
            }
        }
    return Pair(popularMoviesMap, popularTvShows)
}
