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

import anystream.data.UserSession
import anystream.data.asApiResponse
import anystream.models.*
import anystream.models.api.HomeResponse
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.model.MovieDb
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.response.*
import io.ktor.routing.*
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase


fun Route.addHomeRoutes(tmdb: TmdbApi, mongodb: CoroutineDatabase) {
    val playbackStatesDb = mongodb.getCollection<PlaybackState>()
    val moviesDb = mongodb.getCollection<Movie>()
    val tvShowDb = mongodb.getCollection<TvShow>()
    val episodeDb = mongodb.getCollection<Episode>()
    val mediaRefsDb = mongodb.getCollection<MediaReference>()
    route("/home") {
        get {
            val session = call.principal<UserSession>()!!

            // Currently watching
            val playbackStates = playbackStatesDb
                .find(PlaybackState::userId eq session.userId)
                .sort(descending(PlaybackState::updatedAt))
                .limit(10)
                .toList()

            val playbackStateMovies = moviesDb
                .find(Movie::id `in` playbackStates.map(PlaybackState::mediaId))
                .toList()

            val playbackStateItems = playbackStates.associateBy { state ->
                playbackStateMovies.first { it.id == state.mediaId }
            }

            // Recently Added Movies
            val recentlyAddedMovies = moviesDb
                .find()
                .sort(descending(Movie::added))
                .limit(20)
                .toList()
            val recentlyAddedRefs = mediaRefsDb
                .find(MediaReference::contentId `in` recentlyAddedMovies.map(Movie::id))
                .toList()
            val recentlyAdded = recentlyAddedMovies.associateWith { movie ->
                recentlyAddedRefs.find { it.contentId == movie.id }
            }

            val tvShows = tvShowDb
                .find()
                .sort(descending(TvShow::added))
                .limit(20)
                .toList()

            // Popular movies
            val tmdbPopular = tmdb.movies.getPopularMovies("en", 1)
            val ids = tmdbPopular.map(MovieDb::getId)
            val existingIds = moviesDb
                .find(Movie::tmdbId `in` ids)
                .toList()
                .map(Movie::tmdbId)
            val popularMovies = tmdbPopular.asApiResponse(existingIds).items
            val localPopularMovies = moviesDb
                .find(Movie::tmdbId `in` existingIds)
                .toList()
            val popularMediaRefs = mediaRefsDb
                .find(MediaReference::contentId `in` localPopularMovies.map(Movie::id))
                .toList()
            val popularMoviesMap = popularMovies.associateWith { m ->
                val contentId = localPopularMovies.find { it.tmdbId == m.tmdbId }?.id
                if (contentId == null) {
                    null
                } else {
                    popularMediaRefs.find { it.contentId == contentId }
                }
            }

            call.respond(
                HomeResponse(
                    currentlyWatching = playbackStateItems,
                    recentlyAdded = recentlyAdded,
                    popularMovies = popularMoviesMap,
                    recentlyAddedTv = tvShows
                )
            )
        }
    }
}
