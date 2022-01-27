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

import anystream.db.MediaDao
import anystream.db.MediaReferencesDao
import anystream.db.SearchableContentDao
import anystream.db.model.MediaDb
import anystream.db.model.MediaReferenceDb
import anystream.models.*
import anystream.models.api.SearchResponse
import info.movito.themoviedbapi.TmdbApi
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jdbi.v3.core.Handle

private const val DEFAULT_SEARCH_RESULT_LIMIT = 3
private const val MAX_SEARCH_RESULT_LIMIT = 3
private const val QUERY = "query"
private const val LIMIT = "limit"
private const val MEDIA_KIND = "mediaKind"

fun Route.addSearchRoutes(
    searchableContentDao: SearchableContentDao,
    mediaDao: MediaDao,
    mediaReferencesDao: MediaReferencesDao,
) {
    route("/search") {
        get {
            val query = call.parameters[QUERY]?.trim()?.plus("*") ?: return@get call.respond(SearchResponse())
            val limit = (call.parameters[LIMIT]?.toIntOrNull() ?: DEFAULT_SEARCH_RESULT_LIMIT)
                .coerceIn(1, MAX_SEARCH_RESULT_LIMIT)
            // val mediaKind = call.parameters[MEDIA_KIND]?.uppercase()?.run(MediaKind::valueOf)

            val movieIds = searchableContentDao.search(query, MediaDb.Type.MOVIE, limit)
            val tvShowIds = searchableContentDao.search(query, MediaDb.Type.TV_SHOW, limit)
            val episodeIds = searchableContentDao.search(query, MediaDb.Type.TV_EPISODE, limit)
            val movies = if (movieIds.isNotEmpty()) {
                mediaDao.findAllByGidsAndType(movieIds, MediaDb.Type.MOVIE).map(MediaDb::toMovieModel)
            } else emptyList()
            val tvShows = if (tvShowIds.isNotEmpty()) {
                mediaDao.findAllByGidsAndType(tvShowIds, MediaDb.Type.TV_SHOW).map(MediaDb::toTvShowModel)
            } else emptyList()
            val episodes = if (episodeIds.isNotEmpty()) {
                mediaDao.findAllByGidsAndType(episodeIds, MediaDb.Type.TV_EPISODE).map(MediaDb::toTvEpisodeModel)
            } else emptyList()

            val searchIds = movies.map(Movie::id) + episodes.map(Episode::id)
            val mediaRefs = if (searchIds.isNotEmpty()) {
                mediaReferencesDao.findByContentGids(searchIds)
                    .map(MediaReferenceDb::toMediaRefModel)
                    .associateBy(MediaReference::contentId)
            } else emptyMap()

            call.respond(
                SearchResponse(
                    movies = movies,
                    tvShows = tvShows,
                    episodes = episodes,
                    mediaReferences = mediaRefs,
                )
            )
        }
    }
}
