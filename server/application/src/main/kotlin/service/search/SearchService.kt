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
import anystream.models.*
import anystream.models.api.SearchResponse
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
            val movieIds = searchableContentDao.search(query, MediaType.MOVIE, limit)
            val tvShowIds = searchableContentDao.search(query, MediaType.TV_SHOW, limit)
            val episodeIds = searchableContentDao.search(query, MediaType.TV_EPISODE, limit)
            val movies = mediaDao.findAllByGidsAndType(movieIds, MediaType.MOVIE)
                .map(Metadata::toMovieModel)

            val tvShows = mediaDao.findAllByGidsAndType(tvShowIds, MediaType.TV_SHOW)
                .map { showRecord ->
                    SearchResponse.TvShowResult(
                        tvShow = showRecord.toTvShowModel(),
                        seasonCount = mediaDao.countSeasonsForTvShow(showRecord.id),
                    )
                }
            val episodesDb = mediaDao.findAllByGidsAndType(episodeIds, MediaType.TV_EPISODE)
                .map(Metadata::toTvEpisodeModel)
            val episodeShowIds = episodesDb.map(Episode::showId).distinct()
            val episodeShows = mediaDao.findAllByGidsAndType(episodeShowIds, MediaType.TV_SHOW)
                .map(Metadata::toTvShowModel)
                .associateBy(TvShow::id)
            val episodes = episodesDb.map { episode ->
                SearchResponse.EpisodeResult(
                    episode = episode,
                    tvShow = episodeShows.getValue(episode.showId),
                )
            }

            val searchIds = movies.map(Movie::id) + episodes.map { it.episode.id }
            val mediaLinks = mediaLinkDao.findByMetadataIds(searchIds)
                .filter { it.metadataId != null }
                .associateBy { it.metadataId!! }

            SearchResponse(
                movies = movies,
                tvShows = tvShows,
                episodes = episodes,
                mediaLink = mediaLinks,
            )
        } catch (e: Throwable) {
            logger.error("Search query failed", e)
            SearchResponse()
        }
    }
}
