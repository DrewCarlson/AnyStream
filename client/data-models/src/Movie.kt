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
package anystream.models

import kotlinx.serialization.Serializable

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val overview: String,
    val tagline: String? = null,
    val tmdbId: Int,
    val imdbId: String?,
    val runtime: Int,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val added: Long,
    val tmdbRating: Int? = null,
    val genres: List<Genre>,
    val companies: List<ProductionCompany>,
    val contentRating: String?,
) {
    val isAdded: Boolean
        get() = !id.contains(':')
}

@Serializable
data class Image(
    val filePath: String,
    val language: String,
)

fun Metadata.toMovieModel(): Movie {
    check(mediaType == MediaType.MOVIE) {
        "MetadataDb item '$id' is of type '$mediaType', cannot convert to Movie model."
    }
    return Movie(
        id = id,
        title = checkNotNull(title),
        overview = overview.orEmpty(),
        tmdbId = tmdbId ?: -1,
        imdbId = imdbId,
        runtime = checkNotNull(runtime),
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = firstAvailableAt?.instantToTmdbDate(),
        added = createdAt.epochSeconds,
        tagline = null,//tagline,
        tmdbRating = tmdbRating,
        genres = emptyList(),
        companies = emptyList(),
        contentRating = contentRating,
    )
}
