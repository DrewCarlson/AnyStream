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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt


fun TmdbMovieDetail.asMovie(
    id: String,
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
    added = Instant.now().toEpochMilli(),
    tmdbRating = (voteAverage * 10).roundToInt(),
    tagline = tagline,
    contentRating = releaseDates?.getCertification(Locale.getDefault().country),
    // TODO: use remote ids
    genres = genres.map { Genre("", it.name, it.id) },
    companies = productionCompanies.orEmpty().map { tmdbCompany ->
        ProductionCompany("", tmdbCompany.name.orEmpty(), tmdbCompany.id)
    },
)

fun TmdbShowDetail.asTvShow(
    tmdbSeasons: List<TmdbSeasonDetail>,
    id: String,
    existingEpisodes: Map<Int, Metadata>,
    existingSeasons: Map<Int, Metadata>,
): Triple<Metadata, List<Metadata>, List<Metadata>> {
    val seasons = tmdbSeasons.map { season ->
        val existingSeason = existingSeasons[season.seasonNumber]
        season.asTvSeason(
            id = existingSeason?.id ?: ObjectId.next(),
            showId = id,
        )
    }.associateBy(Metadata::index)
    val episodes = tmdbSeasons.flatMap { season ->
        val existingSeason = seasons[season.seasonNumber]
            ?: run {
                // TODO: forward feedback for unhandled episodes
                return@flatMap emptyList()
            }
        season.episodes.orEmpty().map { episode ->
            val existingEpisode = existingEpisodes[episode.episodeNumber]
            episode.asTvEpisode(
                id = existingEpisode?.id ?: ObjectId.next(),
                showId = id,
                seasonId = existingSeason.id,
            )
        }
    }
    return Triple(asTvShow(id), seasons.values.toList(), episodes)
}

fun TmdbEpisode.asTvEpisode(
    id: String,
    showId: String,
    seasonId: String,
): Metadata {
    val now = Clock.System.now()
    return Metadata(
        id = id,
        parentId = seasonId,
        rootId = showId,
        // omit tmdb id because season ids are not unique across shows/seasons/episodes
        tmdbId = null,
        title = name ?: "",
        overview = overview.orEmpty(),
        firstAvailableAt = airDate?.atStartOfDayIn(TimeZone.UTC),
        index = episodeNumber,
        parentIndex = seasonNumber,
        posterPath = stillPath,
        tmdbRating = voteAverage?.run { times(10).roundToInt() },
        createdAt = now,
        updatedAt = now,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_EPISODE,
    )
}

fun TmdbSeason.asTvSeason(id: String, showId: String): Metadata {
    val now = Clock.System.now()
    return Metadata(
        id = id,
        parentId = showId,
        rootId = showId,
        // omit tmdb id because season ids are not unique across shows/seasons/episodes
        tmdbId = null,
        title = name,
        overview = overview.orEmpty(),
        index = seasonNumber,
        firstAvailableAt = airDate?.atStartOfDayIn(TimeZone.UTC),
        posterPath = posterPath,
        createdAt = now,
        updatedAt = now,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_SEASON,
    )
}

fun TmdbSeasonDetail.asTvSeason(id: String, showId: String): Metadata {
    val now = Clock.System.now()
    return Metadata(
        id = id,
        tmdbId = null,
        parentId = showId,
        rootId = showId,
        title = name,
        overview = overview,
        index = seasonNumber,
        firstAvailableAt = airDate?.atStartOfDayIn(TimeZone.UTC),
        posterPath = posterPath,
        backdropPath = images?.backdrops?.firstOrNull()?.filePath,
        createdAt = now,
        updatedAt = now,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_SEASON,
    )
}

fun TmdbShowDetail.asTvShow(id: String): Metadata {
    val now = Clock.System.now()
    return Metadata(
        id = id,
        title = name,
        tmdbId = this.id,
        overview = overview,
        firstAvailableAt = firstAirDate?.atStartOfDayIn(TimeZone.UTC),
        posterPath = posterPath.orEmpty(),
        createdAt = now,
        updatedAt = now,
        tmdbRating = (voteAverage * 10).roundToInt(),
        //tagline = null,
        backdropPath = images?.backdrops?.firstOrNull()?.filePath,
        contentRating = contentRatings?.getContentRating(Locale.getDefault().country),
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_SHOW,
    )
}
