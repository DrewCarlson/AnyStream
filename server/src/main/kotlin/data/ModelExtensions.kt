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

import anystream.models.Image
import anystream.models.Movie
import anystream.models.api.TmdbMoviesResponse
import anystream.models.api.TmdbTvShowResponse
import anystream.models.tmdb.CompleteTvSeries
import anystream.models.tmdb.PartialMovie
import anystream.models.tmdb.PartialTvSeries
import info.movito.themoviedbapi.TvResultsPage
import info.movito.themoviedbapi.model.ArtworkType
import info.movito.themoviedbapi.model.MovieDb
import info.movito.themoviedbapi.model.core.MovieResultsPage
import info.movito.themoviedbapi.model.keywords.Keyword
import info.movito.themoviedbapi.model.tv.TvSeries
import java.time.Instant

private const val MAX_CACHED_POSTERS = 5

fun MovieResultsPage.asApiResponse(existingRecordIds: List<Int>): TmdbMoviesResponse {
    val ids = existingRecordIds.toMutableList()
    return when (results) {
        null -> TmdbMoviesResponse()
        else -> TmdbMoviesResponse(
            items = results.map { it.asPartialMovie(ids) },
            itemTotal = totalResults,
            page = page,
            pageTotal = totalPages
        )
    }
}

fun MovieDb.asPartialMovie(
    existingRecordIds: MutableList<Int>? = null
) = PartialMovie(
    tmdbId = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    releaseDate = releaseDate,
    backdropPath = backdropPath,
    isAdded = existingRecordIds?.remove(id) ?: false
)

fun MovieDb.asMovie(
    id: String,
    userId: String
) = Movie(
    id = id,
    tmdbId = this.id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    releaseDate = releaseDate,
    backdropPath = backdropPath,
    imdbId = imdbID,
    runtime = runtime,
    posters = getImages(ArtworkType.POSTER)
        .filter { "en".equals(it.language, true) }
        .take(MAX_CACHED_POSTERS)
        .map { img ->
            Image(
                filePath = img.filePath,
                language = img.language ?: ""
            )
        },
    added = Instant.now().toEpochMilli(),
    addedByUserId = userId
)

fun TvResultsPage.asApiResponse() =
    when (results) {
        null -> TmdbTvShowResponse()
        else -> TmdbTvShowResponse(
            items = results.map { it.asPartialTvSeries() },
            itemTotal = totalResults,
            page = page,
            pageTotal = totalPages
        )
    }

fun TvSeries.asPartialTvSeries() = PartialTvSeries(
    tmdbId = id,
    name = name,
    overview = overview,
    firstAirDate = firstAirDate,
    lastAirDate = lastAirDate
)


fun TvSeries.asCompleteTvSeries() = CompleteTvSeries(
    tmdbId = id,
    name = name,
    overview = overview,
    firstAirDate = firstAirDate,
    lastAirDate = lastAirDate,
    keywords = keywords.map(Keyword::getName)
)
