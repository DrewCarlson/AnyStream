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
import java.util.*
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


fun TmdbMovieDetail.asMovie(
    id: String,
    clock: Clock = Clock.System, // TODO: remove default
) = Movie(
    id = id,
    tmdbId = this.id,
    title = title,
    overview = overview,
    releaseDate = releaseDate?.atStartOfDayIn(TimeZone.UTC),
    imdbId = imdbId,
    runtime = runtime?.minutes ?: Duration.ZERO,
    createdAt = clock.now(),
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
    posterPaths: MutableMap<String, List<Pair<String, String?>>>
): Triple<Metadata, List<Metadata>, List<Metadata>> {
    posterPaths[id] = listOf(
        "poster" to posterPath,
        "backdrop" to backdropPath,
    )
    val seasons = tmdbSeasons.map { season ->
        val existingSeason = existingSeasons[season.seasonNumber]
        val seasonId = existingSeason?.id ?: ObjectId.next()
        posterPaths[seasonId] = listOf("poster" to season.posterPath)
        season.asTvSeason(
            id = seasonId,
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
            val episodeId = existingEpisode?.id ?: ObjectId.next()
            posterPaths[episodeId] = listOf("poster" to episode.stillPath)
            episode.asTvEpisode(
                id = episodeId,
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
        createdAt = now,
        updatedAt = now,
        tmdbRating = (voteAverage * 10).roundToInt(),
        //tagline = null,
        contentRating = contentRatings?.getContentRating(Locale.getDefault().country),
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_SHOW,
    )
}
