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

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val overview: String,
    val tagline: String? = null,
    val tmdbId: Int,
    val imdbId: String?,
    val runtime: Duration,
    val releaseDate: Instant?,
    val createdAt: Instant,
    val tmdbRating: Int? = null,
    val contentRating: String?,
) {
    val isAdded: Boolean
        get() = !id.contains(':')

    val releaseYear: String?
        get() = releaseDate
            ?.toLocalDateTime(TimeZone.currentSystemDefault())
            ?.year
            ?.toString()
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
        releaseDate = firstAvailableAt,
        createdAt = createdAt,
        tagline = null,//tagline,
        tmdbRating = tmdbRating,
        contentRating = contentRating,
    )
}
