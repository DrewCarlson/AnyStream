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
data class Episode(
    val id: String,
    val showId: String,
    val seasonId: String,
    val name: String,
    val tmdbId: Int,
    val overview: String,
    val airDate: String?,
    val number: Int,
    val seasonNumber: Int,
    val stillPath: String,
    val backdropPath: String?,
    val tmdbRating: Int?,
)


fun Metadata.toTvEpisodeModel(): Episode {
    check(mediaType == MediaType.TV_EPISODE) {
        "MetadataDb item '$id' is of type '$mediaType', cannot convert to TvEpisode model."
    }
    return Episode(
        id = id,
        showId = checkNotNull(rootId),
        seasonId = checkNotNull(parentId),
        name = checkNotNull(title),
        tmdbId = tmdbId ?: -1,
        overview = overview.orEmpty(),
        airDate = firstAvailableAt,//?.instantToTmdbDate(),
        number = checkNotNull(index),
        seasonNumber = checkNotNull(parentIndex),
        stillPath = posterPath.orEmpty(),
        backdropPath = backdropPath,
        tmdbRating = tmdbRating,
    )
}
