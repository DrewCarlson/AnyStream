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

import kotlinx.datetime.*
import kotlinx.serialization.Serializable

@Serializable
data class TvShow(
    val id: String,
    val name: String,
    val tmdbId: Int,
    val overview: String,
    val tagline: String? = null,
    val firstAirDate: String?,
    val added: Long,
    val tmdbRating: Int? = null,
    val contentRating: String? = null,
    val genres: List<Genre> = emptyList(),
    val companies: List<ProductionCompany> = emptyList(),
) {
    val isAdded: Boolean
        get() = !id.contains(':')
}


fun Metadata.toTvShowModel(): TvShow {
    check(mediaType == MediaType.TV_SHOW) {
        "MetadataDb item '$id' is of type '$mediaType', cannot convert to TvShow model."
    }
    return TvShow(
        id = id,
        name = checkNotNull(title),
        tmdbId = tmdbId ?: -1,
        overview = overview.orEmpty(),
        firstAirDate = firstAvailableAt?.instantToTmdbDate(),
        added = createdAt.epochSeconds,
        tagline = null,//tagline,
        tmdbRating = tmdbRating,
        contentRating = contentRating,
    )
}

fun Instant.instantToTmdbDate(): String {
    return toLocalDateTime(TimeZone.UTC).run { "$year-$monthNumber-$dayOfMonth" }
}

fun String.tmdbDateToInstant(): Instant? {
    return split('-')
        .takeIf { it.size == 3 }
        ?.let { (year, month, day) ->
            LocalDate(year.toInt(), month.toInt(), day.toInt()).atStartOfDayIn(TimeZone.UTC)
        }
}
