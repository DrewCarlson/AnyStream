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

import anystream.db.model.MetadataDb
import anystream.models.*
import anystream.util.ObjectId
import app.moviebase.tmdb.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt

private const val MAX_CACHED_POSTERS = 5

fun TmdbMovieDetail.asMovie(
    id: String,
    userId: Int = 1
) = Movie(
    id = -1,
    gid = id,
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
    }
)

fun TmdbShowDetail.asTvShow(
    tmdbSeasons: List<TmdbSeason>,
    id: String,
    userId: Int,
    createId: (id: Int) -> String = { ObjectId.get().toString() }
): Triple<MetadataDb, List<MetadataDb>, List<MetadataDb>> {
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

fun TmdbEpisode.asTvEpisode(id: String, showId: String, seasonId: String, userId: Int = 1): MetadataDb {
    val now = Clock.System.now()
    return MetadataDb(
        id = -1,
        gid = id,
        parentId = -1, // TODO: Add parent id int
        parentGid = seasonId,
        rootId = -1, // TODO: Add root id int
        rootGid = showId,
        tmdbId = this.id,
        title = name ?: "",
        overview = overview.orEmpty(),
        addedByUserId = userId,
        firstAvailableAt = airDate?.atStartOfDayIn(TimeZone.UTC),
        index = episodeNumber,
        parentIndex = seasonNumber,
        posterPath = stillPath,
        tmdbRating = voteAverage?.run { times(10).roundToInt() },
        createdAt = now,
        updatedAt = now,
        mediaKind = MediaKind.TV,
        mediaType = MetadataDb.Type.TV_EPISODE
    )
}

fun TmdbSeason.asTvSeason(id: String, userId: Int = 1): MetadataDb {
    val now = Clock.System.now()
    return MetadataDb(
        id = -1,
        gid = id,
        tmdbId = this.id,
        title = name,
        overview = overview.orEmpty(),
        index = seasonNumber,
        firstAvailableAt = airDate?.atStartOfDayIn(TimeZone.UTC),
        posterPath = posterPath,
        addedByUserId = userId,
        createdAt = now,
        updatedAt = now,
        mediaKind = MediaKind.TV,
        mediaType = MetadataDb.Type.TV_SEASON
    )
}

fun TmdbShowDetail.asTvShow(id: String, userId: Int = 1): MetadataDb {
    val now = Clock.System.now()
    return MetadataDb(
        id = -1,
        gid = id,
        title = name,
        tmdbId = this.id,
        overview = overview,
        firstAvailableAt = firstAirDate?.atStartOfDayIn(TimeZone.UTC),
        posterPath = posterPath ?: "",
        createdAt = now,
        updatedAt = now,
        addedByUserId = userId,
        tmdbRating = (voteAverage * 10).roundToInt(),
        tagline = null,
        contentRating = contentRatings?.getContentRating(Locale.getDefault().country),
        genres = genres.map { Genre(-1, it.name, it.id) },
        companies = productionCompanies.orEmpty().map { tmdbCompany ->
            ProductionCompany(-1, tmdbCompany.name.orEmpty(), tmdbCompany.id)
        },
        mediaKind = MediaKind.TV,
        mediaType = MetadataDb.Type.TV_SHOW
    )
}
