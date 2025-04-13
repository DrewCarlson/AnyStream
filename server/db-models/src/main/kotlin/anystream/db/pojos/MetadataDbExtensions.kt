/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
package anystream.db.pojos

import anystream.models.*
import kotlinx.datetime.*


fun Movie.toMetadataDb(): Metadata {
    val createTime = Clock.System.now()
    return Metadata(
        id = id,
        title = title,
        overview = overview,
        tmdbId = tmdbId,
        imdbId = imdbId,
        runtime = runtime,
        firstAvailableAt = releaseDate,
        createdAt = createTime,
        updatedAt = createTime,
        mediaKind = MediaKind.MOVIE,
        mediaType = MediaType.MOVIE,
        tmdbRating = tmdbRating,
        contentRating = contentRating,
    )
}

fun TvShow.toMetadataDb(): Metadata {
    val createTime = Clock.System.now()
    return Metadata(
        id = id,
        title = name,
        overview = overview,
        tmdbId = tmdbId,
        firstAvailableAt = firstAirDate,
        createdAt = createTime,
        updatedAt = createTime,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_SHOW,
        tmdbRating = tmdbRating,
        contentRating = contentRating,
    )
}

fun TvSeason.fromTvSeason(tvShowRecord: Metadata): Metadata {
    val createTime = Clock.System.now()
    return Metadata(
        id = id,
        rootId = tvShowRecord.id,
        parentId = tvShowRecord.id,
        title = name,
        overview = overview,
        tmdbId = tmdbId,
        index = seasonNumber,
        firstAvailableAt = airDate,
        createdAt = createTime,
        updatedAt = createTime,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_SEASON,
    )
}

fun Episode.fromTvEpisode(tvShowRecord: Metadata, tvSeasonRecord: Metadata): Metadata {
    val createTime = Clock.System.now()
    return Metadata(
        id = id,
        rootId = tvShowRecord.id,
        parentId = tvSeasonRecord.id,
        title = name,
        overview = overview,
        tmdbId = tmdbId,
        index = number,
        parentIndex = tvSeasonRecord.index,
        firstAvailableAt = airDate,
        createdAt = createTime,
        updatedAt = createTime,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_EPISODE,
        tmdbRating = tvShowRecord.tmdbRating,
    )
}
