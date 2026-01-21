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
import anystream.db.util.*
import anystream.db.pojos.*
import anystream.db.tables.references.MEDIA_LINK
import anystream.db.tables.references.METADATA
import anystream.models.*
import anystream.models.PlaybackState
import anystream.models.api.*
import kotlinx.coroutines.future.await
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

class MetadataDbQueries(
    private val db: DSLContext,
    val metadataDao: MetadataDao,
    private val tagsDao: TagsDao,
    private val mediaLinkDao: MediaLinkDao,
    private val playbackStatesDao: PlaybackStatesDao,
    private val searchableContentDao: SearchableContentDao,
) {
    private val logger = LoggerFactory.getLogger(MetadataDbQueries::class.java)

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
            db.select(MEDIA_LINK)
                .from(MEDIA_LINK)
                .where(MEDIA_LINK.METADATA_ID.`in`(mediaRecords.map(Movie::id)))
                .fetchAsync()
                .thenApplyAsync {
                    it.map { row -> row.intoType<MediaLink>() }
                }
                .await()
                .associateBy { checkNotNull(it.metadataId) }
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
        return when (val type = metadataDao.findType(metadataId)) {
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
            playbackStatesDao.findByUserIdAndMetadataId(userId, movieId)
        }

        val genres = tagsDao.findGenresForMetadata(movieId)
        val companies = tagsDao.findCompaniesForMetadata(movieId)
        val (cast, crew) = tagsDao.findCastAndCrewForMetadata(movieId)

        return MovieResponse(
            movie = mediaRecord.toMovieModel(),
            mediaLinks = mediaLinks,
            playbackState = playbackState,
            genres = genres,
            companies = companies,
            cast = cast,
            crew = crew,
            streamEncodings = mediaLinks.associate { link ->
                link.id to mediaLinkDao.findStreamEncodings(link.id)
            }
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
        showId: String,
        includeLinks: Boolean = false,
        includePlaybackStateForUser: String? = null,
    ): TvShowResponse? {
        val show = metadataDao.findByIdAndType(showId, MediaType.TV_SHOW) ?: return null
        val seasons = metadataDao.findAllByParentIdAndType(showId, MediaType.TV_SEASON)
        val mediaLinks = if (includeLinks && seasons.isNotEmpty()) {
            val seasonIds = seasons.map(Metadata::id)
            mediaLinkDao.findByMetadataIds(seasonIds + show.id)
        } else {
            emptyList()
        }
        val playbackState = includePlaybackStateForUser
            ?.takeIf { mediaLinks.isNotEmpty() }
            ?.let { userId ->
                val mediaLinkIds = mediaLinks.mapNotNull(MediaLink::metadataId)
                playbackStatesDao.findByUserIdAndMetadataIds(userId, mediaLinkIds)
                    .maxByOrNull(PlaybackState::updatedAt)
            }
        val (cast, crew)  = tagsDao.findCastAndCrewForMetadata(showId)
        return TvShowResponse(
            tvShow = show.toTvShowModel(),
            mediaLinks = mediaLinks,
            seasons = seasons.map(Metadata::toTvSeasonModel),
            genres = tagsDao.findGenresForMetadata(showId),
            companies = tagsDao.findCompaniesForMetadata(showId),
            cast = cast,
            crew = crew,
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
            db.select(MEDIA_LINK)
                .from(MEDIA_LINK)
                .where(MEDIA_LINK.METADATA_ID.`in`(searchIds))
                .awaitInto<MediaLink>()
                .associateBy { checkNotNull(it.metadataId) }
        } else {
            emptyMap()
        }
        val playbackStateRecord = includePlaybackStateForUser
            ?.takeIf { episodeRecords.isNotEmpty() }
            ?.let { userId ->
                playbackStatesDao.findByUserIdAndMetadataIds(userId, episodeRecords.map(Metadata::id))
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
        val showRecord = metadataDao.find(checkNotNull(episodeRecord.rootId)) ?: return null
        val mediaLinks = if (includeLinks) {
            mediaLinkDao.findByMetadataId(episodeId)
        } else {
            emptyList()
        }
        val playbackState = includePlaybackStateForUser?.let { userId ->
            playbackStatesDao.findByUserIdAndMetadataId(userId, episodeId)
        }
        val streamEncodings = mediaLinkDao.findStreamEncodings(mediaLinks.map(MediaLink::id))
        val (cast, crew)  = tagsDao.findCastAndCrewForMetadata(listOf(showRecord.id, episodeId))
        return EpisodeResponse(
            episode = episodeRecord.toTvEpisodeModel(),
            show = showRecord.toTvShowModel(),
            cast = cast,
            crew = crew,
            mediaLinks = mediaLinks,
            playbackState = playbackState,
            streamEncodings = streamEncodings,
        )
    }

    suspend fun findCurrentlyWatching(userId: String, limit: Int): CurrentlyWatchingQueryResults {
        val playbackStates = playbackStatesDao.findWithUniqueRootByUserId(userId, limit)
            .associateBy(PlaybackState::metadataId)

        if (playbackStates.isEmpty()) {
            return CurrentlyWatchingQueryResults(emptyList(), emptyMap(), emptyMap())
        }

        val playbackMetadataIds = playbackStates.keys.toList()

        val movies = metadataDao.findAllByIdsAndType(playbackMetadataIds, MediaType.MOVIE)
            .map(Metadata::toMovieModel)

        val episodes = metadataDao.findAllByIdsAndType(playbackMetadataIds, MediaType.TV_EPISODE)
            .map(Metadata::toTvEpisodeModel)

        val tvShowIds = episodes.map(Episode::showId)
        val tvShows = if (tvShowIds.isNotEmpty()) {
            metadataDao.findAllByIdsAndType(tvShowIds, MediaType.TV_SHOW)
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
        return db.select(METADATA.asterisk(), MEDIA_LINK.asterisk())
            .from(METADATA)
            .join(MEDIA_LINK)
            .on(
                METADATA.ID.eq(MEDIA_LINK.METADATA_ID)
                    .and(MEDIA_LINK.DESCRIPTOR.eq(Descriptor.VIDEO))
            )
            .where(METADATA.MEDIA_TYPE.eq(MediaType.MOVIE))
            .orderBy(METADATA.CREATED_AT.desc())
            .limit(limit)
            .fetchAsync()
            .thenApplyAsync { result ->
                result.intoMap(
                    { it.into(METADATA).into(Metadata::class.java).toMovieModel() },
                    { it.into(MEDIA_LINK).into(MediaLink::class.java) }
                )
            }
            .await()
    }

    suspend fun findRecentlyAddedTv(limit: Int): List<TvShow> {
        return metadataDao.findByType(MediaType.TV_SHOW, limit).map(Metadata::toTvShowModel)
    }

    suspend fun findMediaRefByFilePath(path: String): MediaLink? {
        return mediaLinkDao.findByFilePath(path)
    }

    suspend fun findMediaLinksByMetadataId(metadataId: String): List<MediaLink> {
        return mediaLinkDao.findByMetadataId(metadataId)
    }

    suspend fun findMediaLinksByMetadataIds(metadataIds: List<String>): List<MediaLink> {
        return mediaLinkDao.findByMetadataIds(metadataIds)
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
        return metadataDao.findAllByParentIdAndType(tvShowId, MediaType.TV_SEASON)
    }

    suspend fun findTvShowsByTmdbId(tmdbIds: List<Int>): List<TvShow> {
        if (tmdbIds.isEmpty()) return emptyList()
        return metadataDao.findAllByTmdbIdsAndType(tmdbIds, MediaType.TV_SHOW).map(Metadata::toTvShowModel)
    }

    suspend fun findEpisodesByShow(showId: String, seasonNumber: Int? = null): List<Episode> {
        return if (seasonNumber == null) {
            metadataDao.findAllByRootIdAndType(showId, MediaType.TV_EPISODE)
        } else {
            metadataDao.findAllByRootIdAndParentIndexAndType(showId, seasonNumber, MediaType.TV_EPISODE)
        }.map(Metadata::toTvEpisodeModel)
    }

    suspend fun findMediaById(metadataId: String): FindMediaResult {
        val mediaRecord = metadataDao.find(metadataId) ?: return FindMediaResult()
        return when (mediaRecord.mediaType) {
            MediaType.MOVIE -> FindMediaResult(movie = mediaRecord.toMovieModel())
            MediaType.TV_SHOW -> FindMediaResult(tvShow = mediaRecord.toTvShowModel())
            MediaType.TV_EPISODE -> FindMediaResult(episode = mediaRecord.toTvEpisodeModel())
            MediaType.TV_SEASON -> FindMediaResult(season = mediaRecord.toTvSeasonModel())
        }
    }

    suspend fun insertMovie(
        movie: Movie,
        genres: List<Genre>,
        companies: List<ProductionCompany>,
    ): Metadata {
        val movieRecord = movie.toMetadataDb()
        val id = metadataDao.insertMetadata(movieRecord)
        insertGenres(id, genres)
        insertCompanies(id, companies)
        return movieRecord.copy(id = id)
    }

    suspend fun insertGenres(metadataId: String, genres: List<Genre>) {
        genres.onEach { genre ->
            val dbGenre = if (genre.id.isBlank()) {
                genre.tmdbId?.let { tagsDao.findGenreByTmdbId(it) }
                    ?: genre.copy(id = tagsDao.insertTag(genre.name, TagType.GENRE, genre.tmdbId))
            } else {
                genre
            }
            tagsDao.insertMetadataGenreLink(metadataId, dbGenre.id)
        }
    }

    suspend fun insertCompanies(metadataId: String, companies: List<ProductionCompany>) {
        companies.onEach { company ->
            val dbCompany = if (company.id.isBlank()) {
                company.tmdbId?.let { tagsDao.findCompanyByTmdbId(it) }
                    ?: company.copy(id = tagsDao.insertTag(company.name, TagType.COMPANY, company.tmdbId))
            } else {
                company
            }
            tagsDao.insertMetadataCompanyLink(metadataId, dbCompany.id)
        }
    }

    suspend fun insertCredits(
        metadataId: String,
        credits: Map<Person, List<MetadataCredit>>
    ): Map<Person, List<MetadataCredit>> {
        if (credits.isEmpty()) {
            return emptyMap()
        }
        val dbCredits = credits.map { (person, credits) ->
            val dbPerson = if (person.id.isBlank()) {
                person.tmdbId?.let { tagsDao.findPersonByTmdbId(it) }
                    ?: person.copy(id = tagsDao.insertTag(person.name, TagType.PERSON, person.tmdbId))
            } else {
                person
            }
            dbPerson to credits.map { credit ->
                credit.copy(
                    metadataId = metadataId,
                    personId = dbPerson.id,
                )
            }
        }.toMap()
        tagsDao.insertCredits(dbCredits.values.flatten())
        return dbCredits
    }

    suspend fun insertTvShow(
        tvShow: Metadata,
        tvSeasons: List<Metadata>,
        episodes: List<Metadata>,
    ) {
        metadataDao.insertMetadata(listOf(tvShow) + tvSeasons + episodes)
    }

    /**
     * Update an existing movie's metadata and tags.
     * This method is used for refreshing movie metadata from the provider.
     * @param movie The updated movie data
     * @param genres The updated genre list
     * @param companies The updated production company list
     * @return The updated metadata record
     */
    suspend fun updateMovie(
        movie: Movie,
        genres: List<Genre>,
        companies: List<ProductionCompany>,
    ): Metadata {
        val movieRecord = movie.toMetadataDb()
        metadataDao.updateMetadata(movieRecord)

        // Refresh tags by deleting old and inserting new
        tagsDao.deleteGenresForMetadata(movieRecord.id)
        tagsDao.deleteCompaniesForMetadata(movieRecord.id)

        insertGenres(movieRecord.id, genres)
        insertCompanies(movieRecord.id, companies)

        return movieRecord
    }

    /**
     * Refresh credits for a metadata record.
     * Deletes existing credits and inserts new ones.
     */
    suspend fun refreshCredits(
        metadataId: String,
        credits: Map<Person, List<MetadataCredit>>
    ): Map<Person, List<MetadataCredit>> {
        tagsDao.deleteCreditsForMetadata(metadataId)
        return insertCredits(metadataId, credits)
    }

    /**
     * Update an existing TV show's metadata, seasons, and episodes.
     * This method is used for refreshing TV show metadata from the provider.
     * Existing seasons/episodes are updated, new ones are inserted.
     * @param tvShow The updated TV show metadata
     * @param tvSeasons The updated season list
     * @param episodes The updated episode list
     */
    suspend fun updateTvShow(
        tvShow: Metadata,
        tvSeasons: List<Metadata>,
        episodes: List<Metadata>,
    ) {
        // Update the show itself
        metadataDao.updateMetadata(tvShow)

        // Refresh tags
        tagsDao.deleteGenresForMetadata(tvShow.id)
        tagsDao.deleteCompaniesForMetadata(tvShow.id)

        // Upsert seasons and episodes (update existing, insert new)
        (tvSeasons + episodes).forEach { metadata ->
            metadataDao.upsertMetadata(metadata)
        }
    }

    /**
     * Delete a movie and all its associated data.
     * Deletes in order: media links, tags (credits/genres/companies), searchable content, metadata.
     * @return A DeleteResult with details of what was deleted.
     */
    suspend fun deleteMovie(metadataId: String): DeleteResult {
        return db.transactionDelete("movie", metadataId) {
            val linksDeleted = mediaLinkDao.deleteByMetadataId(metadataId)
            val tagsDeleted = tagsDao.deleteAllTagsForMetadata(metadataId)
            val searchDeleted = searchableContentDao.deleteById(metadataId)
            val metadataDeleted = metadataDao.deleteById(metadataId)

            logger.info(
                "Deleted movie {}: {} links, {} tags, {} search entries, {} metadata",
                metadataId, linksDeleted, tagsDeleted.values.sum(), searchDeleted, metadataDeleted
            )

            mapOf(
                "mediaLinks" to linksDeleted,
                "credits" to (tagsDeleted["credits"] ?: 0),
                "genres" to (tagsDeleted["genres"] ?: 0),
                "companies" to (tagsDeleted["companies"] ?: 0),
                "searchableContent" to searchDeleted,
                "metadata" to metadataDeleted
            )
        }
    }

    /**
     * Delete a TV show and all its associated data (seasons, episodes, links, tags, etc.).
     * Deletes in order: media links, tags (credits/genres/companies), searchable content, metadata.
     * @return A DeleteResult with details of what was deleted.
     */
    suspend fun deleteTvShow(metadataId: String): DeleteResult {
        return db.transactionDelete("tvShow", metadataId) {
            // Get all metadata IDs (seasons + episodes) that belong to this show
            val childMetadataIds = metadataDao.findAllIdsByRootId(metadataId)
            val allMetadataIds = listOf(metadataId) + childMetadataIds

            logger.info(
                "Deleting TV show {} with {} seasons/episodes",
                metadataId, childMetadataIds.size
            )

            // Delete media links for all metadata (show, seasons, episodes)
            val linksDeleted = mediaLinkDao.deleteByRootMetadataId(metadataId)

            // Delete tags for all metadata entries
            val tagsDeleted = tagsDao.deleteAllTagsForMetadataIds(allMetadataIds)

            // Delete searchable content for all metadata entries
            val searchDeleted = searchableContentDao.deleteByIds(allMetadataIds)

            // Delete child metadata (seasons, episodes) then the show itself
            val childrenDeleted = metadataDao.deleteByRootId(metadataId)
            val showDeleted = metadataDao.deleteById(metadataId)

            logger.info(
                "Deleted TV show {}: {} links, {} tags, {} search entries, {} metadata",
                metadataId, linksDeleted, tagsDeleted.values.sum(), searchDeleted, childrenDeleted + showDeleted
            )

            mapOf(
                "mediaLinks" to linksDeleted,
                "credits" to (tagsDeleted["credits"] ?: 0),
                "genres" to (tagsDeleted["genres"] ?: 0),
                "companies" to (tagsDeleted["companies"] ?: 0),
                "searchableContent" to searchDeleted,
                "metadata" to (childrenDeleted + showDeleted)
            )
        }
    }

    suspend fun deleteLinksByContentId(metadataId: String) {
        mediaLinkDao.deleteByMetadataId(metadataId)
    }

    suspend fun deleteLinksByRootContentId(metadataId: String) {
        mediaLinkDao.deleteByRootMetadataId(metadataId)
    }

    suspend fun findTvSeasonsByIds(tvSeasonIds: List<String>): List<Metadata> {
        if (tvSeasonIds.isEmpty()) return emptyList()
        return metadataDao.findAllByIdsAndType(tvSeasonIds, MediaType.TV_SEASON)
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
