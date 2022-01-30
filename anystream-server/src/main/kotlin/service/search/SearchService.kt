/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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
package anystream.service.search

import anystream.db.MediaDao
import anystream.db.MediaReferencesDao
import anystream.db.SearchableContentDao
import anystream.db.model.MediaDb
import anystream.db.model.MediaDb.Type
import anystream.db.model.MediaReferenceDb
import anystream.models.Episode
import anystream.models.MediaReference
import anystream.models.Movie
import anystream.models.TvShow
import anystream.models.api.SearchResponse
import org.jdbi.v3.core.JdbiException
import org.slf4j.Logger

class SearchService(
    private val logger: Logger,
    private val searchableContentDao: SearchableContentDao,
    private val mediaDao: MediaDao,
    private val mediaReferencesDao: MediaReferencesDao,
) {

    fun search(inputQuery: String, limit: Int): SearchResponse {
        val query = "\"${inputQuery.trim()}*\"".trim()
        return try {
            val movieIds = searchableContentDao.search(query, Type.MOVIE, limit)
            val tvShowIds = searchableContentDao.search(query, Type.TV_SHOW, limit)
            val episodeIds = searchableContentDao.search(query, Type.TV_EPISODE, limit)
            val movies = if (movieIds.isNotEmpty()) {
                mediaDao.findAllByGidsAndType(movieIds, Type.MOVIE).map(MediaDb::toMovieModel)
            } else emptyList()
            val tvShows = if (tvShowIds.isNotEmpty()) {
                mediaDao.findAllByGidsAndType(tvShowIds, Type.TV_SHOW).map { showRecord ->
                    SearchResponse.TvShowResult(
                        tvShow = showRecord.toTvShowModel(),
                        seasonCount = mediaDao.countSeasonsForTvShow(showRecord.gid)
                    )
                }
            } else emptyList()
            val episodes = if (episodeIds.isNotEmpty()) {
                val episodes = mediaDao.findAllByGidsAndType(episodeIds, Type.TV_EPISODE)
                    .map(MediaDb::toTvEpisodeModel)
                val episodeShowIds = episodes.map(Episode::showId).distinct()
                val episodeShows = mediaDao.findAllByGidsAndType(episodeShowIds, Type.TV_SHOW)
                    .map(MediaDb::toTvShowModel)
                    .associateBy(TvShow::id)
                episodes.map { episode ->
                    SearchResponse.EpisodeResult(
                        episode = episode,
                        tvShow = episodeShows.getValue(episode.showId)
                    )
                }
            } else emptyList()

            val searchIds = movies.map(Movie::id) + episodes.map { it.episode.id }
            val mediaRefs = if (searchIds.isNotEmpty()) {
                mediaReferencesDao.findByContentGids(searchIds)
                    .map(MediaReferenceDb::toMediaRefModel)
                    .associateBy(MediaReference::contentId)
            } else emptyMap()

            SearchResponse(
                movies = movies,
                tvShows = tvShows,
                episodes = episodes,
                mediaReferences = mediaRefs,
            )
        } catch (e: JdbiException) {
            logger.error("Search query failed", e)
            SearchResponse()
        }
    }
}
