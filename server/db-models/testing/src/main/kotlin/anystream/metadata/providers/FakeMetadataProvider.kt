/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.metadata.providers

import anystream.db.MetadataDao
import anystream.db.pojos.fromTvEpisode
import anystream.db.pojos.fromTvSeason
import anystream.db.pojos.toMetadataDb
import anystream.metadata.MetadataProvider
import anystream.models.*
import anystream.models.api.*
import anystream.util.ObjectId
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A fake metadata provider for testing purposes.
 *
 * This provider allows pre-populating test data and simulating various
 * error conditions without making real API calls.
 *
 * @param metadataDao Optional DAO for persisting metadata to the database.
 *                    If provided, imported metadata will be inserted into the database.
 *                    If null, metadata is only returned (useful for search-only tests).
 */
class FakeMetadataProvider(
    private val metadataDao: MetadataDao? = null,
    private val movies: MutableMap<Int, FakeMovie> = mutableMapOf(),
    private val tvShows: MutableMap<Int, FakeTvShow> = mutableMapOf(),
) : MetadataProvider {

    override val id: String = "fake"

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE, MediaKind.TV)

    // Error injection controls
    var shouldFailSearch: Boolean = false
    var shouldFailImport: Boolean = false
    var searchDelay: Duration = Duration.ZERO
    var importDelay: Duration = Duration.ZERO
    var searchErrorMessage: String = "Simulated search failure"
    var importErrorMessage: String = "Simulated import failure"

    // Tracking for verification
    private val _searchCalls = mutableListOf<QueryMetadata>()
    val searchCalls: List<QueryMetadata> get() = _searchCalls.toList()

    private val _importCalls = mutableListOf<ImportMetadata>()
    val importCalls: List<ImportMetadata> get() = _importCalls.toList()

    /**
     * Add a movie to the fake provider's data store.
     */
    fun addMovie(
        tmdbId: Int,
        title: String,
        year: Int,
        overview: String = "Test overview for $title",
        runtime: Duration = 120.minutes,
        imdbId: String? = null,
    ): FakeMovie {
        val movie = FakeMovie(
            tmdbId = tmdbId,
            title = title,
            year = year,
            overview = overview,
            runtime = runtime,
            imdbId = imdbId,
        )
        movies[tmdbId] = movie
        return movie
    }

    /**
     * Add a TV show to the fake provider's data store.
     */
    fun addTvShow(
        tmdbId: Int,
        name: String,
        year: Int,
        overview: String = "Test overview for $name",
        seasons: List<FakeSeason> = emptyList(),
    ): FakeTvShow {
        val tvShow = FakeTvShow(
            tmdbId = tmdbId,
            name = name,
            year = year,
            overview = overview,
            seasons = seasons.toMutableList(),
        )
        tvShows[tmdbId] = tvShow
        return tvShow
    }

    /**
     * Add a season to an existing TV show.
     */
    fun addSeason(
        showTmdbId: Int,
        seasonNumber: Int,
        name: String = "Season $seasonNumber",
        episodes: List<FakeEpisode> = emptyList(),
    ): FakeSeason {
        val show = tvShows[showTmdbId]
            ?: throw IllegalArgumentException("TV show with tmdbId $showTmdbId not found")
        val season = FakeSeason(
            tmdbId = showTmdbId * 1000 + seasonNumber,
            seasonNumber = seasonNumber,
            name = name,
            episodes = episodes.toMutableList(),
        )
        show.seasons.add(season)
        return season
    }

    /**
     * Add an episode to an existing season.
     */
    fun addEpisode(
        showTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        name: String = "Episode $episodeNumber",
        overview: String = "Test overview for episode $episodeNumber",
    ): FakeEpisode {
        val show = tvShows[showTmdbId]
            ?: throw IllegalArgumentException("TV show with tmdbId $showTmdbId not found")
        val season = show.seasons.find { it.seasonNumber == seasonNumber }
            ?: throw IllegalArgumentException("Season $seasonNumber not found for show $showTmdbId")
        val episode = FakeEpisode(
            tmdbId = showTmdbId * 100000 + seasonNumber * 1000 + episodeNumber,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            name = name,
            overview = overview,
        )
        season.episodes.add(episode)
        return episode
    }

    /**
     * Clear all data and reset error injection state.
     */
    fun reset() {
        movies.clear()
        tvShows.clear()
        _searchCalls.clear()
        _importCalls.clear()
        shouldFailSearch = false
        shouldFailImport = false
        searchDelay = Duration.ZERO
        importDelay = Duration.ZERO
        searchErrorMessage = "Simulated search failure"
        importErrorMessage = "Simulated import failure"
    }

    override suspend fun search(request: QueryMetadata): QueryMetadataResult {
        _searchCalls.add(request)

        if (searchDelay > Duration.ZERO) {
            delay(searchDelay)
        }

        if (shouldFailSearch) {
            return QueryMetadataResult.ErrorDataProviderException(searchErrorMessage)
        }

        return when (request.mediaKind) {
            MediaKind.MOVIE -> searchMovies(request)
            MediaKind.TV -> searchTvShows(request)
            else -> error("Unsupported MediaKind: ${request.mediaKind}")
        }
    }

    override suspend fun importMetadata(request: ImportMetadata): List<ImportMetadataResult> {
        _importCalls.add(request)

        if (importDelay > Duration.ZERO) {
            delay(importDelay)
        }

        if (shouldFailImport) {
            return request.metadataIds.map {
                ImportMetadataResult.ErrorDataProviderException(importErrorMessage)
            }
        }

        return when (request.mediaKind) {
            MediaKind.MOVIE -> request.metadataIds.map { tmdbIdStr ->
                importMovie(tmdbIdStr.toInt())
            }
            MediaKind.TV -> request.metadataIds.map { tmdbIdStr ->
                importTvShow(tmdbIdStr.toInt())
            }
            else -> error("Unsupported MediaKind: ${request.mediaKind}")
        }
    }

    private fun searchMovies(request: QueryMetadata): QueryMetadataResult {
        val query = request.query?.lowercase()
        val year = request.year
        val metadataId = request.metadataId?.toIntOrNull()

        val matchingMovies = when {
            metadataId != null -> movies.values.filter { it.tmdbId == metadataId }
            query != null -> movies.values.filter { movie ->
                movie.title.lowercase().contains(query) &&
                    (year == null || movie.year == year)
            }
            else -> emptyList()
        }

        val results = matchingMovies
            .run { if (request.firstResultOnly) take(1) else this }
            .map { movie -> movie.toMetadataMatch() }

        return QueryMetadataResult.Success(id, results, request.extras)
    }

    private fun searchTvShows(request: QueryMetadata): QueryMetadataResult {
        val query = request.query?.lowercase()
        val year = request.year
        val metadataId = request.metadataId?.toIntOrNull()
        val extras = request.extras?.asTvShowExtras()

        val matchingShows = when {
            metadataId != null -> tvShows.values.filter { it.tmdbId == metadataId }
            query != null -> tvShows.values.filter { show ->
                show.name.lowercase().contains(query) &&
                    (year == null || show.year == year)
            }
            else -> emptyList()
        }

        val results = matchingShows
            .run { if (request.firstResultOnly) take(1) else this }
            .map { show -> show.toMetadataMatch(extras) }

        return QueryMetadataResult.Success(id, results, request.extras)
    }

    private suspend fun importMovie(tmdbId: Int): ImportMetadataResult {
        val fakeMovie = movies[tmdbId]
            ?: return ImportMetadataResult.ErrorDataProviderException("Movie with tmdbId $tmdbId not found")

        // Check for existing metadata by tmdbId
        val existingMovieMetadata = metadataDao?.findByTmdbIdAndType(tmdbId, MediaType.MOVIE)
        if (existingMovieMetadata != null) {
            return ImportMetadataResult.ErrorMetadataAlreadyExists(
                existingMediaId = existingMovieMetadata.id,
                match = MetadataMatch.MovieMatch(
                    remoteMetadataId = tmdbId.toString(),
                    remoteId = "fake:movie:$tmdbId",
                    exists = true,
                    movie = existingMovieMetadata.toMovieModel(),
                    providerId = id,
                ),
            )
        }

        val movieId = ObjectId.next()
        val movie = fakeMovie.toMovie(movieId)

        // Insert into database if dao is provided
        metadataDao?.insertMetadata(movie.toMetadataDb())

        return ImportMetadataResult.Success(
            match = MetadataMatch.MovieMatch(
                remoteMetadataId = tmdbId.toString(),
                remoteId = "fake:movie:$tmdbId",
                exists = true,
                movie = movie,
                providerId = id,
            ),
        )
    }

    private suspend fun importTvShow(tmdbId: Int): ImportMetadataResult {
        val fakeShow = tvShows[tmdbId]
            ?: return ImportMetadataResult.ErrorDataProviderException("TV show with tmdbId $tmdbId not found")

        // Check for existing metadata by tmdbId
        val existingShowMetadata = metadataDao?.findByTmdbIdAndType(tmdbId, MediaType.TV_SHOW)
        if (existingShowMetadata != null) {
            // Metadata already exists, return it
            val existingSeasons = metadataDao.findAllByParentIdAndType(existingShowMetadata.id, MediaType.TV_SEASON)
            val existingEpisodes = metadataDao.findAllByRootIdAndType(existingShowMetadata.id, MediaType.TV_EPISODE)
            return ImportMetadataResult.ErrorMetadataAlreadyExists(
                existingMediaId = existingShowMetadata.id,
                match = MetadataMatch.TvShowMatch(
                    remoteMetadataId = tmdbId.toString(),
                    remoteId = "fake:tv:$tmdbId",
                    exists = true,
                    tvShow = existingShowMetadata.toTvShowModel(),
                    seasons = existingSeasons.map { it.toTvSeasonModel() },
                    episodes = existingEpisodes.map { it.toTvEpisodeModel() },
                    providerId = id,
                ),
            )
        }

        val showId = ObjectId.next()
        val (tvShow, seasons, episodes) = fakeShow.toTvShowModels(showId)

        // Insert into database if dao is provided
        metadataDao?.let { dao ->
            val tvShowMetadata = tvShow.toMetadataDb()
            val seasonMetadataList = seasons.map { it.fromTvSeason(tvShowMetadata) }
            val seasonMetadataMap = seasonMetadataList.associateBy { it.id }
            val episodeMetadataList = episodes.map { episode ->
                val seasonMetadata = seasonMetadataMap[episode.seasonId]!!
                episode.fromTvEpisode(tvShowMetadata, seasonMetadata)
            }
            dao.insertMetadata(listOf(tvShowMetadata) + seasonMetadataList + episodeMetadataList)
        }

        return ImportMetadataResult.Success(
            match = MetadataMatch.TvShowMatch(
                remoteMetadataId = tmdbId.toString(),
                remoteId = "fake:tv:$tmdbId",
                exists = true,
                tvShow = tvShow,
                seasons = seasons,
                episodes = episodes,
                providerId = id,
            ),
        )
    }

    private fun FakeMovie.toMovie(movieId: String): Movie {
        return Movie(
            id = movieId,
            title = title,
            overview = overview,
            tmdbId = tmdbId,
            imdbId = imdbId,
            runtime = runtime,
            releaseDate = Instant.fromEpochSeconds(
                (year - 1970L) * 365 * 24 * 60 * 60
            ),
            createdAt = Clock.System.now(),
            contentRating = null,
        )
    }

    private fun FakeMovie.toMetadataMatch(): MetadataMatch.MovieMatch {
        val remoteId = "fake:movie:$tmdbId"
        return MetadataMatch.MovieMatch(
            remoteMetadataId = tmdbId.toString(),
            remoteId = remoteId,
            exists = false,
            movie = toMovie(remoteId),
            providerId = id,
        )
    }

    private fun FakeTvShow.toTvShowModels(showId: String): Triple<TvShow, List<TvSeason>, List<Episode>> {
        val tvShow = TvShow(
            id = showId,
            name = name,
            tmdbId = tmdbId,
            overview = overview,
            firstAirDate = Instant.fromEpochSeconds(
                (year - 1970L) * 365 * 24 * 60 * 60
            ),
            createdAt = Clock.System.now(),
        )

        val tvSeasons = seasons.map { season ->
            val seasonId = ObjectId.next()
            TvSeason(
                id = seasonId,
                name = season.name,
                overview = "",
                seasonNumber = season.seasonNumber,
                airDate = tvShow.firstAirDate,
                tmdbId = season.tmdbId,
            )
        }

        val episodes = seasons.flatMap { season ->
            val seasonModel = tvSeasons.find { it.seasonNumber == season.seasonNumber }!!
            season.episodes.map { episode ->
                Episode(
                    id = ObjectId.next(),
                    showId = showId,
                    seasonId = seasonModel.id,
                    name = episode.name,
                    tmdbId = episode.tmdbId,
                    overview = episode.overview,
                    airDate = tvShow.firstAirDate,
                    number = episode.episodeNumber,
                    seasonNumber = episode.seasonNumber,
                    tmdbRating = null,
                )
            }
        }

        return Triple(tvShow, tvSeasons, episodes)
    }

    private fun FakeTvShow.toMetadataMatch(extras: QueryMetadata.Extras.TvShowExtras? = null): MetadataMatch.TvShowMatch {
        val remoteId = "fake:tv:$tmdbId"
        val showId = remoteId

        val tvShow = TvShow(
            id = showId,
            name = name,
            tmdbId = tmdbId,
            overview = overview,
            firstAirDate = Instant.fromEpochSeconds(
                (year - 1970L) * 365 * 24 * 60 * 60
            ),
            createdAt = Clock.System.now(),
        )

        val filteredSeasons = if (extras?.seasonNumber != null) {
            seasons.filter { it.seasonNumber == extras.seasonNumber }
        } else {
            seasons
        }

        val tvSeasons = filteredSeasons.map { season ->
            TvSeason(
                id = "$remoteId:season:${season.seasonNumber}",
                name = season.name,
                overview = "",
                seasonNumber = season.seasonNumber,
                airDate = tvShow.firstAirDate,
                tmdbId = season.tmdbId,
            )
        }

        val episodes = filteredSeasons.flatMap { season ->
            val seasonId = "$remoteId:season:${season.seasonNumber}"
            val filteredEpisodes = if (extras?.episodeNumber != null) {
                season.episodes.filter { it.episodeNumber == extras.episodeNumber }
            } else {
                season.episodes
            }
            filteredEpisodes.map { episode ->
                Episode(
                    id = "$remoteId:episode:${season.seasonNumber}:${episode.episodeNumber}",
                    showId = showId,
                    seasonId = seasonId,
                    name = episode.name,
                    tmdbId = episode.tmdbId,
                    overview = episode.overview,
                    airDate = tvShow.firstAirDate,
                    number = episode.episodeNumber,
                    seasonNumber = episode.seasonNumber,
                    tmdbRating = null,
                )
            }
        }

        return MetadataMatch.TvShowMatch(
            remoteMetadataId = tmdbId.toString(),
            remoteId = remoteId,
            exists = false,
            tvShow = tvShow,
            seasons = tvSeasons,
            episodes = episodes,
            providerId = id,
        )
    }
}

/**
 * Represents a fake movie for testing.
 */
data class FakeMovie(
    val tmdbId: Int,
    val title: String,
    val year: Int,
    val overview: String,
    val runtime: Duration,
    val imdbId: String? = null,
)

/**
 * Represents a fake TV show for testing.
 */
data class FakeTvShow(
    val tmdbId: Int,
    val name: String,
    val year: Int,
    val overview: String,
    val seasons: MutableList<FakeSeason> = mutableListOf(),
)

/**
 * Represents a fake TV season for testing.
 */
data class FakeSeason(
    val tmdbId: Int,
    val seasonNumber: Int,
    val name: String,
    val episodes: MutableList<FakeEpisode> = mutableListOf(),
)

/**
 * Represents a fake TV episode for testing.
 */
data class FakeEpisode(
    val tmdbId: Int,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String,
)

/**
 * Helper function to create a FakeMetadataProvider with pre-populated data.
 *
 * @param metadataDao Optional DAO for persisting metadata to the database.
 *                    If provided, imported metadata will be inserted into the database.
 */
fun createFakeMetadataProvider(
    metadataDao: MetadataDao? = null,
    configure: FakeMetadataProvider.() -> Unit = {}
): FakeMetadataProvider {
    return FakeMetadataProvider(metadataDao).apply(configure)
}

/**
 * DSL helper for creating a TV show with seasons and episodes.
 */
fun FakeMetadataProvider.tvShow(
    tmdbId: Int,
    name: String,
    year: Int,
    overview: String = "Test overview for $name",
    configure: TvShowBuilder.() -> Unit = {}
): FakeTvShow {
    val show = addTvShow(tmdbId, name, year, overview)
    TvShowBuilder(this, tmdbId).apply(configure)
    return show
}

class TvShowBuilder(
    private val provider: FakeMetadataProvider,
    private val showTmdbId: Int,
) {
    fun season(
        seasonNumber: Int,
        name: String = "Season $seasonNumber",
        configure: SeasonBuilder.() -> Unit = {}
    ) {
        provider.addSeason(showTmdbId, seasonNumber, name)
        SeasonBuilder(provider, showTmdbId, seasonNumber).apply(configure)
    }
}

class SeasonBuilder(
    private val provider: FakeMetadataProvider,
    private val showTmdbId: Int,
    private val seasonNumber: Int,
) {
    fun episode(
        episodeNumber: Int,
        name: String = "Episode $episodeNumber",
        overview: String = "Test overview for episode $episodeNumber",
    ) {
        provider.addEpisode(showTmdbId, seasonNumber, episodeNumber, name, overview)
    }

    fun episodes(count: Int, nameTemplate: String = "Episode %d") {
        repeat(count) { index ->
            episode(index + 1, nameTemplate.format(index + 1))
        }
    }
}
