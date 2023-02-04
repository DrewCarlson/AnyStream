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
package anystream.data

import anystream.db.*
import anystream.db.model.MediaLinkDb
import anystream.db.model.MetadataDb
import anystream.db.model.PlaybackStateDb
import anystream.models.*
import anystream.models.api.*

class MetadataDbQueries(
    val searchableContentDao: SearchableContentDao,
    val metadataDao: MetadataDao,
    val tagsDao: TagsDao,
    val mediaLinkDao: MediaLinkDao,
    val playbackStatesDao: PlaybackStatesDao,
) {

    fun findMovies(includeLinks: Boolean = false): MoviesResponse {
        val mediaRecords = metadataDao.findAllByTypeSortedByTitle(MetadataDb.Type.MOVIE)
        val mediaLinkRecords = if (includeLinks && mediaRecords.isNotEmpty()) {
            mediaLinkDao.findByMetadataGids(mediaRecords.map(MetadataDb::gid))
        } else {
            emptyList()
        }

        return MoviesResponse(
            movies = mediaRecords.map(MetadataDb::toMovieModel),
            mediaLinks = mediaLinkRecords.map(MediaLinkDb::toModel),
        )
    }

    fun findMediaById(
        mediaId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): MediaLookupResponse {
        return when (metadataDao.findTypeByGid(mediaId)) {
            MetadataDb.Type.MOVIE -> MediaLookupResponse(
                movie = findMovieById(mediaId, includeLinks, includePlaybackStateForUser)
            )
            MetadataDb.Type.TV_SHOW -> MediaLookupResponse(
                tvShow = findShowById(mediaId, includeLinks, includePlaybackStateForUser)
            )
            MetadataDb.Type.TV_EPISODE -> MediaLookupResponse(
                episode = findEpisodeById(mediaId, includeLinks, includePlaybackStateForUser)
            )
            MetadataDb.Type.TV_SEASON -> MediaLookupResponse(
                season = findSeasonById(mediaId, includeLinks, includePlaybackStateForUser)
            )
            else -> MediaLookupResponse()
        }
    }

    fun findMovieById(
        movieId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): MovieResponse? {
        val mediaRecord = metadataDao.findByGidAndType(movieId, MetadataDb.Type.MOVIE) ?: return null
        val mediaLinks = if (includeLinks) {
            mediaLinkDao.findByMetadataGid(mediaRecord.gid)
        } else {
            emptyList()
        }
        val playbackState = includePlaybackStateForUser?.let { userId ->
            playbackStatesDao.findByUserIdAndMediaGid(userId, movieId)
        }

        return MovieResponse(
            movie = mediaRecord.toMovieModel(),
            mediaLinks = mediaLinks.map(MediaLinkDb::toModel),
            playbackState = playbackState?.toStateModel(),
        )
    }

    fun findShows(includeLinks: Boolean = false): TvShowsResponse {
        val mediaRecords = metadataDao.findAllByTypeSortedByTitle(MetadataDb.Type.TV_SHOW)
        val mediaLinks = if (includeLinks && mediaRecords.isNotEmpty()) {
            val showIds = mediaRecords.map(MetadataDb::gid)
            mediaLinkDao.findByRootMetadataGids(showIds)
        } else {
            emptyList()
        }

        return TvShowsResponse(
            tvShows = mediaRecords.map(MetadataDb::toTvShowModel),
            mediaLinks = mediaLinks.map(MediaLinkDb::toModel),
        )
    }

    fun findShowById(
        showId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): TvShowResponse? {
        val show = metadataDao.findByGidAndType(showId, MetadataDb.Type.TV_SHOW) ?: return null
        val seasons = metadataDao.findAllByParentGidAndType(showId, MetadataDb.Type.TV_SEASON)
        val mediaLinks = if (includeLinks && seasons.isNotEmpty()) {
            val seasonGids = seasons.map(MetadataDb::gid)
            mediaLinkDao.findByMetadataGids(seasonGids + show.gid)
        } else {
            emptyList()
        }
        val playbackState = includePlaybackStateForUser
            ?.takeIf { mediaLinks.isNotEmpty() }
            ?.let { userId ->
                playbackStatesDao.findByUserIdAndMediaGids(userId, mediaLinks.map(MediaLinkDb::gid))
                    .maxByOrNull(PlaybackStateDb::updatedAt)
            }
        return TvShowResponse(
            tvShow = show.toTvShowModel(),
            mediaLinks = mediaLinks.map(MediaLinkDb::toModel),
            seasons = seasons.map(MetadataDb::toTvSeasonModel),
            playbackState = playbackState?.toStateModel(),
        )
    }

    fun findSeasonById(
        seasonId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): SeasonResponse? {
        val seasonRecord = metadataDao.findByGidAndType(seasonId, MetadataDb.Type.TV_SEASON) ?: return null
        val tvShowGid = checkNotNull(seasonRecord.parentGid)
        val tvShowRecord = metadataDao.findByGidAndType(tvShowGid, MetadataDb.Type.TV_SHOW) ?: return null
        val episodeRecords = metadataDao.findAllByParentGidAndType(seasonId, MetadataDb.Type.TV_EPISODE)
        val mediaLinks = if (includeLinks) {
            val searchIds = episodeRecords.map(MetadataDb::gid) + tvShowGid + seasonId
            mediaLinkDao.findByMetadataGids(searchIds)
        } else {
            emptyList()
        }
        val playbackStateRecord = includePlaybackStateForUser
            ?.takeIf { episodeRecords.isNotEmpty() }
            ?.let { userId ->
                playbackStatesDao.findByUserIdAndMediaGids(userId, episodeRecords.map(MetadataDb::gid))
                    .maxByOrNull(PlaybackStateDb::updatedAt)
            }
        return SeasonResponse(
            season = seasonRecord.toTvSeasonModel(),
            show = tvShowRecord.toTvShowModel(),
            episodes = episodeRecords.map(MetadataDb::toTvEpisodeModel),
            mediaLinks = mediaLinks.map(MediaLinkDb::toModel)
                .filter { !it.metadataGid.isNullOrBlank() }
                .associateBy { it.metadataGid!! },
            playbackState = playbackStateRecord?.toStateModel(),
        )
    }

    fun findEpisodeById(
        episodeId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): EpisodeResponse? {
        val episodeRecord = metadataDao.findByGidAndType(episodeId, MetadataDb.Type.TV_EPISODE) ?: return null
        val showRecord = metadataDao.findByGid(checkNotNull(episodeRecord.rootGid)) ?: return null
        val mediaLinks = if (includeLinks) {
            mediaLinkDao.findByMetadataGid(episodeId)
        } else {
            emptyList()
        }
        val playbackState = includePlaybackStateForUser?.let { userId ->
            playbackStatesDao.findByUserIdAndMediaGid(userId, episodeId)
        }
        return EpisodeResponse(
            episode = episodeRecord.toTvEpisodeModel(),
            show = showRecord.toTvShowModel(),
            mediaLinks = mediaLinks.map(MediaLinkDb::toModel),
            playbackState = playbackState?.toStateModel(),
        )
    }

    fun findCurrentlyWatching(userId: Int, limit: Int): CurrentlyWatchingQueryResults {
        // TODO: Limit is not correctly applied as playbackStates for different episodes
        //  of the same tv show are returned by the query and filtered later.
        //  The find query should select playbackStates with a unique root id.
        val playbackStates = playbackStatesDao.findByUserId(userId, limit)
            .map(PlaybackStateDb::toStateModel)
            .associateBy(PlaybackState::metadataGid)

        if (playbackStates.isEmpty()) {
            return CurrentlyWatchingQueryResults(emptyList(), emptyMap(), emptyMap())
        }

        val playbackMediaIds = playbackStates.keys.toList()

        val movies = metadataDao.findAllByGidsAndType(playbackMediaIds, MetadataDb.Type.MOVIE)
            .map(MetadataDb::toMovieModel)

        val episodes = metadataDao.findAllByGidsAndType(playbackMediaIds, MetadataDb.Type.TV_EPISODE)
            .map(MetadataDb::toTvEpisodeModel)
            .sortedByDescending { playbackStates.getValue(it.gid).updatedAt }
            .distinctBy(Episode::showId)

        val tvShowIds = episodes.map(Episode::showId)
        val tvShows = if (tvShowIds.isNotEmpty()) {
            metadataDao.findAllByGidsAndType(tvShowIds, MetadataDb.Type.TV_SHOW)
                .map(MetadataDb::toTvShowModel)
        } else {
            emptyList()
        }

        val filteredPlaybackStates = playbackStates.values.filter { state ->
            episodes.any { it.gid == state.metadataGid } || movies.any { it.gid == state.metadataGid }
        }

        val episodeAndShowPairs = episodes.map { episode ->
            episode to tvShows.first { it.gid == episode.showId }
        }

        return CurrentlyWatchingQueryResults(
            playbackStates = filteredPlaybackStates,
            currentlyWatchingMovies = movies.associateBy { playbackStates.getValue(it.gid).id },
            currentlyWatchingTv = episodeAndShowPairs.associateBy { (episode, _) ->
                playbackStates.getValue(episode.gid).id
            },
        )
    }

    fun findRecentlyAddedMovies(limit: Int): Map<Movie, MediaLink?> {
        val movies = metadataDao.findByType(MetadataDb.Type.MOVIE, limit).map(MetadataDb::toMovieModel)
        val mediaLink = if (movies.isNotEmpty()) {
            mediaLinkDao.findByMetadataGids(movies.map(Movie::gid))
                .map(MediaLinkDb::toModel)
        } else {
            emptyList()
        }
        return movies.associateWith { movie ->
            mediaLink.find { it.metadataGid == movie.gid }
        }
    }

    fun findRecentlyAddedTv(limit: Int): List<TvShow> {
        return metadataDao.findByType(MetadataDb.Type.TV_SHOW, limit).map(MetadataDb::toTvShowModel)
    }

    fun findMediaRefByFilePath(path: String): MediaLink? {
        return mediaLinkDao.findByFilePath(path)?.toModel()
    }

    fun findMediaLinksByMetadataId(mediaId: String): List<MediaLink> {
        return mediaLinkDao.findByMetadataGid(mediaId).map(MediaLinkDb::toModel)
    }

    fun findMediaLinksByMetadataIds(mediaIds: List<String>): List<MediaLink> {
        if (mediaIds.isEmpty()) return emptyList()
        return mediaLinkDao.findByMetadataGids(mediaIds).map(MediaLinkDb::toModel)
    }

    fun findMovieByTmdbId(tmdbId: Int): Movie? {
        return metadataDao.findByTmdbIdAndType(tmdbId, MetadataDb.Type.MOVIE)?.toMovieModel()
    }

    fun findMoviesByTmdbId(tmdbIds: List<Int>): List<Movie> {
        return metadataDao.findAllByTmdbIdsAndType(tmdbIds, MetadataDb.Type.MOVIE).map(MetadataDb::toMovieModel)
    }

    fun findTvShowByTmdbId(tmdbId: Int): TvShow? {
        return metadataDao.findByTmdbIdAndType(tmdbId, MetadataDb.Type.TV_SHOW)?.toTvShowModel()
    }

    fun findTvSeasonsByTvShowId(tvShowId: String): List<MetadataDb> {
        return metadataDao.findAllByParentGidAndType(tvShowId, MetadataDb.Type.TV_SEASON)
    }

    fun findTvShowsByTmdbId(tmdbIds: List<Int>): List<TvShow> {
        if (tmdbIds.isEmpty()) return emptyList()
        return metadataDao.findAllByTmdbIdsAndType(tmdbIds, MetadataDb.Type.TV_SHOW).map(MetadataDb::toTvShowModel)
    }

    fun findEpisodesByShow(showId: String, seasonNumber: Int? = null): List<Episode> {
        return if (seasonNumber == null) {
            metadataDao.findAllByRootGidAndType(showId, MetadataDb.Type.TV_EPISODE)
        } else {
            metadataDao.findAllByRootGidAndParentIndexAndType(showId, seasonNumber, MetadataDb.Type.TV_EPISODE)
        }.map(MetadataDb::toTvEpisodeModel)
    }

    fun findMediaById(mediaId: String): FindMediaResult {
        val mediaRecord = metadataDao.findByGid(mediaId) ?: return FindMediaResult()
        return when (mediaRecord.mediaType) {
            MetadataDb.Type.MOVIE -> FindMediaResult(movie = mediaRecord.toMovieModel())
            MetadataDb.Type.TV_SHOW -> FindMediaResult(tvShow = mediaRecord.toTvShowModel())
            MetadataDb.Type.TV_EPISODE -> FindMediaResult(episode = mediaRecord.toTvEpisodeModel())
            MetadataDb.Type.TV_SEASON -> FindMediaResult(season = mediaRecord.toTvSeasonModel())
        }
    }

    fun findMetadataByLinkGid(gid: String): String? {
        return mediaLinkDao.findByGid(gid)?.metadataGid
    }

    fun findAllMediaLinks(): List<MediaLink> {
        return mediaLinkDao.all().map(MediaLinkDb::toModel)
    }

    fun findMediaLinkByGid(gid: String): MediaLink? {
        return mediaLinkDao.findByGid(gid)?.toModel()
    }

    fun insertMovie(movie: Movie): MetadataDb {
        val movieRecord = MetadataDb.fromMovie(movie)

        val id = metadataDao.insertMetadata(movieRecord)
        val companies = movie.companies.map { company ->
            if (company.id == -1) {
                company.tmdbId?.run(tagsDao::findCompanyByTmdbId)
                    ?: company.copy(id = tagsDao.insertTag(company.name, company.tmdbId))
            } else {
                company
            }
        }.onEach { company -> tagsDao.insertMetadataCompanyLink(id, company.id) }
        val genres = movie.genres.map { genre ->
            if (genre.id == -1) {
                genre.tmdbId?.run(tagsDao::findGenreByTmdbId)
                    ?: genre.copy(id = tagsDao.insertTag(genre.name, genre.tmdbId))
            } else {
                genre
            }
        }.onEach { genre -> tagsDao.insertMetadataGenreLink(id, genre.id) }
        val finalRecord = movieRecord.copy(id = id, genres = genres, companies = companies)

        searchableContentDao.insert(movie.gid, MetadataDb.Type.MOVIE, movie.title)
        return finalRecord
    }

    fun insertTvShow(
        tvShow: MetadataDb,
        tvSeasons: List<MetadataDb>,
        episodes: List<MetadataDb>
    ): Triple<MetadataDb, List<MetadataDb>, List<MetadataDb>> {
        val tvShowRecord = tvShow.copy(id = metadataDao.insertMetadata(tvShow))
        val tvSeasonRecordMap = tvSeasons.map { tvSeason ->
            val updatedSeason = tvSeason.copy(
                rootId = tvShowRecord.id,
                rootGid = tvShowRecord.gid,
                parentId = tvShowRecord.id,
                parentGid = tvShowRecord.gid,
            )
            updatedSeason.copy(id = metadataDao.insertMetadata(updatedSeason))
        }.associateBy { checkNotNull(it.index) }
        val tvEpisodeRecords = episodes.map { tvEpisode ->
            val tvSeasonRecord = tvSeasonRecordMap.getValue(tvEpisode.parentIndex!!)
            val updatedEpisode = tvEpisode.copy(
                rootId = tvShowRecord.id,
                rootGid = tvShowRecord.gid,
                parentId = tvSeasonRecord.id,
                parentGid = tvSeasonRecord.gid,
            )
            updatedEpisode.copy(id = metadataDao.insertMetadata(updatedEpisode))
        }

        if (!tvShow.title.isNullOrBlank()) {
            searchableContentDao.insert(tvShow.gid, MetadataDb.Type.TV_SHOW, tvShow.title)
        }
        episodes.forEach { episode ->
            if (!episode.title.isNullOrBlank()) {
                searchableContentDao.insert(episode.gid, MetadataDb.Type.TV_EPISODE, episode.title)
            }
        }
        return Triple(tvShowRecord, tvSeasonRecordMap.values.toList(), tvEpisodeRecords)
    }

    fun deleteMovie(mediaId: String): Boolean {
        metadataDao.deleteByGid(mediaId)
        return true
    }

    fun deleteTvShow(mediaId: String): Boolean {
        mediaLinkDao.deleteByRootContentGid(mediaId)
        metadataDao.deleteByRootGid(mediaId)
        metadataDao.deleteByGid(mediaId)
        return true
    }

    fun deleteLinksByContentId(mediaId: String) {
        mediaLinkDao.deleteByContentGid(mediaId)
    }

    fun deleteLinksByRootContentId(mediaId: String) {
        mediaLinkDao.deleteByRootContentGid(mediaId)
    }

    fun findTvSeasonsByIds(tvSeasonIds: List<String>): List<MetadataDb> {
        if (tvSeasonIds.isEmpty()) return emptyList()
        return metadataDao.findAllByGidsAndType(tvSeasonIds, MetadataDb.Type.TV_SEASON)
    }

    data class CurrentlyWatchingQueryResults(
        val playbackStates: List<PlaybackState>,
        val currentlyWatchingMovies: Map<String, Movie>,
        val currentlyWatchingTv: Map<String, Pair<Episode, TvShow>>,
    )

    data class FindMediaResult(
        val movie: Movie? = null,
        val tvShow: TvShow? = null,
        val episode: Episode? = null,
        val season: TvSeason? = null,
    ) {
        fun hasResult(): Boolean {
            return movie != null || tvShow != null || episode != null || season != null
        }
    }
}
