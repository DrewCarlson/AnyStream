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

import dev.drewhamilton.poko.Poko
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Poko
@Serializable
class TvShow(
    val id: String,
    val name: String,
    val tmdbId: Int,
    val overview: String,
    val tagline: String? = null,
    val firstAirDate: Instant?,
    val createdAt: Instant,
    val tmdbRating: Int? = null,
    val contentRating: String? = null,
) {
    val isAdded: Boolean
        get() = !id.contains(':')

    val releaseYear: String?
        get() = firstAirDate
            ?.toLocalDateTime(TimeZone.currentSystemDefault())
            ?.year
            ?.toString()
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
        firstAirDate = firstAvailableAt,
        createdAt = createdAt,
        tagline = null,//tagline,
        tmdbRating = tmdbRating,
        contentRating = contentRating,
    )
}

fun Instant.instantToTmdbDate(): String {
    return toLocalDateTime(TimeZone.UTC).run { "$year-${month.number}-$day" }
}

fun String.tmdbDateToInstant(): Instant? {
    return split('-')
        .takeIf { it.size == 3 }
        ?.let { (year, month, day) ->
            LocalDate(year.toInt(), month.toInt(), day.toInt()).atStartOfDayIn(TimeZone.UTC)
        }
}
