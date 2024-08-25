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
data class TvSeason(
    val id: String,
    val name: String,
    val overview: String,
    val seasonNumber: Int,
    val airDate: String?,
    val tmdbId: Int?,
    val posterPath: String?,
)


fun Metadata.toTvSeasonModel(): TvSeason {
    check(mediaType == MediaType.TV_SEASON) {
        "MetadataDb item '$id' is of type '$mediaType', cannot convert to TvSeason model."
    }
    return TvSeason(
        id = id,
        name = checkNotNull(title),
        overview = overview.orEmpty(),
        seasonNumber = checkNotNull(index),
        airDate = firstAvailableAt?.instantToTmdbDate(),
        tmdbId = tmdbId,
        posterPath = posterPath,
    )
}
