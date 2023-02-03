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

import anystream.db.MediaLinkDao
import anystream.db.MetadataDao
import anystream.db.SearchableContentDao
import anystream.db.model.MediaLinkDb
import anystream.db.model.MetadataDb
import anystream.db.model.MetadataDb.Type
import anystream.models.Episode
import anystream.models.Movie
import anystream.models.TvShow
import anystream.models.api.SearchResponse
import org.jdbi.v3.core.JdbiException
import org.slf4j.LoggerFactory

class SearchService(
    private val searchableContentDao: SearchableContentDao,
    private val mediaDao: MetadataDao,
    private val mediaLinkDao: MediaLinkDao,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun search(inputQuery: String, limit: Int): SearchResponse {
        val query = "\"${inputQuery.trim()}\"*".trim()
        return try {
            val movieIds = searchableContentDao.search(query, Type.MOVIE, limit)
            val tvShowIds = searchableContentDao.search(query, Type.TV_SHOW, limit)
            val episodeIds = searchableContentDao.search(query, Type.TV_EPISODE, limit)
            val movies = if (movieIds.isNotEmpty()) {
                mediaDao.findAllByGidsAndType(movieIds, Type.MOVIE).map(MetadataDb::toMovieModel)
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
                    .map(MetadataDb::toTvEpisodeModel)
                val episodeShowIds = episodes.map(Episode::showId).distinct()
                val episodeShows = if (episodeShowIds.isNotEmpty()) {
                    mediaDao.findAllByGidsAndType(episodeShowIds, Type.TV_SHOW)
                        .map(MetadataDb::toTvShowModel)
                        .associateBy(TvShow::gid)
                } else emptyMap()
                episodes.map { episode ->
                    SearchResponse.EpisodeResult(
                        episode = episode,
                        tvShow = episodeShows.getValue(episode.showId)
                    )
                }
            } else emptyList()

            val searchIds = movies.map(Movie::gid) + episodes.map { it.episode.gid }
            val mediaLinks = if (searchIds.isNotEmpty()) {
                mediaLinkDao.findByMetadataGids(searchIds)
                    .filter { it.metadataGid != null }
                    .map(MediaLinkDb::toModel)
                    .associateBy { it.metadataGid!! }
            } else emptyMap()

            SearchResponse(
                movies = movies,
                tvShows = tvShows,
                episodes = episodes,
                mediaLink = mediaLinks,
            )
        } catch (e: JdbiException) {
            logger.error("Search query failed", e)
            SearchResponse()
        }
    }
}
