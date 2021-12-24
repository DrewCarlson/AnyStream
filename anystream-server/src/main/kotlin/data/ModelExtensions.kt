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
package anystream.data

import anystream.models.*
import anystream.util.ObjectId
import info.movito.themoviedbapi.model.ArtworkType
import info.movito.themoviedbapi.model.MovieDb
import info.movito.themoviedbapi.model.tv.TvEpisode
import info.movito.themoviedbapi.model.tv.TvSeries
import java.time.Instant
import info.movito.themoviedbapi.model.tv.TvSeason as TvSeasonDb

private const val MAX_CACHED_POSTERS = 5

fun MovieDb.asMovie(
    id: String,
    userId: Int = 1,
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
    posters = runCatching { getImages(ArtworkType.POSTER) }
        .getOrNull()
        .orEmpty()
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

fun TvSeries.asTvShow(
    tmdbSeasons: List<TvSeasonDb>,
    id: String,
    userId: Int,
    createId: (id: Int) -> String = { ObjectId.get().toString() },
): Triple<TvShow, List<TvSeason>, List<Episode>> {
    val episodes = tmdbSeasons.flatMap { season ->
        season.episodes.map { episode ->
            episode.asTvEpisode(createId(episode.id), id, createId(season.id))
        }
    }
    val seasons = tmdbSeasons.map { season ->
        season.asTvSeason(createId(season.id))
    }
    return Triple(asTvShow(id, userId), seasons, episodes)
}

fun TvEpisode.asTvEpisode(id: String, showId: String, seasonId: String): Episode {
    return Episode(
        id = id,
        seasonId = seasonId,
        tmdbId = this.id,
        name = name,
        overview = overview,
        airDate = airDate ?: "",
        number = episodeNumber,
        seasonNumber = seasonNumber,
        showId = showId,
        stillPath = stillPath ?: ""
    )
}

fun TvSeasonDb.asTvSeason(id: String): TvSeason {
    return TvSeason(
        id = id,
        tmdbId = this.id,
        name = name,
        overview = overview,
        seasonNumber = seasonNumber,
        airDate = airDate ?: "",
        posterPath = posterPath ?: "",
    )
}

fun TvSeries.asTvShow(id: String, userId: Int = 1): TvShow {
    return TvShow(
        id = id,
        name = name,
        tmdbId = this.id,
        overview = overview,
        firstAirDate = firstAirDate ?: "",
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes,
        posterPath = posterPath ?: "",
        added = Instant.now().toEpochMilli(),
        addedByUserId = userId,
    )
}
