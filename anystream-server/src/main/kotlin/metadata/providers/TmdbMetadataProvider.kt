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

import anystream.data.MediaDbQueries
import anystream.data.asMovie
import anystream.data.asTvShow
import anystream.metadata.MetadataProvider
import anystream.models.MediaKind
import anystream.models.api.*
import anystream.util.ObjectId
import anystream.util.toRemoteId
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.TmdbMovies
import info.movito.themoviedbapi.TmdbTV
import info.movito.themoviedbapi.TmdbTvSeasons
import info.movito.themoviedbapi.model.MovieDb
import info.movito.themoviedbapi.model.tv.TvSeason
import info.movito.themoviedbapi.model.tv.TvSeries
import org.jdbi.v3.core.JdbiException

class TmdbMetadataProvider(
    private val tmdbApi: TmdbApi,
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
            MediaKind.MOVIE -> {
                val tmdbMovies = try {
                    when {
                        request.query != null ->
                            queryMovies(request.query!!, request.year)
                        request.contentId != null ->
                            listOfNotNull(fetchMovie(request.contentId!!.toInt()))
                        else -> error("No content")
                    }
                } catch (e: Throwable) {
                    return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
                }
                val tmdbMovieIds = tmdbMovies.map(MovieDb::getId)
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
                QueryMetadataResult.Success(providerId = id, results = matches)
            }
            MediaKind.TV -> {
                val tmdbSeries = try {
                    // search for tv show, sort by optional year
                    request.query
                        ?.run(::queryTvSeries)
                        ?.sortedBy { series ->
                            request.year == series.firstAirDate
                                ?.split('-')
                                ?.find { it.length == 4 }
                                ?.toInt()
                        }
                        // load series by tmdb id
                        ?: request.contentId
                            ?.toIntOrNull()
                            ?.run(::fetchTvSeries)
                            ?.run(::listOf)
                        ?: error("No query or content id")
                } catch (e: Throwable) {
                    return QueryMetadataResult.ErrorDataProviderException(e.stackTraceToString())
                }
                val tmdbSeriesIds = tmdbSeries.map(TvSeries::getId)
                val existingShows = try {
                    queries.findTvShowsByTmdbId(tmdbSeriesIds)
                } catch (e: JdbiException) {
                    return QueryMetadataResult.ErrorDatabaseException(e.stackTraceToString())
                }

                val matches = tmdbSeries.map { tvSeries ->
                    val existingShow = existingShows.find { it.tmdbId == tvSeries.id }
                    val remoteId = tvSeries.toRemoteId()
                    val existingSeasons = existingShow?.run {
                        queries.findTvSeasonsByTvShowId(id)
                    }.orEmpty()
                    MetadataMatch.TvShowMatch(
                        contentId = tvSeries.id.toString(),
                        remoteId = remoteId,
                        exists = existingShow != null,
                        tvShow = tvSeries.asTvShow(existingShow?.id ?: remoteId),
                        seasons = existingSeasons,
                    )
                }
                QueryMetadataResult.Success(providerId = id, results = matches)
            }
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
                queries.insertMovie(movie)
                ImportMetadataResult.Success(
                    match = MetadataMatch.MovieMatch(
                        contentId = movieDb.id.toString(),
                        remoteId = movie.id,
                        exists = true,
                        movie = movie,
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
                queries.insertTvShow(tvShow, tvSeasons, episodes)
                ImportMetadataResult.Success(
                    match = MetadataMatch.TvShowMatch(
                        contentId = tmdbSeries.id.toString(),
                        remoteId = tvShow.id,
                        exists = true,
                        tvShow = tvShow,
                        seasons = tvSeasons,
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
                )
            )
        }
    }

    private fun queryTvSeries(query: String): List<TvSeries> {
        val results = tmdbApi.search.searchTv(query, null, 1).results
        return results.mapNotNull { fetchTvSeries(it.id) }
    }

    private fun queryMovies(query: String, year: Int?): List<MovieDb> {
        val results = tmdbApi.search.searchMovie(query, year ?: 0, null, false, 1).results
        return results.mapNotNull { fetchMovie(it.id) }
    }

    private fun fetchTvSeries(seriesId: Int): TvSeries? {
        return tmdbApi.tvSeries.getSeries(
            seriesId,
            null,
            TmdbTV.TvMethod.content_ratings,
            TmdbTV.TvMethod.credits,
            TmdbTV.TvMethod.external_ids,
            TmdbTV.TvMethod.images,
            TmdbTV.TvMethod.keywords,
            TmdbTV.TvMethod.videos,
            TmdbTV.TvMethod.recommendations,
        )
    }

    private fun fetchSeason(seriesId: Int, seasonNumber: Int): TvSeason? {
        return tmdbApi.tvSeasons.getSeason(
            seriesId,
            seasonNumber,
            null,
            TmdbTvSeasons.SeasonMethod.images,
            TmdbTvSeasons.SeasonMethod.credits,
            TmdbTvSeasons.SeasonMethod.external_ids,
            TmdbTvSeasons.SeasonMethod.videos,
        )
    }

    private fun fetchMovie(movieId: Int): MovieDb? {
        return tmdbApi.movies.getMovie(
            movieId,
            null,
            TmdbMovies.MovieMethod.alternative_titles,
            TmdbMovies.MovieMethod.credits,
            TmdbMovies.MovieMethod.images,
            TmdbMovies.MovieMethod.videos,
            TmdbMovies.MovieMethod.keywords,
            TmdbMovies.MovieMethod.releases,
            TmdbMovies.MovieMethod.reviews,
            TmdbMovies.MovieMethod.similar,
            TmdbMovies.MovieMethod.release_dates,
        )
    }
}
