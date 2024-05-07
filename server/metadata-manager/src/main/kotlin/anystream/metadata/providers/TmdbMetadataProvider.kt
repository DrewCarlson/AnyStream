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
import anystream.metadata.MetadataProvider
import anystream.models.*
import anystream.models.api.*
import anystream.util.ObjectId
import anystream.util.toRemoteId
import app.moviebase.tmdb.Tmdb3
import app.moviebase.tmdb.model.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

class TmdbMetadataProvider(
    private val tmdbApi: Tmdb3,
    private val queries: MetadataDbQueries,
) : MetadataProvider {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val id: String = "tmdb"

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE, MediaKind.TV)

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
        logger.debug("Importing movie by id {}", tmdbId)
        val existingMovie = queries.findMovieByTmdbId(tmdbId)

        if (existingMovie != null && !request.refresh) {
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
        val movieId = existingMovie?.id ?: ObjectId.get().toString()
        val movieDb = try {
            checkNotNull(fetchMovie(tmdbId))
        } catch (e: Throwable) {
            logger.error("Failed to fetch data from Tmdb", e)
            return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val movie = movieDb.asMovie(movieId)
        return try {
            val finalMovie = queries.insertMovie(movie).toMovieModel()
            ImportMetadataResult.Success(
                match = MetadataMatch.MovieMatch(
                    remoteMetadataId = movie.tmdbId.toString(),
                    remoteId = movieDb.toRemoteId(),
                    exists = true,
                    movie = finalMovie,
                    providerId = this@TmdbMetadataProvider.id,
                ),
                subresults = emptyList(),
            )
        } catch (e: Throwable) {
            logger.error("Failed to insert new movie metadata", e)
            ImportMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        }
    }

    private suspend fun importTvShow(tmdbId: Int, request: ImportMetadata): ImportMetadataResult {
        val existingTvShow = queries.findTvShowByTmdbId(tmdbId)

        if (existingTvShow != null && !request.refresh) {
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

        val tvShowId = existingTvShow?.id ?: ObjectId.get().toString()
        val tmdbSeries = try {
            checkNotNull(fetchTvSeries(tmdbId))
        } catch (e: Throwable) {
            logger.error("Failed to fetch tv series data", e)
            return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val tmdbSeasons = try {
            tmdbSeries.seasons
                .filter { it.seasonNumber > 0 }
                .mapNotNull { season -> fetchSeason(tmdbSeries.id, season.seasonNumber) }
        } catch (e: Throwable) {
            logger.error("Failed to fetch tv season data", e)
            return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val existingSeasons = queries.metadataDao
            .findAllByRootGidAndType(tvShowId, MediaType.TV_SEASON)
            .associateBy { it.tmdbId!! }
        val existingEpisodes = queries.metadataDao
            .findAllByRootGidAndType(tvShowId, MediaType.TV_EPISODE)
            .associateBy { it.tmdbId!! }
        val (tvShow, tvSeasons, episodes) = tmdbSeries.asTvShow(
            tmdbSeasons,
            tvShowId,
            existingEpisodes,
            existingSeasons,
        )

        return try {
            val (finalTvShow, finalSeasons, finalEpisodes) = queries.insertTvShow(tvShow, tvSeasons, episodes)
            ImportMetadataResult.Success(
                match = MetadataMatch.TvShowMatch(
                    remoteMetadataId = tmdbSeries.id.toString(),
                    remoteId = "tmdb:tv:${tmdbSeries.id}",
                    exists = true,
                    tvShow = finalTvShow.toTvShowModel(),
                    seasons = finalSeasons.map(Metadata::toTvSeasonModel),
                    episodes = finalEpisodes.map(Metadata::toTvEpisodeModel),
                    providerId = this@TmdbMetadataProvider.id,
                ),
            )
        } catch (e: Throwable) {
            logger.error("Failed to insert tv show data", e)
            ImportMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        }
    }

    private suspend fun queryTvSeries(query: String, year: Int?): List<TmdbShowDetail> {
        val results = tmdbApi.search.findShows(query, 1, null, firstAirDateYear = year).results
        return results.mapNotNull { fetchTvSeries(it.id) }
    }

    private suspend fun queryMovies(query: String, year: Int?): List<TmdbMovieDetail> {
        return tmdbApi.search.findMovies(
            query = query,
            page = 1,
            year = year,
            language = null,
            includeAdult = false,
        ).results.mapNotNull { fetchMovie(it.id) }
    }

    private suspend fun fetchTvSeries(seriesTmdbId: Int): TmdbShowDetail? {
        return try {
            tmdbApi.show.getDetails(
                seriesTmdbId,
                null,
                listOf(
                    AppendResponse.TV_CREDITS,
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

    private suspend fun fetchSeason(seriesTmdbId: Int, seasonNumber: Int): TmdbSeasonDetail? {
        return try {
            tmdbApi.showSeasons.getDetails(
                seriesTmdbId,
                seasonNumber,
                null,
                listOf(
                    AppendResponse.RELEASES_DATES,
                    AppendResponse.IMAGES,
                    AppendResponse.CREDITS,
                    AppendResponse.TV_CREDITS,
                    AppendResponse.EXTERNAL_IDS,
                ),
                "",
            )
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    private suspend fun fetchMovie(movieTmdbId: Int): TmdbMovieDetail? {
        return try {
            tmdbApi.movies.getDetails(
                movieTmdbId,
                null,
                listOf(
                    AppendResponse.EXTERNAL_IDS,
                    AppendResponse.CREDITS,
                    AppendResponse.RELEASES_DATES,
                    AppendResponse.IMAGES,
                    AppendResponse.MOVIE_CREDITS,
                ),
            )
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    private suspend fun searchMovie(request: QueryMetadata): QueryMetadataResult {
        val tmdbMovies = try {
            when {
                request.query != null -> queryMovies(request.query!!, request.year)
                request.metadataGid != null -> listOfNotNull(fetchMovie(request.metadataGid!!.toInt()))
                else -> error("No content")
            }
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val tmdbMovieIds = tmdbMovies.map(TmdbMovieDetail::id)
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
        return QueryMetadataResult.Success(id, matches, request.extras)
    }

    private suspend fun searchTv(request: QueryMetadata): QueryMetadataResult {
        val metadataGid = request.metadataGid
        val tvShowExtras = request.extras?.asTvShowExtras()
        val tmdbSeries = try {
            // search for tv show, sort by optional year
            request.query?.let { queryTvSeries(it, request.year) }?.run {
                if (request.year == null) {
                    sortedByDescending { it.name.equals(request.query, true) }
                } else {
                    sortedByDescending {
                        it.name.equals(request.query, true) && it.firstAirDate?.year == request.year
                    }
                }
            }
                // load series by tmdb id
                ?: metadataGid?.toIntOrNull()?.let { fetchTvSeries(it) }?.run(::listOf)
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }

        if (tmdbSeries == null && !metadataGid.isNullOrBlank()) {
            val tvResponse = queries.findShowById(metadataGid)
                ?: return QueryMetadataResult.ErrorDatabaseException("Could not find show with gid '$metadataGid'.")
            val episodes = queries.findEpisodesByShow(metadataGid)
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
        } else if (tmdbSeries == null) {
            error("No id or query to search.")
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
                    ?: tvSeries.seasons.map { it.asTvSeason(it.toRemoteId(tvSeries.id)) }
            } else {
                tvSeries.seasons
                    .filter { it.seasonNumber == tvShowExtras.seasonNumber }
                    .map { it.asTvSeason(it.toRemoteId(tvSeries.id)) }
            }
            val existingEpisodes = when {
                tvShowExtras?.seasonNumber != null -> {
                    val seasonNumber = checkNotNull(tvShowExtras.seasonNumber)
                    val episodeNumber = tvShowExtras.episodeNumber
                    try {
                        val seasonResponse = tmdbApi.showSeasons
                            .getDetails(tvSeries.id, seasonNumber, null)
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
}
