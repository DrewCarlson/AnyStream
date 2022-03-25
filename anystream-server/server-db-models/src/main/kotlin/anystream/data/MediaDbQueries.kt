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
import anystream.db.model.MediaDb
import anystream.db.model.MediaReferenceDb
import anystream.db.model.PlaybackStateDb
import anystream.models.*
import anystream.models.api.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MediaDbQueries(
    private val searchableContentDao: SearchableContentDao,
    private val mediaDao: MediaDao,
    private val tagsDao: TagsDao,
    private val mediaReferencesDao: MediaReferencesDao,
    private val playbackStatesDao: PlaybackStatesDao,
) {

    private val mediaInsertLock = Mutex()
    private val mediaRefInsertLock = Mutex()
    private val searchableContentInsertLock = Mutex()

    fun findMovies(includeRefs: Boolean = false): MoviesResponse {
        val mediaRecords = mediaDao.findAllByTypeSortedByTitle(MediaDb.Type.MOVIE)
        val mediaRefRecords = if (includeRefs && mediaRecords.isNotEmpty()) {
            mediaReferencesDao.findByContentGids(mediaRecords.map(MediaDb::gid))
        } else emptyList()

        return MoviesResponse(
            movies = mediaRecords.map(MediaDb::toMovieModel),
            mediaReferences = mediaRefRecords.map(MediaReferenceDb::toMediaRefModel),
        )
    }

    fun findMediaById(
        mediaId: String,
        includeRefs: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): MediaLookupResponse {
        return when (mediaDao.findTypeByGid(mediaId)) {
            MediaDb.Type.MOVIE -> MediaLookupResponse(
                movie = findMovieById(mediaId, includeRefs, includePlaybackStateForUser)
            )
            MediaDb.Type.TV_SHOW -> MediaLookupResponse(
                tvShow = findShowById(mediaId, includeRefs, includePlaybackStateForUser)
            )
            MediaDb.Type.TV_EPISODE -> MediaLookupResponse(
                episode = findEpisodeById(mediaId, includeRefs, includePlaybackStateForUser)
            )
            MediaDb.Type.TV_SEASON -> MediaLookupResponse(
                season = findSeasonById(mediaId, includeRefs, includePlaybackStateForUser)
            )
            else -> MediaLookupResponse()
        }
    }

    fun findMovieById(
        movieId: String,
        includeRefs: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): MovieResponse? {
        val mediaRecord = mediaDao.findByGidAndType(movieId, MediaDb.Type.MOVIE) ?: return null
        val mediaRefs = if (includeRefs) {
            mediaReferencesDao.findByContentGid(mediaRecord.gid)
        } else emptyList()
        val playbackState = includePlaybackStateForUser?.let { userId ->
            playbackStatesDao.findByUserIdAndMediaGid(userId, movieId)
        }

        return MovieResponse(
            movie = mediaRecord.toMovieModel(),
            mediaRefs = mediaRefs.map(MediaReferenceDb::toMediaRefModel),
            playbackState = playbackState?.toStateModel(),
        )
    }

    fun findShows(includeRefs: Boolean = false): TvShowsResponse {
        val mediaRecords = mediaDao.findAllByTypeSortedByTitle(MediaDb.Type.TV_SHOW)
        val mediaRefs = if (includeRefs && mediaRecords.isNotEmpty()) {
            val showIds = mediaRecords.map(MediaDb::gid)
            mediaReferencesDao.findByRootContentGids(showIds)
        } else emptyList()

        return TvShowsResponse(
            tvShows = mediaRecords.map(MediaDb::toTvShowModel),
            mediaRefs = mediaRefs.map(MediaReferenceDb::toMediaRefModel),
        )
    }

    fun findShowById(
        showId: String,
        includeRefs: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): TvShowResponse? {
        val show = mediaDao.findByGidAndType(showId, MediaDb.Type.TV_SHOW) ?: return null
        val seasons = mediaDao.findAllByParentGidAndType(showId, MediaDb.Type.TV_SEASON)
        val mediaRefs = if (includeRefs && seasons.isNotEmpty()) {
            val seasonGids = seasons.map(MediaDb::gid)
            mediaReferencesDao.findByContentGids(seasonGids + show.gid)
        } else emptyList()
        val playbackState = includePlaybackStateForUser
            ?.takeIf { mediaRefs.isNotEmpty() }
            ?.let { userId ->
                playbackStatesDao.findByUserIdAndMediaGids(userId, mediaRefs.map(MediaReferenceDb::gid))
                    .maxByOrNull(PlaybackStateDb::updatedAt)
            }
        return TvShowResponse(
            tvShow = show.toTvShowModel(),
            mediaRefs = mediaRefs.map(MediaReferenceDb::toMediaRefModel),
            seasons = seasons.map(MediaDb::toTvSeasonModel),
            playbackState = playbackState?.toStateModel(),
        )
    }

    fun findSeasonById(
        seasonId: String,
        includeRefs: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): SeasonResponse? {
        val seasonRecord = mediaDao.findByGidAndType(seasonId, MediaDb.Type.TV_SEASON) ?: return null
        val tvShowGid = checkNotNull(seasonRecord.parentGid)
        val tvShowRecord = mediaDao.findByGidAndType(tvShowGid, MediaDb.Type.TV_SHOW) ?: return null
        val episodeRecords = mediaDao.findAllByParentGidAndType(seasonId, MediaDb.Type.TV_EPISODE)
        val mediaRefs = if (includeRefs) {
            val searchIds = episodeRecords.map(MediaDb::gid) + tvShowGid + seasonId
            mediaReferencesDao.findByContentGids(searchIds)
        } else emptyList()
        val playbackStateRecord = includePlaybackStateForUser
            ?.takeIf { episodeRecords.isNotEmpty() }
            ?.let { userId ->
                playbackStatesDao.findByUserIdAndMediaGids(userId, episodeRecords.map(MediaDb::gid))
                    .maxByOrNull(PlaybackStateDb::updatedAt)
            }
        return SeasonResponse(
            season = seasonRecord.toTvSeasonModel(),
            show = tvShowRecord.toTvShowModel(),
            episodes = episodeRecords.map(MediaDb::toTvEpisodeModel),
            mediaRefs = mediaRefs.map(MediaReferenceDb::toMediaRefModel).associateBy(MediaReference::contentId),
            playbackState = playbackStateRecord?.toStateModel(),
        )
    }

    fun findEpisodeById(
        episodeId: String,
        includeRefs: Boolean = false,
        includePlaybackStateForUser: Int? = null,
    ): EpisodeResponse? {
        val episodeRecord = mediaDao.findByGidAndType(episodeId, MediaDb.Type.TV_EPISODE) ?: return null
        val showRecord = mediaDao.findByGid(checkNotNull(episodeRecord.rootGid)) ?: return null
        val mediaRefs = if (includeRefs) {
            mediaReferencesDao.findByContentGid(episodeId)
        } else emptyList()
        val playbackState = includePlaybackStateForUser?.let { userId ->
            playbackStatesDao.findByUserIdAndMediaGid(userId, episodeId)
        }
        return EpisodeResponse(
            episode = episodeRecord.toTvEpisodeModel(),
            show = showRecord.toTvShowModel(),
            mediaRefs = mediaRefs.map(MediaReferenceDb::toMediaRefModel),
            playbackState = playbackState?.toStateModel(),
        )
    }

    fun findCurrentlyWatching(userId: Int, limit: Int): CurrentlyWatchingQueryResults {
        // TODO: Limit is not correctly applied as playbackStates for different episodes
        //  of the same tv show are returned by the query and filtered later.
        //  The find query should select playbackStates with a unique root id.
        val playbackStates = playbackStatesDao.findByUserId(userId, limit)
            .map(PlaybackStateDb::toStateModel)
            .associateBy(PlaybackState::mediaId)

        if (playbackStates.isEmpty()) {
            return CurrentlyWatchingQueryResults(emptyList(), emptyMap(), emptyMap())
        }

        val playbackMediaIds = playbackStates.keys.toList()

        val movies = mediaDao.findAllByGidsAndType(playbackMediaIds, MediaDb.Type.MOVIE)
            .map(MediaDb::toMovieModel)

        val episodes = mediaDao.findAllByGidsAndType(playbackMediaIds, MediaDb.Type.TV_EPISODE)
            .map(MediaDb::toTvEpisodeModel)
            .sortedByDescending { playbackStates.getValue(it.id).updatedAt }
            .distinctBy(Episode::showId)

        val tvShowIds = episodes.map(Episode::showId)
        val tvShows = if (tvShowIds.isNotEmpty()) {
            mediaDao.findAllByGidsAndType(tvShowIds, MediaDb.Type.TV_SHOW)
                .map(MediaDb::toTvShowModel)
        } else {
            emptyList()
        }

        val filteredPlaybackStates = playbackStates.values.filter { state ->
            episodes.any { it.id == state.mediaId } || movies.any { it.id == state.mediaId }
        }

        val episodeAndShowPairs = episodes.map { episode ->
            episode to tvShows.first { it.id == episode.showId }
        }

        return CurrentlyWatchingQueryResults(
            playbackStates = filteredPlaybackStates,
            currentlyWatchingMovies = movies.associateBy { playbackStates.getValue(it.id).id },
            currentlyWatchingTv = episodeAndShowPairs.associateBy { (episode, _) ->
                playbackStates.getValue(episode.id).id
            },
        )
    }

    fun findRecentlyAddedMovies(limit: Int): Map<Movie, MediaReference?> {
        val movies = mediaDao.findByType(MediaDb.Type.MOVIE, limit).map(MediaDb::toMovieModel)
        val mediaReferences = if (movies.isNotEmpty()) {
            mediaReferencesDao.findByContentGids(movies.map(Movie::id))
                .map(MediaReferenceDb::toMediaRefModel)
        } else emptyList()
        return movies.associateWith { movie ->
            mediaReferences.find { it.contentId == movie.id }
        }
    }

    fun findRecentlyAddedTv(limit: Int): List<TvShow> {
        return mediaDao.findByType(MediaDb.Type.TV_SHOW, limit).map(MediaDb::toTvShowModel)
    }

    fun findMediaRefByFilePath(path: String): MediaReference? {
        return mediaReferencesDao.findByFilePath(path)?.toMediaRefModel()
    }

    fun findMediaRefsByContentId(mediaId: String): List<MediaReference> {
        return mediaReferencesDao.findByContentGid(mediaId).map(MediaReferenceDb::toMediaRefModel)
    }

    fun findMediaRefsByContentIds(mediaIds: List<String>): List<MediaReference> {
        if (mediaIds.isEmpty()) return emptyList()
        return mediaReferencesDao.findByContentGids(mediaIds).map(MediaReferenceDb::toMediaRefModel)
    }

    fun findMediaRefsByRootContentId(rootMediaId: String): List<MediaReference> {
        return mediaReferencesDao.findByRootContentGid(rootMediaId).map(MediaReferenceDb::toMediaRefModel)
    }

    fun findMovieByTmdbId(tmdbId: Int): Movie? {
        return mediaDao.findByTmdbIdAndType(tmdbId, MediaDb.Type.MOVIE)?.toMovieModel()
    }

    fun findMoviesByTmdbId(tmdbIds: List<Int>): List<Movie> {
        return mediaDao.findAllByTmdbIdsAndType(tmdbIds, MediaDb.Type.MOVIE).map(MediaDb::toMovieModel)
    }

    fun findTvShowById(showId: String): TvShow? {
        return mediaDao.findByGidAndType(showId, MediaDb.Type.TV_SHOW)?.toTvShowModel()
    }

    fun findTvShowBySeasonId(seasonId: String): TvShow? {
        return mediaDao.findByGidAndType(seasonId, MediaDb.Type.TV_SEASON)?.parentGid?.let { showId ->
            mediaDao.findByGidAndType(showId, MediaDb.Type.TV_SHOW)?.toTvShowModel()
        }
    }

    fun findTvShowByTmdbId(tmdbId: Int): TvShow? {
        return mediaDao.findByTmdbIdAndType(tmdbId, MediaDb.Type.TV_SHOW)?.toTvShowModel()
    }

    fun findTvSeasonsByTvShowId(tvShowId: String): List<TvSeason> {
        return mediaDao.findAllByParentGidAndType(tvShowId, MediaDb.Type.TV_SEASON).map(MediaDb::toTvSeasonModel)
    }

    fun findTvShowsByTmdbId(tmdbIds: List<Int>): List<TvShow> {
        if (tmdbIds.isEmpty()) return emptyList()
        return mediaDao.findAllByTmdbIdsAndType(tmdbIds, MediaDb.Type.TV_SHOW).map(MediaDb::toTvShowModel)
    }

    fun findEpisodesByShow(showId: String, seasonNumber: Int? = null): List<Episode> {
        return if (seasonNumber == null) {
            mediaDao.findAllByRootGidAndType(showId, MediaDb.Type.TV_EPISODE)
        } else {
            mediaDao.findAllByRootGidAndParentIndexAndType(showId, seasonNumber, MediaDb.Type.TV_EPISODE)
        }.map(MediaDb::toTvEpisodeModel)
    }

    fun findEpisodesBySeason(seasonId: String): List<Episode> {
        return mediaDao.findAllByParentGidAndType(seasonId, MediaDb.Type.TV_SEASON)
            .map(MediaDb::toTvEpisodeModel)
    }

    fun findMediaById(mediaId: String): FindMediaResult {
        val mediaRecord = mediaDao.findByGid(mediaId) ?: return FindMediaResult()
        return when (mediaRecord.mediaType) {
            MediaDb.Type.MOVIE -> FindMediaResult(movie = mediaRecord.toMovieModel())
            MediaDb.Type.TV_SHOW -> FindMediaResult(tvShow = mediaRecord.toTvShowModel())
            MediaDb.Type.TV_EPISODE -> FindMediaResult(episode = mediaRecord.toTvEpisodeModel())
            MediaDb.Type.TV_SEASON -> FindMediaResult(season = mediaRecord.toTvSeasonModel())
        }
    }

    fun findMediaIdByRefId(refId: String): String? {
        return mediaReferencesDao.findByGid(refId)?.contentGid
    }

    fun findAllMediaRefs(): List<MediaReference> {
        return mediaReferencesDao.all().map(MediaReferenceDb::toMediaRefModel)
    }

    fun findMediaRefById(refId: String): MediaReference? {
        return mediaReferencesDao.findByGid(refId)?.toMediaRefModel()
    }

    suspend fun insertMediaReference(mediaReference: MediaReference) {
        mediaRefInsertLock.withLock {
            mediaReferencesDao.insertReference(MediaReferenceDb.fromRefModel(mediaReference))
        }
    }

    suspend fun insertMediaReferences(mediaReferences: List<MediaReference>) {
        mediaRefInsertLock.withLock {
            mediaReferences
                .map(MediaReferenceDb::fromRefModel)
                .forEach(mediaReferencesDao::insertReference)
        }
    }

    suspend fun insertMovie(movie: Movie): MediaDb {
        val movieRecord = MediaDb.fromMovie(movie)
        val finalRecord = mediaInsertLock.withLock {
            val id = mediaDao.insertMedia(movieRecord)
            val companies = movie.companies.map { company ->
                if (company.id == -1) {
                    company.tmdbId?.run(tagsDao::findCompanyByTmdbId)
                        ?: company.copy(id = tagsDao.insertTag(company.name, company.tmdbId))
                } else company
            }.onEach { company -> tagsDao.insertMediaCompanyLink(id, company.id) }
            val genres = movie.genres.map { genre ->
                if (genre.id == -1) {
                    genre.tmdbId?.run(tagsDao::findGenreByTmdbId)
                        ?: genre.copy(id = tagsDao.insertTag(genre.name, genre.tmdbId))
                } else genre
            }.onEach { genre -> tagsDao.insertMediaGenreLink(id, genre.id) }
            movieRecord.copy(id = id, genres = genres, companies = companies)
        }
        searchableContentInsertLock.withLock {
            searchableContentDao.insert(movie.id, MediaDb.Type.MOVIE, movie.title)
        }
        return finalRecord
    }

    suspend fun insertTvShow(tvShow: TvShow, tvSeasons: List<TvSeason>, episodes: List<Episode>) {
        mediaInsertLock.withLock {
            val tvShowRecord = MediaDb.fromTvShow(tvShow).let { record ->
                record.copy(id = mediaDao.insertMedia(record))
            }
            val tvSeasonRecords = tvSeasons.map { tvSeason ->
                MediaDb.fromTvSeason(tvShowRecord, tvSeason).let { record ->
                    record.copy(id = mediaDao.insertMedia(record))
                }
            }
            val tvSeasonRecordMap = tvSeasonRecords.associateBy { checkNotNull(it.index) }
            val tvEpisodeRecords = episodes.map { tvEpisode ->
                val tvSeasonRecord = tvSeasonRecordMap.getValue(tvEpisode.seasonNumber)
                MediaDb.fromTvEpisode(tvShowRecord, tvSeasonRecord, tvEpisode)
            }
            tvEpisodeRecords.forEach(mediaDao::insertMedia)
        }

        searchableContentInsertLock.withLock {
            searchableContentDao.insert(tvShow.id, MediaDb.Type.TV_SHOW, tvShow.name)
            episodes.forEach { episode ->
                searchableContentDao.insert(episode.id, MediaDb.Type.TV_EPISODE, episode.name)
            }
        }
    }

    fun updateMovie(movie: Movie): Boolean {
        TODO()
        // return moviesDb.updateOne(movie).modifiedCount > 0
    }

    fun updateTvShow(tvShow: TvShow): Boolean {
        TODO()
        // return tvShowDb.updateOne(tvShow).modifiedCount > 0
    }

    fun updateTvSeason(tvSeason: TvSeason): Boolean {
        TODO()
        /*return tvShowDb.updateOne(
            TvShow::seasons elemMatch (TvSeason::id eq tvSeason.id),
            set(TvShow::seasons.posOp setTo tvSeason)
        ).modifiedCount > 0*/
    }

    fun updateTvEpisode(episode: Episode): Boolean {
        TODO()
        // return episodeDb.updateOne(episode).modifiedCount > 0
    }

    fun updateTvEpisodes(episodes: List<Episode>): Boolean {
        TODO()
        /*val updates = episodes.map { episode ->
            replaceOne(Episode::id eq episode.id, episode)
        }
        return episodeDb.bulkWrite(updates).run {
            modifiedCount > 0 || deletedCount > 0 || insertedCount > 0
        }*/
    }

    fun deleteMovie(mediaId: String): Boolean {
        mediaDao.deleteByGid(mediaId)
        return true
    }

    fun deleteTvShow(mediaId: String): Boolean {
        mediaReferencesDao.deleteByRootContentGid(mediaId)
        mediaDao.deleteByRootGid(mediaId)
        mediaDao.deleteByGid(mediaId)
        return true
    }

    fun deleteRefsByContentId(mediaId: String) {
        mediaReferencesDao.deleteByContentGid(mediaId)
    }

    fun deleteRefsByRootContentId(mediaId: String) {
        mediaReferencesDao.deleteByRootContentGid(mediaId)
    }

    fun findTvSeasonsByIds(tvSeasonIds: List<String>): List<MediaDb> {
        if (tvSeasonIds.isEmpty()) return emptyList()
        return mediaDao.findAllByGidsAndType(tvSeasonIds, MediaDb.Type.TV_SEASON)
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
