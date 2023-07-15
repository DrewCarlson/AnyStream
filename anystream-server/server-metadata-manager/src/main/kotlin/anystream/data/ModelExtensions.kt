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
    id: Int,
    gid: String,
    userId: Int = 1,
) = Movie(
    id = id,
    gid = gid,
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
    tmdbSeasons: List<TmdbSeasonDetail>,
    id: Int,
    gid: String,
    userId: Int,
    existingEpisodes: Map<Int, MetadataDb>,
    existingSeasons: Map<Int, MetadataDb>,
    createId: (id: Int) -> String = { ObjectId.get().toString() },
): Triple<MetadataDb, List<MetadataDb>, List<MetadataDb>> {
    val episodes = tmdbSeasons.flatMap { season ->
        val existingSeason = existingSeasons[season.id]
        season.episodes.orEmpty().map { episode ->
            val existingEpisode = existingEpisodes[season.id]
            episode.asTvEpisode(
                existingEpisode?.id ?: -1,
                existingEpisode?.gid ?: createId(episode.id),
                id,
                gid,
                existingSeason?.id ?: -1,
                existingEpisode?.gid ?: createId(season.id),
            )
        }
    }
    val seasons = tmdbSeasons.map { season ->
        val existingSeason = existingSeasons[season.id]
        season.asTvSeason(existingSeason?.id ?: -1, existingSeason?.gid ?: createId(season.id))
    }
    return Triple(asTvShow(id, gid, userId), seasons, episodes)
}

fun TmdbEpisode.asTvEpisode(
    id: Int,
    gid: String,
    showId: Int,
    showGid: String,
    seasonId: Int,
    seasonGid: String,
    userId: Int = 1,
): MetadataDb {
    val now = Clock.System.now()
    return MetadataDb(
        id = id,
        gid = gid,
        parentId = seasonId,
        parentGid = seasonGid,
        rootId = showId,
        rootGid = showGid,
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
        mediaType = MetadataDb.Type.TV_EPISODE,
    )
}

fun TmdbSeason.asTvSeason(id: Int, gid: String, userId: Int = 1): MetadataDb {
    val now = Clock.System.now()
    return MetadataDb(
        id = id,
        gid = gid,
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
        mediaType = MetadataDb.Type.TV_SEASON,
    )
}

fun TmdbSeasonDetail.asTvSeason(id: Int, gid: String, userId: Int = 1): MetadataDb {
    val now = Clock.System.now()
    return MetadataDb(
        id = id,
        gid = gid,
        tmdbId = this.id,
        title = name,
        overview = overview,
        index = seasonNumber,
        firstAvailableAt = airDate?.atStartOfDayIn(TimeZone.UTC),
        posterPath = posterPath,
        addedByUserId = userId,
        createdAt = now,
        updatedAt = now,
        mediaKind = MediaKind.TV,
        mediaType = MetadataDb.Type.TV_SEASON,
    )
}

fun TmdbShowDetail.asTvShow(
    id: Int,
    gid: String,
    userId: Int = 1,
): MetadataDb {
    val now = Clock.System.now()
    return MetadataDb(
        id = id,
        gid = gid,
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
        mediaType = MetadataDb.Type.TV_SHOW,
    )
}
