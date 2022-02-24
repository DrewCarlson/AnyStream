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
import anystream.models.MediaKind
import anystream.models.api.*
import anystream.util.ObjectId
import anystream.util.toRemoteId
import app.moviebase.tmdb.Tmdb3
import app.moviebase.tmdb.model.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import org.jdbi.v3.core.JdbiException

class TmdbMetadataProvider(
    private val tmdbApi: Tmdb3,
    private val queries: MediaDbQueries,
) : MetadataProvider {

    override val id: String = "tmdb"

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE, MediaKind.TV)

    override suspend fun importMetadata(request: ImportMetadata): List<ImportMetadataResult> {
        return when (request.mediaKind) {
            MediaKind.MOVIE -> request.contentIds.map { contentId ->
                importMovie(contentId.toInt(), request)
            }
            MediaKind.TV -> request.contentIds.map { contentId ->
                importTvShow(contentId.toInt(), request)
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

    private suspend fun importMovie(contentId: Int, request: ImportMetadata): ImportMetadataResult {
        val existingMovie = queries.findMovieByTmdbId(contentId)

        val result = if (existingMovie == null || request.refresh) {
            val movieId = existingMovie?.id ?: ObjectId.get().toString()
            val userId = existingMovie?.addedByUserId ?: 1
            val movieDb = try {
                checkNotNull(fetchMovie(contentId))
            } catch (e: Throwable) {
                return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
            }
            val movie = movieDb.asMovie(movieId, userId)
            try {
                val finalMovie = queries.insertMovie(movie).toMovieModel()
                ImportMetadataResult.Success(
                    match = MetadataMatch.MovieMatch(
                        contentId = finalMovie.id,
                        remoteId = movie.id,
                        exists = true,
                        movie = finalMovie,
                    ),
                    subresults = emptyList(),
                )
            } catch (e: JdbiException) {
                ImportMetadataResult.ErrorDatabaseException(e.stackTraceToString())
            }
        } else {
            ImportMetadataResult.ErrorMediaAlreadyExists(
                existingMediaId = existingMovie.id,
                match = MetadataMatch.MovieMatch(
                    contentId = existingMovie.tmdbId.toString(),
                    remoteId = existingMovie.id,
                    exists = true,
                    movie = existingMovie,
                )
            )
        }
        return result
    }

    private suspend fun importTvShow(
        contentId: Int,
        request: ImportMetadata
    ): ImportMetadataResult {
        val existingTvShow = queries.findTvShowByTmdbId(contentId)
        val existingSeasons = existingTvShow?.run { queries.findTvSeasonsByTvShowId(id) }.orEmpty()

        return if (existingTvShow == null || request.refresh) {
            val tvShowId = existingTvShow?.id ?: ObjectId.get().toString()
            val userId = 1
            val tmdbSeries = try {
                checkNotNull(fetchTvSeries(contentId))
            } catch (e: Throwable) {
                return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
            }
            val tmdbSeasons = try {
                tmdbSeries.seasons
                    .filter { it.seasonNumber > 0 }
                    .mapNotNull { season ->
                        fetchSeason(tmdbSeries.id, season.seasonNumber)
                    }
            } catch (e: Throwable) {
                return ImportMetadataResult.ErrorDataProviderException(e.stackTraceToString())
            }
            val (tvShow, tvSeasons, episodes) = tmdbSeries.asTvShow(tmdbSeasons, tvShowId, userId)

            try {
                // TODO: Return processed show models
                queries.insertTvShow(tvShow, tvSeasons, episodes)
                ImportMetadataResult.Success(
                    match = MetadataMatch.TvShowMatch(
                        contentId = tmdbSeries.id.toString(),
                        remoteId = tvShow.id,
                        exists = true,
                        tvShow = tvShow,
                        seasons = tvSeasons,
                        episodes = emptyList(),
                    )
                )
            } catch (e: JdbiException) {
                ImportMetadataResult.ErrorDatabaseException(e.stackTraceToString())
            }
        } else {
            ImportMetadataResult.ErrorMediaAlreadyExists(
                existingMediaId = existingTvShow.id,
                match = MetadataMatch.TvShowMatch(
                    contentId = existingTvShow.tmdbId.toString(),
                    remoteId = existingTvShow.id,
                    exists = true,
                    tvShow = existingTvShow,
                    seasons = existingSeasons,
                    episodes = emptyList(),
                )
            )
        }
    }

    private suspend fun queryTvSeries(query: String): List<TmdbShowDetail> {
        val results = tmdbApi.search.findShows(query, 1, null).results
        return results.mapNotNull { fetchTvSeries(it.id) }
    }

    private suspend fun queryMovies(query: String, year: Int?): List<TmdbMovieDetail> {
        return tmdbApi.search.findMovies(
            query = query,
            page = 1,
            year = year ?: 0,
            language = null,
            includeAdult = false
        ).results.mapNotNull { fetchMovie(it.id) }
    }

    private suspend fun fetchTvSeries(seriesId: Int): TmdbShowDetail? {
        return try {
            tmdbApi.show.getDetails(
                seriesId,
                null,
                listOf(
                    AppendResponse.TV_CREDITS,
                    AppendResponse.CONTENT_RATING,
                    AppendResponse.IMAGES,
                    AppendResponse.EXTERNAL_IDS,
                    AppendResponse.WATCH_PROVIDERS,
                    AppendResponse.RELEASES_DATES,
                )
            )
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    private suspend fun fetchSeason(seriesId: Int, seasonNumber: Int): TmdbSeason? {
        return try {
            tmdbApi.showSeasons.getDetails(
                seriesId,
                seasonNumber,
                null,
                listOf(
                    AppendResponse.RELEASES_DATES,
                    AppendResponse.IMAGES,
                    AppendResponse.CREDITS,
                    AppendResponse.TV_CREDITS,
                    AppendResponse.EXTERNAL_IDS,
                ),
                ""
            )
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    private suspend fun fetchMovie(movieId: Int): TmdbMovieDetail? {
        return try {
            tmdbApi.movies.getDetails(
                movieId,
                null,
                listOf(
                    AppendResponse.EXTERNAL_IDS,
                    AppendResponse.CREDITS,
                    AppendResponse.RELEASES_DATES,
                    AppendResponse.IMAGES,
                    AppendResponse.MOVIE_CREDITS,
                )
            )
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    private suspend fun searchMovie(request: QueryMetadata): QueryMetadataResult {
        val tmdbMovies = try {
            when {
                request.query != null -> queryMovies(request.query!!, request.year)
                request.contentId != null -> listOfNotNull(fetchMovie(request.contentId!!.toInt()))
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
        } catch (e: JdbiException) {
            return QueryMetadataResult.ErrorDatabaseException(e.stackTraceToString())
        }
        val matches = tmdbMovies.map { movieDb ->
            val existingMovie = existingMovies.find { it.tmdbId == movieDb.id }
            val remoteId = movieDb.toRemoteId()
            MetadataMatch.MovieMatch(
                contentId = movieDb.id.toString(),
                remoteId = remoteId,
                exists = existingMovie != null,
                movie = movieDb.asMovie(
                    id = existingMovie?.id ?: remoteId,
                    userId = 1,
                )
            )
        }
        return QueryMetadataResult.Success(id, matches, request.extras)
    }

    private suspend fun searchTv(request: QueryMetadata): QueryMetadataResult {
        val tvShowExtras = request.extras?.asTvShowExtras()
        val tmdbSeries = try {
            // search for tv show, sort by optional year
            request.query
                ?.let { queryTvSeries(it) }
                ?.sortedBy { series -> request.year == series.firstAirDate?.year }
                // load series by tmdb id
                ?: request.contentId
                    ?.toIntOrNull()
                    ?.let { fetchTvSeries(it) }
                    ?.run(::listOf)
                ?: error("No query or content id")
        } catch (e: Throwable) {
            return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val tmdbSeriesIds = tmdbSeries.map(TmdbShowDetail::id)
        val existingShows = try {
            queries.findTvShowsByTmdbId(tmdbSeriesIds)
        } catch (e: JdbiException) {
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
                                if (episodeNumber == null) this else {
                                    filter { episode ->
                                        episode.episodeNumber == episodeNumber
                                    }
                                }
                            }
                            .map { tmdbEpisode ->
                                tmdbEpisode.asTvEpisode(
                                    tmdbEpisode.toRemoteId(tvSeries.id),
                                    tvSeries.toRemoteId(),
                                    seasonResponse.toRemoteId(tvSeries.id)
                                )
                            }
                    } catch (e: Throwable) {
                        return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
                    }
                }
                else -> emptyList()
            }

            MetadataMatch.TvShowMatch(
                contentId = tvSeries.id.toString(),
                remoteId = remoteId,
                exists = existingShow != null,
                tvShow = tvSeries.asTvShow(existingShow?.id ?: remoteId),
                seasons = existingSeasons,
                episodes = existingEpisodes,
            )
        }
        return QueryMetadataResult.Success(id, matches, request.extras)
    }
}
