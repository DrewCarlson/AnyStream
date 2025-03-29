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
import anystream.db.pojos.*
import anystream.db.tables.references.MEDIA_LINK
import anystream.db.tables.references.METADATA
import anystream.models.*
import anystream.models.PlaybackState
import anystream.models.api.*
import org.jooq.DSLContext

class MetadataDbQueries(
    private val db: DSLContext,
    val metadataDao: MetadataDao,
    val tagsDao: TagsDao,
    val mediaLinkDao: MediaLinkDao,
    val playbackStatesDao: PlaybackStatesDao,
) {

    suspend fun findMovies(
        includeLinks: Boolean = false,
        limit: Int = 0,
        offset: Int = 0,
    ): MoviesResponse {
        val recordCount = metadataDao.countByType(MediaType.MOVIE)
        val mediaRecords = if (limit == 0) {
            metadataDao.findAllByTypeSortedByTitle(MediaType.MOVIE)
        } else {
            metadataDao.findByTypeSortedByTitle(MediaType.MOVIE, limit, offset)
        }.map(Metadata::toMovieModel)
        val mediaLinkRecords = if (includeLinks && mediaRecords.isNotEmpty()) {
            db.selectFrom(MEDIA_LINK)
                .where(MEDIA_LINK.ID.`in`(mediaRecords.map(Movie::id)))
                .fetchMap(
                    MEDIA_LINK.METADATA_ID.coerce(String::class.java),
                    MediaLink::class.java
                )
        } else {
            emptyMap()
        }

        return MoviesResponse(
            movies = mediaRecords,
            mediaLinks = mediaLinkRecords,
            limit = limit,
            offset = offset,
            total = recordCount.toInt(),
        )
    }

    suspend fun findMediaById(
        metadataId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: String? = null,
    ): MediaLookupResponse? {
        return when (val type = metadataDao.findTypeById(metadataId)) {
            MediaType.MOVIE -> findMovieById(metadataId, includeLinks, includePlaybackStateForUser)
            MediaType.TV_SHOW -> findShowById(metadataId, includeLinks, includePlaybackStateForUser)
            MediaType.TV_EPISODE -> findEpisodeById(metadataId, includeLinks, includePlaybackStateForUser)
            MediaType.TV_SEASON -> findSeasonById(metadataId, includeLinks, includePlaybackStateForUser)
            else -> error("Unhandled MediaType '$type'")
        }
    }

    suspend fun findMovieById(
        movieId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: String? = null,
    ): MovieResponse? {
        val mediaRecord = metadataDao.findByIdAndType(movieId, MediaType.MOVIE) ?: return null
        val mediaLinks = if (includeLinks) {
            mediaLinkDao.findByMetadataId(mediaRecord.id)
        } else {
            emptyList()
        }
        val playbackState = includePlaybackStateForUser?.let { userId ->
            playbackStatesDao.findByUserIdAndMediaId(userId, movieId)
        }

        return MovieResponse(
            movie = mediaRecord.toMovieModel(),
            mediaLinks = mediaLinks,
            playbackState = playbackState,
        )
    }

    suspend fun findShows(includeLinks: Boolean = false): TvShowsResponse {
        val mediaRecords = metadataDao.findAllByTypeSortedByTitle(MediaType.TV_SHOW)
        val mediaLinks = if (includeLinks && mediaRecords.isNotEmpty()) {
            val showIds = mediaRecords.map(Metadata::id)
            mediaLinkDao.findByRootMetadataIds(showIds)
        } else {
            emptyList()
        }

        return TvShowsResponse(
            tvShows = mediaRecords.map(Metadata::toTvShowModel),
            mediaLinks = mediaLinks,
        )
    }

    suspend fun findShowById(
        showGid: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: String? = null,
    ): TvShowResponse? {
        val show = metadataDao.findByGidAndType(showGid, MediaType.TV_SHOW) ?: return null
        val seasons = metadataDao.findAllByParentGidAndType(showGid, MediaType.TV_SEASON)
        val mediaLinks = if (includeLinks && seasons.isNotEmpty()) {
            val seasonIds = seasons.map(Metadata::id)
            mediaLinkDao.findByMetadataIds(seasonIds + show.id)
        } else {
            emptyList()
        }
        val playbackState = includePlaybackStateForUser
            ?.takeIf { mediaLinks.isNotEmpty() }
            ?.let { userId ->
                playbackStatesDao.findByUserIdAndMediaGids(userId, mediaLinks.map(MediaLink::id))
                    .maxByOrNull(PlaybackState::updatedAt)
            }
        return TvShowResponse(
            tvShow = show.toTvShowModel(),
            mediaLinks = mediaLinks,
            seasons = seasons.map(Metadata::toTvSeasonModel),
            playbackState = playbackState,
        )
    }

    suspend fun findSeasonById(
        seasonId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: String? = null,
    ): SeasonResponse? {
        val seasonRecord = metadataDao.findByIdAndType(seasonId, MediaType.TV_SEASON) ?: return null
        val tvShowId = checkNotNull(seasonRecord.parentId)
        val tvShowRecord = metadataDao.findByIdAndType(tvShowId, MediaType.TV_SHOW) ?: return null
        val episodeRecords = metadataDao.findAllByParentIdAndType(seasonId, MediaType.TV_EPISODE)
        val mediaLinks = if (includeLinks) {
            val searchIds = episodeRecords.map(Metadata::id) + tvShowId + seasonId
            db.select(MEDIA_LINK.METADATA_ID)
                .from(MEDIA_LINK)
                .where(MEDIA_LINK.METADATA_ID.`in`(searchIds))
                .fetchMap(
                    MEDIA_LINK.METADATA_ID.coerce(String::class.java),
                    MediaLink::class.java
                )
        } else {
            emptyMap()
        }
        val playbackStateRecord = includePlaybackStateForUser
            ?.takeIf { episodeRecords.isNotEmpty() }
            ?.let { userId ->
                playbackStatesDao.findByUserIdAndMediaGids(userId, episodeRecords.map(Metadata::id))
                    .maxByOrNull(PlaybackState::updatedAt)
            }
        return SeasonResponse(
            season = seasonRecord.toTvSeasonModel(),
            show = tvShowRecord.toTvShowModel(),
            episodes = episodeRecords.map(Metadata::toTvEpisodeModel),
            mediaLinkMap = mediaLinks,
            playbackState = playbackStateRecord,
        )
    }

    suspend fun findEpisodeById(
        episodeId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: String? = null,
    ): EpisodeResponse? {
        val episodeRecord = metadataDao.findByIdAndType(episodeId, MediaType.TV_EPISODE) ?: return null
        val showRecord = metadataDao.findByGid(checkNotNull(episodeRecord.rootId)) ?: return null
        val mediaLinks = if (includeLinks) {
            mediaLinkDao.findByMetadataId(episodeId)
        } else {
            emptyList()
        }
        val playbackState = includePlaybackStateForUser?.let { userId ->
            playbackStatesDao.findByUserIdAndMediaId(userId, episodeId)
        }
        return EpisodeResponse(
            episode = episodeRecord.toTvEpisodeModel(),
            show = showRecord.toTvShowModel(),
            mediaLinks = mediaLinks,
            playbackState = playbackState,
        )
    }

    suspend fun findCurrentlyWatching(userId: String, limit: Int): CurrentlyWatchingQueryResults {
        val playbackStates = playbackStatesDao.findWithUniqueRootByUserId(userId, limit)
            .associateBy(PlaybackState::metadataId)

        if (playbackStates.isEmpty()) {
            return CurrentlyWatchingQueryResults(emptyList(), emptyMap(), emptyMap())
        }

        val playbackMediaIds = playbackStates.keys.toList()

        val movies = metadataDao.findAllByIdsAndType(playbackMediaIds, MediaType.MOVIE)
            .map(Metadata::toMovieModel)

        val episodes = metadataDao.findAllByIdsAndType(playbackMediaIds, MediaType.TV_EPISODE)
            .map(Metadata::toTvEpisodeModel)

        val tvShowIds = episodes.map(Episode::showId)
        val tvShows = if (tvShowIds.isNotEmpty()) {
            metadataDao.findAllByGidsAndType(tvShowIds, MediaType.TV_SHOW)
                .map(Metadata::toTvShowModel)
        } else {
            emptyList()
        }

        val episodeAndShowPairs = episodes.map { episode ->
            episode to tvShows.first { it.id == episode.showId }
        }

        return CurrentlyWatchingQueryResults(
            playbackStates = playbackStates.values.toList(),
            currentlyWatchingMovies = movies.associateBy { playbackStates.getValue(it.id).id },
            currentlyWatchingTv = episodeAndShowPairs.associateBy { (episode, _) ->
                playbackStates.getValue(episode.id).id
            },
        )
    }

    suspend fun findRecentlyAddedMovies(limit: Int): Map<Movie, MediaLink?> {
        val movies = metadataDao.findByType(MediaType.MOVIE, limit).map(Metadata::toMovieModel)
        val mediaLink = if (movies.isNotEmpty()) {
            db.selectFrom(MEDIA_LINK)
                .where(MEDIA_LINK.METADATA_ID.`in`(movies.map(Movie::id)))
                .and(MEDIA_LINK.DESCRIPTOR.eq(Descriptor.VIDEO))
                .fetchInto(MediaLink::class.java)
        } else {
            emptyList()
        }
        return movies.associateWith { movie ->
            mediaLink.find { it.id == movie.id }
        }
    }

    suspend fun findRecentlyAddedTv(limit: Int): List<TvShow> {
        return metadataDao.findByType(MediaType.TV_SHOW, limit).map(Metadata::toTvShowModel)
    }

    suspend fun findMediaRefByFilePath(path: String): MediaLink? {
        return mediaLinkDao.findByFilePath(path)
    }

    suspend fun findMediaLinksByMetadataId(mediaId: String): List<MediaLink> {
        return mediaLinkDao.findByMetadataId(mediaId)
    }

    suspend fun findMediaLinksByMetadataIds(mediaIds: List<String>): List<MediaLink> {
        if (mediaIds.isEmpty()) return emptyList()
        return mediaLinkDao.findByMetadataIds(mediaIds)
    }

    suspend fun findMovieByTmdbId(tmdbId: Int): Movie? {
        return metadataDao.findByTmdbIdAndType(tmdbId, MediaType.MOVIE)?.toMovieModel()
    }

    suspend fun findMoviesByTmdbId(tmdbIds: List<Int>): List<Movie> {
        return metadataDao.findAllByTmdbIdsAndType(tmdbIds, MediaType.MOVIE).map(Metadata::toMovieModel)
    }

    suspend fun findTvShowByTmdbId(tmdbId: Int): TvShow? {
        return metadataDao.findByTmdbIdAndType(tmdbId, MediaType.TV_SHOW)?.toTvShowModel()
    }

    suspend fun findTvSeasonsByTvShowId(tvShowId: String): List<Metadata> {
        return metadataDao.findAllByParentGidAndType(tvShowId, MediaType.TV_SEASON)
    }

    suspend fun findTvShowsByTmdbId(tmdbIds: List<Int>): List<TvShow> {
        if (tmdbIds.isEmpty()) return emptyList()
        return metadataDao.findAllByTmdbIdsAndType(tmdbIds, MediaType.TV_SHOW).map(Metadata::toTvShowModel)
    }

    suspend fun findEpisodesByShow(showId: String, seasonNumber: Int? = null): List<Episode> {
        return if (seasonNumber == null) {
            metadataDao.findAllByRootGidAndType(showId, MediaType.TV_EPISODE)
        } else {
            metadataDao.findAllByRootGidAndParentIndexAndType(showId, seasonNumber, MediaType.TV_EPISODE)
        }.map(Metadata::toTvEpisodeModel)
    }

    suspend fun findMediaById(mediaId: String): FindMediaResult {
        val mediaRecord = metadataDao.findByGid(mediaId) ?: return FindMediaResult()
        return when (mediaRecord.mediaType) {
            MediaType.MOVIE -> FindMediaResult(movie = mediaRecord.toMovieModel())
            MediaType.TV_SHOW -> FindMediaResult(tvShow = mediaRecord.toTvShowModel())
            MediaType.TV_EPISODE -> FindMediaResult(episode = mediaRecord.toTvEpisodeModel())
            MediaType.TV_SEASON -> FindMediaResult(season = mediaRecord.toTvSeasonModel())
        }
    }

    suspend fun findMediaLinkByGid(gid: String): MediaLink? {
        return mediaLinkDao.findById(gid)
    }

    suspend fun insertMovie(movie: Movie): Metadata {
        val movieRecord = movie.toMetadataDb()
        val id = metadataDao.insertMetadata(movieRecord)
        val companies = movie.companies.map { company ->
            if (company.id.isBlank()) {
                company.tmdbId?.let { tagsDao.findCompanyByTmdbId(it) }
                    ?: company.copy(id = tagsDao.insertTag(company.name, company.tmdbId))
            } else {
                company
            }
        }.onEach { company -> tagsDao.insertMetadataCompanyLink(id, company.id) }
        val genres = movie.genres.map { genre ->
            if (genre.id.isBlank()) {
                genre.tmdbId?.let { tagsDao.findGenreByTmdbId(it) }
                    ?: genre.copy(id = tagsDao.insertTag(genre.name, genre.tmdbId))
            } else {
                genre
            }
        }.onEach { genre -> tagsDao.insertMetadataGenreLink(id, genre.id) }
        return movieRecord.copy(id = id)
    }

    fun insertMetadata(metadata: Metadata): Int {
        val record = db.newRecord(METADATA, metadata)
        return record.store()
    }

    suspend fun insertTvShow(
        tvShow: Metadata,
        tvSeasons: List<Metadata>,
        episodes: List<Metadata>,
    ): Triple<Metadata, List<Metadata>, List<Metadata>> {
        val tvShowRecord = tvShow.copy(id = metadataDao.insertMetadata(tvShow))
        val tvSeasonRecordMap = tvSeasons.map { tvSeason ->
            val updatedSeason = tvSeason.copy(
                rootId = tvShowRecord.id,
                parentId = tvShowRecord.id,
            )
            updatedSeason.copy(id = metadataDao.insertMetadata(updatedSeason))
        }.associateBy { checkNotNull(it.index) }
        val tvEpisodeRecords = episodes.map { tvEpisode ->
            val tvSeasonRecord = tvSeasonRecordMap.getValue(tvEpisode.parentIndex!!)
            val updatedEpisode = tvEpisode.copy(
                rootId = tvShowRecord.id,
                parentId = tvSeasonRecord.id,
            )
            updatedEpisode.copy(id = metadataDao.insertMetadata(updatedEpisode))
        }

        return Triple(tvShowRecord, tvSeasonRecordMap.values.toList(), tvEpisodeRecords)
    }

    suspend fun deleteMovie(mediaId: String): Boolean {
        metadataDao.deleteByGid(mediaId)
        return true
    }

    suspend fun deleteTvShow(mediaId: String): Boolean {
        mediaLinkDao.deleteByRootContentGid(mediaId)
        metadataDao.deleteByRootGid(mediaId)
        metadataDao.deleteByGid(mediaId)
        return true
    }

    suspend fun deleteLinksByContentId(mediaId: String) {
        mediaLinkDao.deleteByContentGid(mediaId)
    }

    suspend fun deleteLinksByRootContentId(mediaId: String) {
        mediaLinkDao.deleteByRootContentGid(mediaId)
    }

    suspend fun findTvSeasonsByIds(tvSeasonIds: List<String>): List<Metadata> {
        if (tvSeasonIds.isEmpty()) return emptyList()
        return metadataDao.findAllByGidsAndType(tvSeasonIds, MediaType.TV_SEASON)
    }

    /**
     * @param currentlyWatchingMovies A map of PlaybackState ids to their Movie metadata.
     * @param currentlyWatchingTv A map of PlaybackState ids to their Tv show metadata.
     */
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
