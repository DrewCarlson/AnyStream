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
import app.moviebase.tmdb.model.*
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt

private const val MAX_CACHED_POSTERS = 5

fun TmdbMovieDetail.asMovie(
    id: String,
    userId: Int = 1,
) = Movie(
    id = id,
    tmdbId = this.id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    releaseDate = releaseDate?.run { "$year-$monthNumber-$dayOfMonth" },
    backdropPath = backdropPath,
    imdbId = imdbId,
    runtime = runtime ?: -1,
    posters = images?.posters.orEmpty()
        .take(MAX_CACHED_POSTERS)
        .map { img -> Image(filePath = img.filePath, language = "") },
    added = Instant.now().toEpochMilli(),
    addedByUserId = userId,
    tmdbRating = (voteAverage * 10).roundToInt(),
    tagline = tagline,
    contentRating = releaseDates?.getCertification(Locale.getDefault().country),
    genres = genres.map { Genre(-1, it.name, it.id) },
    companies = productionCompanies.orEmpty().map { tmdbCompany ->
        ProductionCompany(-1, tmdbCompany.name.orEmpty(), tmdbCompany.id)
    },
)

fun TmdbShowDetail.asTvShow(
    tmdbSeasons: List<TmdbSeason>,
    id: String,
    userId: Int,
    createId: (id: Int) -> String = { ObjectId.get().toString() },
): Triple<TvShow, List<TvSeason>, List<Episode>> {
    val episodes = tmdbSeasons.flatMap { season ->
        season.episodes.orEmpty().map { episode ->
            episode.asTvEpisode(createId(episode.id), id, createId(season.id))
        }
    }
    val seasons = tmdbSeasons.map { season ->
        season.asTvSeason(createId(season.id))
    }
    return Triple(asTvShow(id, userId), seasons, episodes)
}

fun TmdbEpisode.asTvEpisode(id: String, showId: String, seasonId: String): Episode {
    return Episode(
        id = id,
        seasonId = seasonId,
        tmdbId = this.id,
        name = name ?: "",
        overview = "", // TODO: Only TmdbEpisodeDetails has overview,
        airDate = airDate?.run { "$year-$monthNumber-$dayOfMonth" },
        number = episodeNumber,
        seasonNumber = seasonNumber,
        showId = showId,
        stillPath = stillPath ?: "",
        tmdbRating = voteAverage?.run { times(10).roundToInt() },
    )
}

fun TmdbSeason.asTvSeason(id: String): TvSeason {
    return TvSeason(
        id = id,
        tmdbId = this.id,
        name = name,
        overview = "", // TODO: Only TmdbSeasonDetail has overview,
        seasonNumber = seasonNumber,
        airDate = airDate?.run { "$year-$monthNumber-$dayOfMonth" },
        posterPath = posterPath ?: "",
    )
}

fun TmdbShowDetail.asTvShow(id: String, userId: Int = 1): TvShow {
    return TvShow(
        id = id,
        name = name,
        tmdbId = this.id,
        overview = overview,
        firstAirDate = firstAirDate?.run { "$year-$monthNumber-$dayOfMonth" },
        posterPath = posterPath ?: "",
        added = Instant.now().toEpochMilli(),
        addedByUserId = userId,
        tmdbRating = (voteAverage * 10).roundToInt(),
        tagline = null,
        contentRating = contentRatings?.getContentRating(Locale.getDefault().country),
        genres = genres.map { Genre(-1, it.name, it.id) },
        companies = productionCompanies.orEmpty().map { tmdbCompany ->
            ProductionCompany(-1, tmdbCompany.name.orEmpty(), tmdbCompany.id)
        },
    )
}
