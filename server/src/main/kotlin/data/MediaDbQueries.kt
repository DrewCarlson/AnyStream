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
package anystream.data

import anystream.models.*
import anystream.models.api.*
import com.mongodb.MongoException
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.projection
import org.litote.kmongo.coroutine.updateOne

class MediaDbQueries(
    mongodb: CoroutineDatabase
) {

    private val moviesDb = mongodb.getCollection<Movie>()
    private val tvShowDb = mongodb.getCollection<TvShow>()
    private val episodeDb = mongodb.getCollection<Episode>()
    private val usersDb = mongodb.getCollection<User>()
    private val mediaRefsDb = mongodb.getCollection<MediaReference>()
    private val playbackStatesDb = mongodb.getCollection<PlaybackState>()

    suspend fun createIndexes() {
        try {
            moviesDb.ensureIndex(Movie::title.textIndex())
            moviesDb.ensureIndex(Movie::tmdbId)
            tvShowDb.ensureIndex(TvShow::name.textIndex())
            tvShowDb.ensureIndex(TvShow::tmdbId)
            episodeDb.ensureIndex(Episode::name.textIndex())
            episodeDb.ensureIndex(Episode::showId)
            mediaRefsDb.ensureIndex(MediaReference::contentId)
            playbackStatesDb.ensureIndex(PlaybackState::userId)
        } catch (e: MongoException) {
            println("Failed to create search indexes")
            e.printStackTrace()
        }
    }

    suspend fun findMovies(includeRefs: Boolean = false): MoviesResponse {
        val movies = moviesDb.find().toList()
        val mediaRefs = if (includeRefs) {
            val movieIds = movies.map(Movie::id)
            mediaRefsDb.find(MediaReference::contentId `in` movieIds).toList()
        } else emptyList()

        return MoviesResponse(
            movies = movies,
            mediaReferences = mediaRefs,
        )
    }

    suspend fun findMovieById(movieId: String, includeRefs: Boolean = false): MovieResponse? {
        val movie = moviesDb.findOneById(movieId) ?: return null
        val mediaRefs = if (includeRefs) {
            mediaRefsDb.find(MediaReference::contentId eq movieId).toList()
        } else emptyList()

        return MovieResponse(
            movie = movie,
            mediaRefs = mediaRefs
        )
    }

    suspend fun findShows(includeRefs: Boolean = false): TvShowsResponse {
        val tvShows = tvShowDb.find().toList()
        val mediaRefs = if (includeRefs) {
            val tvShowIds = tvShows.map(TvShow::id)
            mediaRefsDb.find(MediaReference::rootContentId `in` tvShowIds).toList()
        } else emptyList()

        return TvShowsResponse(
            tvShows = tvShows,
            mediaRefs = mediaRefs,
        )
    }

    suspend fun findShowById(showId: String, includeRefs: Boolean = false): TvShowResponse? {
        val show = tvShowDb.findOneById(showId) ?: return null
        val mediaRefs = if (includeRefs) {
            val seasonIds = show.seasons.map(TvSeason::id)
            mediaRefsDb.find(MediaReference::contentId `in` seasonIds + showId).toList()
        } else emptyList()
        return TvShowResponse(
            tvShow = show,
            mediaRefs = mediaRefs,
        )
    }

    suspend fun findSeasonById(seasonId: String, includeRefs: Boolean = false): SeasonResponse? {
        val tvShow = tvShowDb.findOne(TvShow::seasons elemMatch (TvSeason::id eq seasonId))
            ?: return null
        val season = tvShow.seasons.find { it.id == seasonId }
            ?: return null
        val episodes = episodeDb
            .find(
                and(
                    Episode::showId eq tvShow.id,
                    Episode::seasonNumber eq season.seasonNumber,
                )
            )
            .toList()
        val mediaRefs = if (includeRefs) {
            val episodeIds = episodes.map(Episode::id)
            mediaRefsDb
                .find(MediaReference::contentId `in` episodeIds)
                .toList()
        } else emptyList()
        return SeasonResponse(
            show = tvShow,
            season = season,
            episodes = episodes,
            mediaRefs = mediaRefs.associateBy(MediaReference::contentId)
        )
    }

    suspend fun findEpisodeById(episodeId: String, includeRefs: Boolean = false): EpisodeResponse? {
        val episode = episodeDb.findOneById(episodeId) ?: return null
        val show = tvShowDb.findOneById(episode.showId) ?: return null
        val mediaRefs = if (includeRefs) {
            mediaRefsDb
                .find(MediaReference::contentId eq episode.id)
                .toList()
        } else emptyList()
        return EpisodeResponse(
            episode = episode,
            show = show,
            mediaRefs = mediaRefs,
        )
    }

    suspend fun findPlaybackStatesByIds(ids: List<String>): List<PlaybackState> {
        return playbackStatesDb.find(PlaybackState::id `in` ids).toList()
    }

    suspend fun findUsersByIds(ids: List<String>): List<User> {
        return usersDb.find(User::id `in` ids).toList()
    }

    suspend fun findCurrentlyWatching(userId: String, limit: Int): CurrentlyWatchingQueryResults {
        val allPlaybackStates = playbackStatesDb
            .find(PlaybackState::userId eq userId)
            .sort(descending(PlaybackState::updatedAt))
            .limit(limit)
            .toList()

        val playbackMediaIds = allPlaybackStates.map(PlaybackState::mediaId)

        val playbackStateMovies = moviesDb
            .find(Movie::id `in` playbackMediaIds)
            .toList()
            .associateBy { movie ->
                allPlaybackStates.first { it.mediaId == movie.id }.id
            }

        val playbackStateEpisodes = episodeDb
            .find(Episode::id `in` playbackMediaIds)
            .toList()
            .distinctBy(Episode::showId)

        val playbackStateTv = tvShowDb
            .find(TvShow::id `in` playbackStateEpisodes.map(Episode::showId))
            .toList()
            .associateBy { show ->
                playbackStateEpisodes.first { it.showId == show.id }
            }
            .toList()
            .associateBy { (episode, _) ->
                allPlaybackStates.first { it.mediaId == episode.id }.id
            }

        val playbackStates = allPlaybackStates
            .filter { state ->
                playbackStateEpisodes.any { it.id == state.mediaId } ||
                        playbackStateMovies.any { (_, movie) -> movie.id == state.mediaId }
            }

        return CurrentlyWatchingQueryResults(
            playbackStates = playbackStates,
            currentlyWatchingMovies = playbackStateMovies,
            currentlyWatchingTv = playbackStateTv,
        )
    }

    suspend fun findRecentlyAddedMovies(limit: Int): Map<Movie, MediaReference?> {
        val recentlyAddedMovies = moviesDb
            .find()
            .sort(descending(Movie::added))
            .limit(limit)
            .toList()
        val recentlyAddedRefs = mediaRefsDb
            .find(MediaReference::contentId `in` recentlyAddedMovies.map(Movie::id))
            .toList()
        return recentlyAddedMovies.associateWith { movie ->
            recentlyAddedRefs.find { it.contentId == movie.id }
        }
    }

    suspend fun findRecentlyAddedTv(limit: Int): List<TvShow> {
        return tvShowDb
            .find()
            .sort(descending(TvShow::added))
            .limit(limit)
            .toList()
    }

    suspend fun findMediaRefByFilePath(path: String): MediaReference? {
        return mediaRefsDb.findOne(LocalMediaReference::filePath eq path)
    }

    suspend fun findMediaRefsByContentId(mediaId: String): List<MediaReference> {
        return mediaRefsDb.find(MediaReference::contentId eq mediaId).toList()
    }

    suspend fun findMediaRefsByContentIds(mediaIds: List<String>): List<MediaReference> {
        return mediaRefsDb.find(MediaReference::contentId `in` mediaIds).toList()
    }

    suspend fun findMediaRefsByRootContentId(rootMediaId: String): List<MediaReference> {
        return mediaRefsDb.find(MediaReference::rootContentId eq rootMediaId).toList()
    }

    suspend fun findMovieByTmdbId(tmdbId: Int): Movie? {
        return moviesDb.findOne(Movie::tmdbId eq tmdbId)
    }

    suspend fun findMoviesByTmdbId(tmdbIds: List<Int>): List<Movie> {
        return moviesDb.find(Movie::tmdbId `in` tmdbIds).toList()
    }

    suspend fun findTvShowById(showId: String): TvShow? {
        return tvShowDb.findOneById(showId)
    }

    suspend fun findTvShowBySeasonId(seasonId: String): TvShow? {
        return tvShowDb.findOne(TvShow::seasons elemMatch (TvSeason::id eq seasonId))
    }

    suspend fun findTvShowByTmdbId(tmdbId: Int): TvShow? {
        return tvShowDb.findOne(TvShow::tmdbId eq tmdbId)
    }

    suspend fun findTvShowsByTmdbId(tmdbIds: List<Int>): List<TvShow> {
        return tvShowDb.find(TvShow::tmdbId `in` tmdbIds).toList()
    }

    suspend fun findEpisodesByShow(showId: String, seasonNumber: Int? = null): List<Episode> {
        return if (seasonNumber == null) {
            episodeDb.find(Episode::showId eq showId).toList()
        } else {
            episodeDb.find(
                Episode::showId eq showId,
                Episode::seasonNumber eq seasonNumber,
            ).toList()
        }
    }

    suspend fun findEpisodesBySeason(seasonId: String): List<Episode> {
        val show = findTvShowBySeasonId(seasonId) ?: return emptyList()
        val seasonNumber = show.seasons.first { it.id == seasonId }.seasonNumber
        return episodeDb.find(
            Episode::showId eq show.id,
            Episode::seasonNumber eq seasonNumber,
        ).toList()
    }

    suspend fun findMediaById(mediaId: String): FindMediaResult {
        return FindMediaResult(
            movie = moviesDb.findOneById(mediaId),
            tvShow = tvShowDb.findOneById(mediaId),
            episode = episodeDb.findOneById(mediaId),
            season = tvShowDb.findOne(TvShow::seasons elemMatch (TvSeason::id eq mediaId))
                ?.seasons
                ?.firstOrNull { it.id == mediaId },
        )
    }

    suspend fun findMediaIdByRefId(refId: String): String? {
        return mediaRefsDb
            .projection(
                MediaReference::contentId,
                MediaReference::id eq refId,
            )
            .first()
    }

    suspend fun insertMediaReference(mediaReference: MediaReference) {
        mediaRefsDb.insertOne(mediaReference)
    }

    suspend fun insertMediaReferences(mediaReferences: List<MediaReference>) {
        mediaRefsDb.insertMany(mediaReferences)
    }

    suspend fun insertMovie(movie: Movie) {
        moviesDb.insertOne(movie)
    }

    suspend fun insertTvShow(tvShow: TvShow, episodes: List<Episode> = emptyList()) {
        tvShowDb.insertOne(tvShow)
        if (episodes.isNotEmpty()) {
            episodeDb.insertMany(episodes)
        }
    }

    suspend fun updateMovie(movie: Movie): Boolean {
        return moviesDb.updateOne(movie).modifiedCount > 0
    }

    suspend fun updateTvShow(tvShow: TvShow): Boolean {
        return tvShowDb.updateOne(tvShow).modifiedCount > 0
    }

    suspend fun updateTvSeason(tvSeason: TvSeason): Boolean {
        return tvShowDb.updateOne(
            TvShow::seasons elemMatch (TvSeason::id eq tvSeason.id),
            set(TvShow::seasons.posOp setTo tvSeason)
        ).modifiedCount > 0
    }

    suspend fun updateTvEpisode(episode: Episode): Boolean {
        return episodeDb.updateOne(episode).modifiedCount > 0
    }

    suspend fun updateTvEpisodes(episodes: List<Episode>): Boolean {
        val updates = episodes.map { episode ->
            replaceOne(Episode::id eq episode.id, episode)
        }
        return episodeDb.bulkWrite(updates).run {
            modifiedCount > 0 || deletedCount > 0 || insertedCount > 0
        }
    }

    suspend fun deleteMovie(mediaId: String): Boolean {
        return moviesDb.deleteOneById(mediaId).deletedCount > 0
    }

    suspend fun deleteTvShow(mediaId: String): Boolean {
        return if (tvShowDb.deleteOneById(mediaId).deletedCount > 0) {
            episodeDb.deleteMany(Episode::showId eq mediaId)
            mediaRefsDb.deleteMany(MediaReference::rootContentId eq mediaId)
            true
        } else false
    }

    suspend fun deleteRefsByContentId(mediaId: String) {
        mediaRefsDb.deleteMany(MediaReference::contentId eq mediaId)
    }

    suspend fun deleteRefsByRootContentId(mediaId: String) {
        mediaRefsDb.deleteMany(MediaReference::rootContentId eq mediaId)
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