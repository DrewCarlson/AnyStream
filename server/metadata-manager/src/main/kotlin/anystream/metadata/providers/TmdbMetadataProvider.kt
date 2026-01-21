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
package anystream.metadata.providers

import anystream.data.*
import anystream.metadata.ImageStore
import anystream.metadata.MetadataProvider
import anystream.models.*
import anystream.models.api.*
import anystream.util.ObjectId
import anystream.util.toRemoteId
import app.moviebase.tmdb.Tmdb3
import app.moviebase.tmdb.model.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.nio.file.Path
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.orEmpty
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

private const val IMAGE_URL = "https://image.tmdb.org/t/p"

class TmdbMetadataProvider(
    private val tmdbApi: Tmdb3,
    private val queries: MetadataDbQueries,
    private val imageStore: ImageStore,
) : MetadataProvider {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val id: String = "tmdb"

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE, MediaKind.TV)

    private val importLocks = MutableStateFlow(emptyList<Pair<Int, MediaKind>>())

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

    private suspend fun importMovie(tmdbId: Int, request: ImportMetadata): ImportMetadataResult {
        val lockData = Pair(tmdbId, request.mediaKind)
        awaitLockAndAcquire(lockData)
        logger.debug("Importing movie by id {}", tmdbId)
        val existingMovie = queries.findMovieByTmdbId(tmdbId)
        if (existingMovie != null && !request.refresh) {
            removeLock(lockData)
            return ImportMetadataResult.ErrorMetadataAlreadyExists(
                existingMediaId = existingMovie.id,
                match = MetadataMatch.MovieMatch(
                    remoteMetadataId = existingMovie.tmdbId.toString(),
                    remoteId = "tmdb:movie:${existingMovie.tmdbId}",
                    exists = true,
                    movie = existingMovie,
                    providerId = this@TmdbMetadataProvider.id,
                ),
            )
        }

        if (existingMovie == null) {
            logger.debug("Importing metadata record for {}", tmdbId)
        } else {
            logger.debug("Refreshing metadata record for {}", existingMovie.id)
        }
        val movieId = existingMovie?.id ?: ObjectId.next()
        val tmdbMovie = try {
            checkNotNull(fetchMovie(tmdbId))
        } catch (e: Throwable) {
            removeLock(lockData)
            logger.error("Failed to fetch data from Tmdb", e)
            return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val movie = tmdbMovie.asMovie(movieId)

        val isRefresh = existingMovie != null

        return try {
            val genres = tmdbMovie.genres.toGenreDb()
            val companies = tmdbMovie.productionCompanies.orEmpty().toCompanyDb()
            val credits = tmdbMovie.credits?.toCreditsDb().orEmpty()

            val metadata = if (isRefresh) {
                queries.updateMovie(movie, genres, companies)
            } else {
                queries.insertMovie(movie, genres, companies)
            }

            val dbCredits = if (isRefresh) {
                queries.refreshCredits(metadata.id, credits)
            } else {
                queries.insertCredits(metadata.id, credits)
            }
            supervisorScope {
                launch { tmdbMovie.cacheImage(movieId, "poster", "w300") }
                launch { tmdbMovie.cacheImage(movieId, "backdrop", "w1280") }

                val crew = tmdbMovie.credits?.crew.orEmpty()
                val cast = tmdbMovie.credits?.cast.orEmpty()
                val allPeople = (crew + cast).associate { it.id to it.profileImage?.path }
                dbCredits
                    .mapNotNull { (person, _) ->
                        val profileImagePath = allPeople[person.tmdbId]
                        val imagePath = imageStore.getPersonImagePath(person.id).takeUnless(Path::exists)
                        if (profileImagePath == null || imagePath == null) return@mapNotNull null
                        async {
                            imageStore.downloadInto(imagePath, "$IMAGE_URL/w276_and_h350_face$profileImagePath")
                        }
                    }
                    .awaitAll()
            }
            ImportMetadataResult.Success(
                match = MetadataMatch.MovieMatch(
                    remoteMetadataId = movie.tmdbId.toString(),
                    remoteId = tmdbMovie.toRemoteId(),
                    exists = true,
                    movie = movie,
                    providerId = this@TmdbMetadataProvider.id,
                ),
                subresults = emptyList(),
            )
        } catch (e: Throwable) {
            removeLock(lockData)
            val operation = if (isRefresh) "refresh" else "insert"
            logger.error("Failed to {} movie metadata", operation, e)
            ImportMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        }
    }

    suspend fun TmdbMovieDetail.cacheImage(metadataId: String, imageType: String, size: String) {
        val path = when (imageType) {
            "backdrop" -> backdropPath
            "poster" -> posterPath
            else -> null
        }
        if (path != null) {
            imageStore.cacheImage(metadataId, imageType, "$IMAGE_URL/$size$path")
        }
    }

    private suspend fun importTvShow(tmdbId: Int, request: ImportMetadata): ImportMetadataResult {
        val lockData = Pair(tmdbId, request.mediaKind)
        awaitLockAndAcquire(lockData)

        val existingTvShow = queries.findTvShowByTmdbId(tmdbId)

        if (existingTvShow != null && !request.refresh) {
            removeLock(lockData)
            val existingEpisodes = queries.findEpisodesByShow(existingTvShow.id)
            val existingSeasons = queries.findTvSeasonsByTvShowId(existingTvShow.id)
            return ImportMetadataResult.ErrorMetadataAlreadyExists(
                existingMediaId = existingTvShow.id,
                match = MetadataMatch.TvShowMatch(
                    remoteMetadataId = existingTvShow.tmdbId.toString(),
                    remoteId = "tmdb:tv:${existingTvShow.tmdbId}",
                    exists = true,
                    tvShow = existingTvShow,
                    seasons = existingSeasons.map { it.toTvSeasonModel() },
                    episodes = existingEpisodes,
                    providerId = this@TmdbMetadataProvider.id,
                ),
            )
        }

        val tvShowId = existingTvShow?.id ?: ObjectId.next()
        val tmdbSeries = try {
            checkNotNull(fetchTvSeries(tmdbId))
        } catch (e: Throwable) {
            removeLock(lockData)
            logger.error("Failed to fetch tv series data", e)
            return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val tmdbSeasons = try {
            tmdbSeries.seasons
                .mapNotNull { season -> fetchSeason(tmdbSeries.id, season.seasonNumber) }
        } catch (e: Throwable) {
            removeLock(lockData)
            logger.error("Failed to fetch tv season data", e)
            return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val existingSeasons = queries.metadataDao
            .findAllByRootIdAndType(tvShowId, MediaType.TV_SEASON)
            .associateBy { it.index!! }
        val existingEpisodes = queries.metadataDao
            .findAllByRootIdAndType(tvShowId, MediaType.TV_EPISODE)
            .associateBy { it.index!! }
        val (tvShow, tvSeasons, episodes, showCredits, episodeCredits, posterPaths, personPosterPaths) =
            tmdbSeries.asTvShow(
                tvShowId,
                tmdbSeasons,
                existingEpisodes,
                existingSeasons,
            )

        val isRefresh = existingTvShow != null

        return try {
            if (isRefresh) {
                queries.updateTvShow(tvShow, tvSeasons, episodes)
            } else {
                queries.insertTvShow(tvShow, tvSeasons, episodes)
            }

            val people1 = if (isRefresh) {
                queries.refreshCredits(tvShowId, showCredits).keys
            } else {
                queries.insertCredits(tvShowId, showCredits).keys
            }
            val people2 = episodeCredits.flatMap { (episodeId, credits) ->
                if (isRefresh) {
                    queries.refreshCredits(episodeId, credits).keys
                } else {
                    queries.insertCredits(episodeId, credits).keys
                }
            }
            val allPeople = (people1 + people2).toSet().associate { it.tmdbId!! to it.id }
            supervisorScope {
                posterPaths.flatMap { (metadataId, imageUrls) ->
                    imageUrls.mapNotNull { (imageType, imageUrl) ->
                        if (imageUrl == null) return@mapNotNull null
                        val cacheFile = imageStore.getMetadataImagePath(imageType, metadataId, tvShow.id)
                        val url = when (imageType) {
                            "poster" -> "$IMAGE_URL/w300$imageUrl"
                            "backdrop" -> "$IMAGE_URL/w1280$imageUrl"
                            else -> return@mapNotNull null
                        }
                        async { imageStore.downloadInto(cacheFile, url) }
                    }
                }.awaitAll()
                personPosterPaths.mapNotNull { (tmdbId, profileImagePath) ->
                    val personId = allPeople.getValue(tmdbId)
                    val imagePath = imageStore.getPersonImagePath(personId).takeUnless(Path::exists)
                    if (profileImagePath == null || imagePath == null) return@mapNotNull null
                    async {
                        imageStore.downloadInto(imagePath, "$IMAGE_URL/w276_and_h350_face$profileImagePath")
                    }
                }.awaitAll()
            }
            ImportMetadataResult.Success(
                match = MetadataMatch.TvShowMatch(
                    remoteMetadataId = tmdbSeries.id.toString(),
                    remoteId = "tmdb:tv:${tmdbSeries.id}",
                    exists = true,
                    tvShow = tvShow.toTvShowModel(),
                    seasons = tvSeasons.map(Metadata::toTvSeasonModel),
                    episodes = episodes.map(Metadata::toTvEpisodeModel),
                    providerId = this@TmdbMetadataProvider.id,
                ),
            )
        } catch (e: Throwable) {
            val operation = if (isRefresh) "refresh" else "insert"
            logger.error("Failed to {} tv show data showId=$tvShowId", operation, e)
            ImportMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        } finally {
            removeLock(lockData)
        }
    }

    private suspend fun queryTvSeries(query: String, year: Int?): Flow<TmdbShowDetail> {
        return tmdbApi.search.findShows(
            query = query,
            page = 1,
            firstAirDateYear = year,
            language = null,
            region = null,
            includeAdult = false
        ).results
            .sortedBy {
                scoreTmdbResult(
                    query = query,
                    queryYear = year,
                    title = it.name,
                    releaseDate = it.firstAirDate,
                    voteCount = it.voteCount
                )
            }
            .asFlow()
            .mapNotNull { fetchTvSeries(it.id) }
    }

    private suspend fun queryMovies(query: String, year: Int?): Flow<TmdbMovieDetail> {
        return tmdbApi.search.findMovies(
            query = query,
            page = 1,
            year = year,
            language = null,
            includeAdult = false,
        ).results
            .sortedBy {
                scoreTmdbResult(
                    query = query,
                    queryYear = year,
                    title = it.title,
                    releaseDate = it.releaseDate,
                    voteCount = it.voteCount
                )
            }
            .asFlow()
            .mapNotNull { fetchMovie(it.id) }
    }

    private fun scoreTmdbResult(
        query: String,
        queryYear: Int?,
        title: String,
        releaseDate: LocalDate?,
        voteCount: Int,
    ): Int {
        val releaseYearInt = releaseDate?.year
        val matchingYear = if (queryYear != null && queryYear == releaseYearInt) 0 else 1
        val hasVotes = if (voteCount > 0) 0 else 1
        return scoreString(query, title) + matchingYear + hasVotes
    }

    private suspend fun fetchTvSeries(seriesTmdbId: Int): TmdbShowDetail? {
        return try {
            tmdbApi.show.getDetails(
                seriesTmdbId,
                null,
                listOf(
                    AppendResponse.CREDITS,
                    AppendResponse.CONTENT_RATING,
                    AppendResponse.IMAGES,
                    AppendResponse.EXTERNAL_IDS,
                    AppendResponse.WATCH_PROVIDERS,
                    AppendResponse.RELEASES_DATES,
                ),
            )
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    private suspend fun fetchSeason(
        seriesTmdbId: Int,
        seasonNumber: Int,
    ): TmdbSeasonDetail? {
        var retry = 1
        while (retry < 3) {
            try {
                return tmdbApi.showSeasons.getDetails(
                    showId = seriesTmdbId,
                    seasonNumber = seasonNumber,
                    language = null,
                    includeImageLanguages = "",
                    appendResponses = listOf(
                        AppendResponse.RELEASES_DATES,
                        AppendResponse.IMAGES,
                        AppendResponse.CREDITS,
                        AppendResponse.EXTERNAL_IDS,
                    ),
                )
            } catch (e: ResponseException) {
                if (e.response.status == HttpStatusCode.NotFound) {
                    return null
                }
                throw e
            } catch (e: SocketException) {
                logger.error("TMDB response error, retry: $retry", e)
            }
            retry++
            delay(2.seconds * retry)
        }
        return null
    }

    private suspend fun fetchMovie(movieTmdbId: Int): TmdbMovieDetail? {
        return try {
            tmdbApi.movies.getDetails(
                movieId = movieTmdbId,
                language = null,
                appendResponses = listOf(
                    AppendResponse.EXTERNAL_IDS,
                    AppendResponse.CREDITS,
                    AppendResponse.RELEASES_DATES,
                    AppendResponse.IMAGES,
                ),
            )
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    private suspend fun searchMovie(request: QueryMetadata): QueryMetadataResult {
        val tmdbMovies = try {
            when {
                request.query != null -> {
                    queryMovies(request.query!!, request.year)
                        .run { if (request.firstResultOnly) take(1) else this }
                        .toList()
                }

                request.metadataId != null -> listOfNotNull(fetchMovie(request.metadataId!!.toInt()))
                else -> error("No content")
            }.toList()
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val tmdbMovieIds = tmdbMovies.map { it.id }
        val existingMovies = try {
            if (tmdbMovieIds.isNotEmpty()) {
                queries.findMoviesByTmdbId(tmdbMovieIds)
            } else {
                emptyList()
            }
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        }
        val matches = tmdbMovies.map { movieDb ->
            val existingMovie = existingMovies.find { it.tmdbId == movieDb.id }
            val remoteId = movieDb.toRemoteId()
            MetadataMatch.MovieMatch(
                remoteMetadataId = movieDb.id.toString(),
                remoteId = remoteId,
                exists = existingMovie != null,
                movie = movieDb.asMovie(
                    id = existingMovie?.id ?: remoteId,
                ),
                providerId = this@TmdbMetadataProvider.id,
            )
        }

        if (request.cacheContent) {
            tmdbMovies
                .filter { tmdbMovie -> existingMovies.none { it.tmdbId == tmdbMovie.id } }
                .forEach { movie ->
                    cacheImages(
                        movie.toRemoteId(),
                        listOf(
                            "poster" to movie.posterPath,
                            "backdrop" to movie.backdropPath,
                        )
                    )
                }
        }

        return QueryMetadataResult.Success(id, matches, request.extras)
    }

    private suspend fun searchTv(request: QueryMetadata): QueryMetadataResult {
        val metadataId = request.metadataId
        val tvShowExtras = request.extras?.asTvShowExtras()
        val tmdbSeries = try {
            when {
                request.query != null -> {
                    queryTvSeries(request.query!!, request.year)
                        .run { if (request.firstResultOnly) take(1) else this }
                        .toList()
                }

                request.metadataId != null -> listOfNotNull(fetchTvSeries(request.metadataId!!.toInt()))
                else -> error("No content") // TODO: return QueryMetadataResult.NoResults
            }
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }

        if (tmdbSeries.isEmpty() && !metadataId.isNullOrBlank()) {
            val tvResponse = queries.findShowById(metadataId)
                ?: return QueryMetadataResult.ErrorDatabaseException("Could not find show with id '$metadataId'.")
            val episodes = queries.findEpisodesByShow(metadataId)
            val matchList = listOf(
                MetadataMatch.TvShowMatch(
                    remoteMetadataId = tvResponse.tvShow.tmdbId.toString(),
                    remoteId = "tmdb:tv:${tvResponse.tvShow.tmdbId}",
                    exists = true,
                    tvShow = tvResponse.tvShow,
                    seasons = tvResponse.seasons,
                    episodes = episodes,
                    providerId = this@TmdbMetadataProvider.id,
                ),
            )
            return QueryMetadataResult.Success(id, matchList, request.extras)
        } else if (tmdbSeries.isEmpty()) {
            return QueryMetadataResult.Success(id, emptyList(), request.extras)
        }

        val tmdbSeriesIds = tmdbSeries.map(TmdbShowDetail::id)
        val existingShows = try {
            queries.findTvShowsByTmdbId(tmdbSeriesIds)
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        }

        val matches = tmdbSeries.map { tvSeries ->
            val existingShow = existingShows.find { it.tmdbId == tvSeries.id }
            val remoteId = tvSeries.toRemoteId()
            val existingSeasons = if (tvShowExtras?.seasonNumber == null) {
                existingShow?.run { queries.findTvSeasonsByTvShowId(id) }
                    ?: tvSeries.seasons.map { it.asTvSeason(it.toRemoteId(tvSeries.id), tvSeries.toRemoteId()) }
            } else {
                tvSeries.seasons
                    .filter { it.seasonNumber == tvShowExtras.seasonNumber }
                    .map { it.asTvSeason(it.toRemoteId(tvSeries.id), tvSeries.toRemoteId()) }
            }
            if (request.cacheContent) {
                cacheImages(
                    tvSeries.toRemoteId(),
                    listOf(
                        "poster" to tvSeries.posterPath,
                        "backdrop" to tvSeries.backdropPath,
                    )
                )
            }
            val existingEpisodes = when {
                tvShowExtras?.seasonNumber != null -> {
                    val seasonNumber = checkNotNull(tvShowExtras.seasonNumber)
                    val episodeNumber = tvShowExtras.episodeNumber
                    try {
                        val seasonResponse = tmdbApi.showSeasons
                            .getDetails(tvSeries.id, seasonNumber, null)
                        if (request.cacheContent) {
                            cacheImages(
                                seasonResponse.toRemoteId(tvSeries.id),
                                listOf(
                                    "poster" to seasonResponse.posterPath,
                                )
                            )
                        }
                        seasonResponse.episodes
                            .orEmpty()
                            .run {
                                if (episodeNumber == null) {
                                    this
                                } else {
                                    filter { episode ->
                                        episode.episodeNumber == episodeNumber
                                    }
                                }
                            }
                            .map { tmdbEpisode ->
                                if (request.cacheContent) {
                                    cacheImages(
                                        tmdbEpisode.toRemoteId(tvSeries.id),
                                        listOf(
                                            "poster" to tmdbEpisode.stillPath,
                                        )
                                    )
                                }
                                tmdbEpisode
                                    .asTvEpisode(
                                        id = tmdbEpisode.toRemoteId(tvSeries.id),
                                        showId = tvSeries.toRemoteId(),
                                        seasonId = seasonResponse.toRemoteId(tvSeries.id),
                                    )
                                    .toTvEpisodeModel()
                            }
                    } catch (e: Throwable) {
                        return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
                    }
                }

                existingShow != null -> {
                    queries.findEpisodesByShow(existingShow.id)
                }

                else -> emptyList()
            }

            MetadataMatch.TvShowMatch(
                remoteMetadataId = tvSeries.id.toString(),
                remoteId = remoteId,
                exists = existingShow != null,
                tvShow = existingShow ?: tvSeries.asTvShow(remoteId).toTvShowModel(),
                seasons = existingSeasons.map { it.toTvSeasonModel() },
                episodes = existingEpisodes,
                providerId = this@TmdbMetadataProvider.id,
            )
        }
        return QueryMetadataResult.Success(id, matches, request.extras)
    }

    private suspend fun cacheImages(imageKey: String, imagePaths: List<Pair<String, String?>>) {
        imagePaths.forEach { (type, path) ->
            val otherCachePath = imageStore.getMetadataImagePath(
                type,
                imageKey.encodeBase64(),
                "remote-metadata-cache/${imageKey.substringBeforeLast('-').encodeBase64()}"
            )
            val url = when (type) {
                "poster" -> "$IMAGE_URL/w300$path"
                "backdrop" -> "$IMAGE_URL/w1280$path"
                else -> return@forEach
            }
            imageStore.downloadInto(otherCachePath, url)
        }
    }

    private suspend fun awaitLockAndAcquire(lockData: Pair<Int, MediaKind>) {
        importLocks.first { locks -> !locks.contains(lockData) }
        importLocks.update { it + lockData }
    }

    private fun removeLock(lockData: Pair<Int, MediaKind>) {
        importLocks.update { it - lockData }
    }

    private fun List<TmdbCompany>.toCompanyDb() = map { company ->
        ProductionCompany(
            id = "",
            name = company.name.orEmpty(),
            tmdbId = company.id,
        )
    }

    private fun List<TmdbGenre>.toGenreDb() =
        map { genre ->
            Genre(
                id = "",
                name = genre.name,
                tmdbId = genre.id,
            )
        }

    private fun TmdbCredits.toCreditsDb(): Map<Person, List<MetadataCredit>> {
        fun TmdbCast.toPerson() = Person(id = "", name = name, tmdbId = id)
        fun TmdbCrew.toPerson() = Person(id = "", name = name, tmdbId = id)
        fun createCredit(
            creditType: CreditType,
            job: CreditJob? = null,
            character: String? = null,
            order: Int? = null,
        ) = MetadataCredit(
            personId = "",
            metadataId = "",
            character = character,
            order = order,
            job = job,
            type = creditType,
        )
        return buildMap(cast.size + crew.size) {
            cast.forEach { cast ->
                val person = cast.toPerson()
                val credit = createCredit(CreditType.CAST, character = cast.character, order = cast.order)
                compute(person) { _, list ->
                    list?.plus(credit) ?: listOf(credit)
                }
            }
            crew.forEach { crew ->
                val job = try {
                    CreditJob.valueOf(crew.job.uppercase())
                } catch (_: IllegalArgumentException) {
                    // ignore undefined jobs
                    return@forEach
                }
                val person = crew.toPerson()
                val credit = createCredit(CreditType.CREW, job = job)
                compute(person) { _, list ->
                    list?.plus(credit) ?: listOf(credit)
                }
            }
        }
    }
}

private fun scoreString(source: String, target: String): Int {
    val s1 = source.lowercase().filter { it.isLetterOrDigit() }
    val s2 = target.lowercase().filter { it.isLetterOrDigit() }

    val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

    for (i in 0..s1.length) {
        for (j in 0..s2.length) {
            when {
                i == 0 -> dp[i][j] = j
                j == 0 -> dp[i][j] = i
                else -> dp[i][j] = minOf(
                    dp[i - 1][j - 1] + costOfSubstitution(s1[i - 1], s2[j - 1]),
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                )
            }
        }
    }

    return dp[s1.length][s2.length]
}

private fun costOfSubstitution(a: Char, b: Char): Int {
    return if (a == b) 0 else 1
}