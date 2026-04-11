/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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

import anystream.data.MetadataDbQueries
import anystream.di.ServerScope
import anystream.metadata.ImageStore
import anystream.metadata.MetadataProvider
import anystream.models.*
import anystream.models.api.*
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import wire.client.WireApiClient
import wire.models.WireCastCredit
import wire.models.WireCoverType
import wire.models.WireCredits
import wire.models.WireCrewCredit
import wire.models.WireImage
import wire.models.WireMovie
import wire.models.WirePerson
import wire.models.WireRating
import wire.models.WireTvEpisode
import wire.models.WireTvSeason
import wire.models.WireTvShow
import wire.models.api.WireMovieSearchResult
import wire.models.api.WireTvShowSearchResult
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * [MetadataProvider] backed by the AnyStream Wire metadata service. Wire returns a movie or show's full
 * tree (seasons + episodes) in a single API call. Local [MetadataId]s use the wire server id directly,
 * so refreshes naturally update the same row.
 */
@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<MetadataProvider>(),
)
@Inject
class WireMetadataProvider(
    private val wireApi: WireApiClient,
    private val queries: MetadataDbQueries,
    private val imageStore: ImageStore,
) : MetadataProvider {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val id: String = "wire"

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE, MediaKind.TV)

    private val importLocks = MutableStateFlow(emptyList<Pair<String, MediaKind>>())

    override suspend fun importMetadata(request: ImportMetadata): List<ImportMetadataResult> {
        return when (request.mediaKind) {
            MediaKind.MOVIE -> request.metadataIds.map { tmdbId ->
                importMovie(tmdbId.toInt(), request)
            }

            MediaKind.TV -> request.metadataIds.map { tmdbId ->
                importTvShow(tmdbId.toInt(), request)
            }

            else -> error("Unsupported MediaKind: ${request.mediaKind}")
        }
    }

    override suspend fun search(request: QueryMetadata): QueryMetadataResult {
        return when (request.mediaKind) {
            MediaKind.MOVIE -> searchMovie(request)
            MediaKind.TV -> searchTv(request)
            else -> error("Unsupported MediaKind: ${request.mediaKind}")
        }
    }

    // ---- Import -------------------------------------------------------------------------------------------

    private suspend fun importMovie(
        tmdbId: Int,
        request: ImportMetadata,
    ): ImportMetadataResult {
        val lockKey = "movie:$tmdbId"
        val lockData = lockKey to request.mediaKind
        awaitLockAndAcquire(lockData)
        logger.debug("Importing wire movie by tmdbId {}", tmdbId)

        val existingMovie = queries.findMovieByTmdbId(tmdbId)
        if (existingMovie != null && !request.refresh) {
            removeLock(lockData)
            return ImportMetadataResult.ErrorMetadataAlreadyExists(
                existingMediaId = existingMovie.id,
                match = MetadataMatch.MovieMatch(
                    remoteMetadataId = existingMovie.tmdbId.toString(),
                    remoteId = "wire:movie:${existingMovie.tmdbId}",
                    exists = true,
                    movie = existingMovie,
                    providerId = id,
                ),
            )
        }

        val wireMovie = try {
            wireApi.getMovie(tmdbId)
        } catch (e: Throwable) {
            removeLock(lockData)
            logger.error("Failed to fetch movie data from Wire", e)
            return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }

        val movieId = existingMovie?.id ?: wireMovie.toMetadataId()
        val movie = wireMovie.toMovie(movieId)

        return try {
            val genres = wireMovie.genres.toGenreDb()
            val companies = wireMovie.studio?.let { listOf(it.toCompanyDb()) }.orEmpty()
            val credits = wireMovie.credits.toCreditsDb()
            val metadata = queries.insertMovie(movie, genres, companies)
            val dbCredits = queries.insertCredits(metadata.id, credits)
            supervisorScope {
                wireMovie.images.forEach { image ->
                    image.toAnystreamImageType()?.let { type ->
                        launch { imageStore.cacheImage(movieId.value, type, image.url) }
                    }
                }

                val personImages = wireMovie.credits.collectPersonImages()
                dbCredits.forEach { (person, _) ->
                    val profileImageUrl = personImages[person.tmdbId] ?: return@forEach
                    val imagePath =
                        imageStore.getPersonImagePath(person.id.value).takeUnless(Path::exists)
                            ?: return@forEach
                    launch { imageStore.downloadInto(imagePath, profileImageUrl) }
                }
            }
            ImportMetadataResult.Success(
                match = MetadataMatch.MovieMatch(
                    remoteMetadataId = movie.tmdbId.toString(),
                    remoteId = "wire:movie:${movie.tmdbId}",
                    exists = true,
                    movie = movie,
                    providerId = id,
                ),
                subresults = emptyList(),
            )
        } catch (e: Throwable) {
            logger.error("Failed to insert wire movie metadata", e)
            ImportMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        } finally {
            removeLock(lockData)
        }
    }

    private suspend fun importTvShow(
        tmdbId: Int,
        request: ImportMetadata,
    ): ImportMetadataResult {
        val lockKey = "tv:$tmdbId"
        val lockData = lockKey to request.mediaKind
        awaitLockAndAcquire(lockData)
        logger.debug("Importing wire tv show by tmdbId {}", tmdbId)

        val existingTvShow = queries.findTvShowByTmdbId(tmdbId)
        if (existingTvShow != null && !request.refresh) {
            removeLock(lockData)
            val existingEpisodes = queries.findEpisodesByShow(existingTvShow.id)
            val existingSeasons = queries.findTvSeasonsByTvShowId(existingTvShow.id)
            return ImportMetadataResult.ErrorMetadataAlreadyExists(
                existingMediaId = existingTvShow.id,
                match = MetadataMatch.TvShowMatch(
                    remoteMetadataId = existingTvShow.tmdbId.toString(),
                    remoteId = "wire:tv:${existingTvShow.tmdbId}",
                    exists = true,
                    tvShow = existingTvShow,
                    seasons = existingSeasons.map { it.toTvSeasonModel() },
                    episodes = existingEpisodes,
                    providerId = id,
                ),
            )
        }

        val wireShow = try {
            wireApi.getTvShow(tmdbId)
        } catch (e: Throwable) {
            removeLock(lockData)
            logger.error("Failed to fetch tv show data from Wire", e)
            return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }

        val tvShowId = existingTvShow?.id ?: wireShow.toMetadataId()
        val tvShowMetadata = wireShow.toTvShowMetadata(tvShowId)
        val seasons = wireShow.seasons
            // Drop "Season 0" specials.
            .filter { it.seasonNumber > 0 }
            .map { season -> season.toSeasonMetadata(season.toMetadataId(), tvShowId) }
        val seasonsByNumber = seasons.associateBy { checkNotNull(it.index) }
        val episodes = wireShow.seasons
            .filter { it.seasonNumber > 0 }
            .flatMap { season ->
                val seasonMetadata = seasonsByNumber[season.seasonNumber] ?: return@flatMap emptyList()
                season.episodes.map { episode ->
                    episode.toEpisodeMetadata(
                        id = episode.toMetadataId(),
                        showId = tvShowId,
                        seasonId = seasonMetadata.id,
                    )
                }
            }

        return try {
            queries.insertTvShow(tvShowMetadata, seasons, episodes)
            val showCredits = wireShow.credits.toCreditsDb()
            queries.insertCredits(tvShowId, showCredits)
            supervisorScope {
                wireShow.images.forEach { image ->
                    image.toAnystreamImageType()?.let { type ->
                        launch { imageStore.cacheImage(tvShowId.value, type, image.url) }
                    }
                }
                wireShow.seasons.forEach { season ->
                    val seasonId = seasonsByNumber[season.seasonNumber]?.id ?: return@forEach
                    season.images.forEach { image ->
                        image.toAnystreamImageType()?.let { type ->
                            launch {
                                imageStore.cacheImage(
                                    metadataId = seasonId.value,
                                    imageType = type,
                                    url = image.url,
                                    rootMetadataId = tvShowId.value,
                                )
                            }
                        }
                    }
                }
            }
            ImportMetadataResult.Success(
                match = MetadataMatch.TvShowMatch(
                    remoteMetadataId = wireShow.tmdbId.toString(),
                    remoteId = "wire:tv:${wireShow.tmdbId}",
                    exists = true,
                    tvShow = tvShowMetadata.toTvShowModel(),
                    seasons = seasons.map(Metadata::toTvSeasonModel),
                    episodes = episodes.map(Metadata::toTvEpisodeModel),
                    providerId = id,
                ),
            )
        } catch (e: Throwable) {
            logger.error("Failed to insert wire tv show data showId=$tvShowId", e)
            ImportMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        } finally {
            removeLock(lockData)
        }
    }

    // ---- Search -------------------------------------------------------------------------------------------

    private suspend fun searchMovie(request: QueryMetadata): QueryMetadataResult {
        val wireMovies = try {
            when {
                request.metadataId != null -> {
                    listOfNotNull(safeGetMovie(request.metadataId!!.toInt()))
                }

                request.query != null -> {
                    val response = wireApi.searchMovies(request.query!!)
                    val tmdbIds = response.movies
                        .run { if (request.firstResultOnly) take(1) else this }
                        .map(WireMovieSearchResult::tmdbId)
                    tmdbIds.mapNotNull { safeGetMovie(it) }
                }

                else -> {
                    error("No content")
                }
            }
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }

        val tmdbIds = wireMovies.map(WireMovie::tmdbId)
        val existingMovies = try {
            if (tmdbIds.isNotEmpty()) queries.findMoviesByTmdbId(tmdbIds) else emptyList()
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        }

        val matches = wireMovies.map { wireMovie ->
            val existingMovie = existingMovies.find { it.tmdbId == wireMovie.tmdbId }
            val movieId = existingMovie?.id ?: wireMovie.toMetadataId()
            MetadataMatch.MovieMatch(
                remoteMetadataId = wireMovie.tmdbId.toString(),
                remoteId = "wire:movie:${wireMovie.tmdbId}",
                exists = existingMovie != null,
                movie = wireMovie.toMovie(movieId),
                providerId = id,
            )
        }
        return QueryMetadataResult.Success(id, matches, request.extras)
    }

    private suspend fun searchTv(request: QueryMetadata): QueryMetadataResult {
        val tvShowExtras = request.extras?.asTvShowExtras()
        val wireShows = try {
            when {
                request.metadataId != null -> {
                    listOfNotNull(safeGetTvShow(request.metadataId!!.toInt()))
                }

                request.query != null -> {
                    val response = wireApi.searchTv(request.query!!)
                    val tmdbIds = response.tvShows
                        .run { if (request.firstResultOnly) take(1) else this }
                        .map(WireTvShowSearchResult::tmdbId)
                    tmdbIds.mapNotNull { safeGetTvShow(it) }
                }

                else -> {
                    error("No content")
                }
            }
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }

        if (wireShows.isEmpty()) {
            return QueryMetadataResult.Success(id, emptyList(), request.extras)
        }

        val tmdbIds = wireShows.map(WireTvShow::tmdbId)
        val existingShows = try {
            queries.findTvShowsByTmdbId(tmdbIds)
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        }

        val matches = wireShows.map { wireShow ->
            val existingShow = existingShows.find { it.tmdbId == wireShow.tmdbId }
            val tvShowId = existingShow?.id ?: wireShow.toMetadataId()
            val filteredSeasons = wireShow.seasons.filter { season ->
                season.seasonNumber > 0 && (
                    tvShowExtras?.seasonNumber == null ||
                        season.seasonNumber == tvShowExtras.seasonNumber
                )
            }
            val seasonMetadata = filteredSeasons.map { season ->
                season.toSeasonMetadata(season.toMetadataId(), tvShowId)
            }
            val seasonIdByNumber = filteredSeasons
                .zip(seasonMetadata)
                .associate { (wire, meta) -> wire.seasonNumber to meta.id }
            val episodeModels = filteredSeasons
                .flatMap { season ->
                    val seasonId = seasonIdByNumber[season.seasonNumber] ?: return@flatMap emptyList()
                    season.episodes
                        .filter { episode ->
                            tvShowExtras?.episodeNumber == null ||
                                episode.episodeNumber == tvShowExtras.episodeNumber
                        }.map { episode ->
                            episode.toEpisodeMetadata(
                                id = episode.toMetadataId(),
                                showId = tvShowId,
                                seasonId = seasonId,
                            )
                        }
                }.map(Metadata::toTvEpisodeModel)

            MetadataMatch.TvShowMatch(
                remoteMetadataId = wireShow.tmdbId.toString(),
                remoteId = "wire:tv:${wireShow.tmdbId}",
                exists = existingShow != null,
                tvShow = existingShow ?: wireShow.toTvShowMetadata(tvShowId).toTvShowModel(),
                seasons = seasonMetadata.map(Metadata::toTvSeasonModel),
                episodes = episodeModels,
                providerId = id,
            )
        }
        return QueryMetadataResult.Success(id, matches, request.extras)
    }

    private suspend fun safeGetMovie(tmdbId: Int): WireMovie? {
        return try {
            wireApi.getMovie(tmdbId)
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    private suspend fun safeGetTvShow(tmdbId: Int): WireTvShow? {
        return try {
            wireApi.getTvShow(tmdbId)
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    // ---- Lock helpers ------------------------------------------------------------------------------------

    private suspend fun awaitLockAndAcquire(lockData: Pair<String, MediaKind>) {
        importLocks.first { locks -> !locks.contains(lockData) }
        importLocks.update { it + lockData }
    }

    private fun removeLock(lockData: Pair<String, MediaKind>) {
        importLocks.update { it - lockData }
    }

    // ---- Wire model -> AnyStream model conversions -------------------------------------------------------

    private fun WireMovie.toMetadataId(): MetadataId = MetadataId(id.ifBlank { "wire:movie:$tmdbId" })

    private fun WireTvShow.toMetadataId(): MetadataId = MetadataId(id.ifBlank { "wire:tv:$tmdbId" })

    private fun WireTvSeason.toMetadataId(): MetadataId = MetadataId(id.ifBlank { "wire:tv:season:$tmdbId" })

    private fun WireTvEpisode.toMetadataId(): MetadataId = MetadataId(id.ifBlank { "wire:tv:episode:$tmdbId" })

    private fun WireMovie.toMovie(movieId: MetadataId): Movie {
        return Movie(
            id = movieId,
            title = title,
            overview = overview,
            tagline = tagline,
            tmdbId = tmdbId,
            imdbId = imdbId,
            runtime = runtime ?: Duration.ZERO,
            releaseDate = premiere ?: theatricalRelease ?: digitalRelease ?: physicalRelease,
            createdAt = Clock.System.now(),
            tmdbRating = ratings.tmdbRatingPercent(),
            contentRating = certifications.firstOrNull { it.country == "US" }?.certification
                ?: certifications.firstOrNull()?.certification,
        )
    }

    private fun WireTvShow.toTvShowMetadata(showId: MetadataId): Metadata {
        val now = Clock.System.now()
        return Metadata(
            id = showId,
            tmdbId = tmdbId,
            title = title,
            overview = overview,
            firstAvailableAt = firstAirDate,
            createdAt = now,
            updatedAt = now,
            tmdbRating = ratings.tmdbRatingPercent(),
            contentRating = certifications.firstOrNull { it.country == "US" }?.certification
                ?: certifications.firstOrNull()?.certification,
            mediaKind = MediaKind.TV,
            mediaType = MediaType.TV_SHOW,
        )
    }

    private fun WireTvSeason.toSeasonMetadata(
        seasonId: MetadataId,
        showId: MetadataId,
    ): Metadata {
        val now = Clock.System.now()
        return Metadata(
            id = seasonId,
            parentId = showId,
            rootId = showId,
            tmdbId = tmdbId,
            title = title ?: "Season $seasonNumber",
            overview = overview.orEmpty(),
            index = seasonNumber,
            firstAvailableAt = airDate,
            createdAt = now,
            updatedAt = now,
            mediaKind = MediaKind.TV,
            mediaType = MediaType.TV_SEASON,
        )
    }

    private fun WireTvEpisode.toEpisodeMetadata(
        id: MetadataId,
        showId: MetadataId,
        seasonId: MetadataId,
    ): Metadata {
        val now = Clock.System.now()
        return Metadata(
            id = id,
            parentId = seasonId,
            rootId = showId,
            tmdbId = tmdbId,
            title = title,
            overview = overview.orEmpty(),
            firstAvailableAt = airDate,
            index = episodeNumber,
            parentIndex = seasonNumber,
            tmdbRating = ratings.tmdbRatingPercent(),
            createdAt = now,
            updatedAt = now,
            mediaKind = MediaKind.TV,
            mediaType = MediaType.TV_EPISODE,
        )
    }

    private fun List<String>.toGenreDb(): List<Genre> {
        return map { name -> Genre(id = TagId(""), name = name, tmdbId = null) }
    }

    private fun String.toCompanyDb(): ProductionCompany {
        return ProductionCompany(id = TagId(""), name = this, tmdbId = null)
    }

    private fun WireCredits.toCreditsDb(): Map<Person, List<MetadataCredit>> {
        return buildMap(cast.size + crew.size) {
            cast.forEach { castCredit ->
                val person = castCredit.person?.toPerson() ?: return@forEach
                val credit = MetadataCredit(
                    personId = TagId(""),
                    metadataId = MetadataId(""),
                    type = CreditType.CAST,
                    character = castCredit.character,
                    order = castCredit.order,
                    job = null,
                )
                merge(person, listOf(credit)) { existing, new -> existing + new }
            }
            crew.forEach { crewCredit ->
                val person = crewCredit.person?.toPerson() ?: return@forEach
                val job = crewCredit.toCreditJob() ?: return@forEach
                val credit = MetadataCredit(
                    personId = TagId(""),
                    metadataId = MetadataId(""),
                    type = CreditType.CREW,
                    character = null,
                    order = null,
                    job = job,
                )
                merge(person, listOf(credit)) { existing, new -> existing + new }
            }
        }
    }

    private fun WirePerson.toPerson(): Person {
        return Person(id = TagId(""), name = name, tmdbId = tmdbId)
    }

    private fun WireCrewCredit.toCreditJob(): CreditJob? {
        return try {
            CreditJob.valueOf(job.uppercase())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun WireCredits.collectPersonImages(): Map<Int, String> {
        val output = mutableMapOf<Int, String>()
        cast.forEach { it.collectInto(output) }
        crew.forEach { it.collectInto(output) }
        return output
    }

    private fun WireCastCredit.collectInto(into: MutableMap<Int, String>) {
        val person = person ?: return
        val image = person.images.firstOrNull()?.url ?: return
        into.putIfAbsent(person.tmdbId, image)
    }

    private fun WireCrewCredit.collectInto(into: MutableMap<Int, String>) {
        val person = person ?: return
        val image = person.images.firstOrNull()?.url ?: return
        into.putIfAbsent(person.tmdbId, image)
    }

    private fun WireImage.toAnystreamImageType(): String? {
        return when (coverType) {
            WireCoverType.Poster -> "poster"

            WireCoverType.Fanart -> "backdrop"

            WireCoverType.Banner,
            WireCoverType.Headshot,
            WireCoverType.Screenshot,
            -> null
        }
    }

    private fun List<WireRating>.tmdbRatingPercent(): Int? {
        val tmdbRating = firstOrNull { it.origin.equals("tmdb", ignoreCase = true) } ?: return null
        return (tmdbRating.value * 10).toInt()
    }
}
