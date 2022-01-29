/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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
package anystream.db.model

import anystream.models.*
import kotlinx.datetime.*

data class MediaDb(
    val id: Int,
    val gid: String,
    val rootId: Int?,
    val rootGid: String?,
    val parentId: Int?,
    val parentGid: String?,
    val title: String?,
    val overview: String?,
    val tmdbId: Int?,
    val imdbId: String?,
    val runtime: Int?,
    val index: Int?,
    val parentIndex: Int?,
    val contentRating: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val firstAvailableAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val addedByUserId: Int,
    val mediaKind: MediaKind,
    val mediaType: Type,
) {
    enum class Type {
        MOVIE,
        TV_SHOW,
        TV_EPISODE,
        TV_SEASON,
    }

    fun toMovieModel(): Movie {
        check(mediaType == Type.MOVIE) {
            "MediaDb item '$gid' is of type '$mediaType', cannot convert to Movie model."
        }
        return Movie(
            id = gid,
            title = checkNotNull(title),
            overview = overview.orEmpty(),
            tmdbId = tmdbId ?: -1,
            imdbId = imdbId,
            runtime = checkNotNull(runtime),
            posters = emptyList(),
            posterPath = posterPath,
            backdropPath = backdropPath,
            releaseDate = firstAvailableAt?.instantToTmdbDate(),
            added = createdAt.epochSeconds,
            addedByUserId = addedByUserId,
        )
    }

    fun toTvShowModel(): TvShow {
        check(mediaType == Type.TV_SHOW) {
            "MediaDb item '$gid' is of type '$mediaType', cannot convert to TvShow model."
        }
        return TvShow(
            id = gid,
            name = checkNotNull(title),
            tmdbId = tmdbId ?: -1,
            overview = overview.orEmpty(),
            firstAirDate = firstAvailableAt?.instantToTmdbDate(),
            posterPath = posterPath.orEmpty(),
            added = createdAt.epochSeconds,
            addedByUserId = addedByUserId,
        )
    }

    fun toTvEpisodeModel(): Episode {
        check(mediaType == Type.TV_EPISODE) {
            "MediaDb item '$gid' is of type '$mediaType', cannot convert to TvEpisode model."
        }
        return Episode(
            id = gid,
            showId = checkNotNull(rootGid),
            seasonId = checkNotNull(parentGid),
            name = checkNotNull(title),
            tmdbId = tmdbId ?: -1,
            overview = overview.orEmpty(),
            airDate = firstAvailableAt?.instantToTmdbDate(),
            number = checkNotNull(index),
            seasonNumber = checkNotNull(parentIndex),
            stillPath = posterPath.orEmpty(),
        )
    }

    fun toTvSeasonModel(): TvSeason {
        check(mediaType == Type.TV_SEASON) {
            "MediaDb item '$gid' is of type '$mediaType', cannot convert to TvSeason model."
        }
        return TvSeason(
            id = gid,
            name = checkNotNull(title),
            overview = overview.orEmpty(),
            seasonNumber = checkNotNull(index),
            airDate = firstAvailableAt?.instantToTmdbDate(),
            tmdbId = tmdbId ?: -1,
            posterPath = posterPath,
        )
    }

    companion object {
        fun fromMovie(movie: Movie): MediaDb {
            val createTime = Clock.System.now()
            return MediaDb(
                id = -1,
                gid = movie.id,
                rootId = null,
                rootGid = null,
                parentId = null,
                parentGid = null,
                title = movie.title,
                overview = movie.overview,
                tmdbId = movie.tmdbId,
                imdbId = movie.imdbId,
                runtime = movie.runtime,
                index = null,
                parentIndex = null,
                contentRating = null,
                posterPath = movie.posterPath,
                backdropPath = movie.backdropPath,
                firstAvailableAt = movie.releaseDate?.tmdbDateToInstant(),
                createdAt = createTime,
                updatedAt = createTime,
                addedByUserId = movie.addedByUserId,
                mediaKind = MediaKind.MOVIE,
                mediaType = Type.MOVIE,
            )
        }

        fun fromTvShow(tvShow: TvShow): MediaDb {
            val createTime = Clock.System.now()
            return MediaDb(
                id = -1,
                gid = tvShow.id,
                rootId = null,
                rootGid = null,
                parentId = null,
                parentGid = null,
                title = tvShow.name,
                overview = tvShow.overview,
                tmdbId = tvShow.tmdbId,
                imdbId = null,
                runtime = null,
                index = null,
                parentIndex = null,
                contentRating = null,
                posterPath = tvShow.posterPath,
                backdropPath = null,
                firstAvailableAt = tvShow.firstAirDate?.tmdbDateToInstant(),
                createdAt = createTime,
                updatedAt = createTime,
                addedByUserId = tvShow.addedByUserId,
                mediaKind = MediaKind.TV,
                mediaType = Type.TV_SHOW,
            )
        }

        fun fromTvSeason(tvShowRecord: MediaDb, tvSeason: TvSeason): MediaDb {
            val createTime = Clock.System.now()
            return MediaDb(
                id = -1,
                gid = tvSeason.id,
                rootId = tvShowRecord.id,
                rootGid = tvShowRecord.gid,
                parentId = tvShowRecord.id,
                parentGid = tvShowRecord.gid,
                title = tvSeason.name,
                overview = tvSeason.overview,
                tmdbId = tvSeason.tmdbId,
                imdbId = null,
                runtime = null,
                index = tvSeason.seasonNumber,
                parentIndex = null,
                contentRating = null,
                posterPath = tvSeason.posterPath,
                backdropPath = null,
                firstAvailableAt = tvSeason.airDate?.tmdbDateToInstant(),
                createdAt = createTime,
                updatedAt = createTime,
                addedByUserId = tvShowRecord.addedByUserId,
                mediaKind = MediaKind.TV,
                mediaType = Type.TV_SEASON,
            )
        }

        fun fromTvEpisode(tvShowRecord: MediaDb, tvSeasonRecord: MediaDb, tvEpisode: Episode): MediaDb {
            val createTime = Clock.System.now()
            return MediaDb(
                id = -1,
                gid = tvEpisode.id,
                rootId = tvShowRecord.id,
                rootGid = tvShowRecord.gid,
                parentId = tvSeasonRecord.id,
                parentGid = tvSeasonRecord.gid,
                title = tvEpisode.name,
                overview = tvEpisode.overview,
                tmdbId = tvEpisode.tmdbId,
                imdbId = null,
                runtime = null,
                index = tvEpisode.number,
                parentIndex = tvSeasonRecord.index,
                contentRating = null,
                posterPath = tvEpisode.stillPath,
                backdropPath = null,
                firstAvailableAt = tvEpisode.airDate?.tmdbDateToInstant(),
                createdAt = createTime,
                updatedAt = createTime,
                addedByUserId = tvShowRecord.addedByUserId,
                mediaKind = MediaKind.TV,
                mediaType = Type.TV_EPISODE,
            )
        }

        private fun Instant.instantToTmdbDate(): String {
            return toLocalDateTime(TimeZone.UTC).run { "$year-$monthNumber-$dayOfMonth" }
        }

        private fun String.tmdbDateToInstant(): Instant? {
            return split('-')
                .takeIf { it.size == 3 }
                ?.let { (year, month, day) ->
                    LocalDate(year.toInt(), month.toInt(), day.toInt()).atStartOfDayIn(TimeZone.UTC)
                }
        }
    }
}
