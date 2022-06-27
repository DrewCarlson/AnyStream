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

data class MetadataDb(
    val id: Int,
    val gid: String,
    val rootId: Int? = null,
    val rootGid: String? = null,
    val parentId: Int? = null,
    val parentGid: String? = null,
    val title: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val runtime: Int? = null,
    val index: Int? = null,
    val parentIndex: Int? = null,
    val contentRating: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAvailableAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val addedByUserId: Int,
    val mediaKind: MediaKind,
    val mediaType: Type,
    val tmdbRating: Int? = null,
    val genres: List<Genre> = emptyList(),
    val companies: List<ProductionCompany> = emptyList(),
) {
    enum class Type {
        MOVIE,
        TV_SHOW,
        TV_EPISODE,
        TV_SEASON,
    }

    fun toMovieModel(): Movie {
        check(mediaType == Type.MOVIE) {
            "MetadataDb item '$gid' is of type '$mediaType', cannot convert to Movie model."
        }
        return Movie(
            id = id,
            gid = gid,
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
            tagline = tagline,
            tmdbRating = tmdbRating,
            genres = genres,
            companies = companies,
            contentRating = contentRating,
        )
    }

    fun toTvShowModel(): TvShow {
        check(mediaType == Type.TV_SHOW) {
            "MetadataDb item '$gid' is of type '$mediaType', cannot convert to TvShow model."
        }
        return TvShow(
            id = id,
            gid = gid,
            name = checkNotNull(title),
            tmdbId = tmdbId ?: -1,
            overview = overview.orEmpty(),
            firstAirDate = firstAvailableAt?.instantToTmdbDate(),
            posterPath = posterPath.orEmpty(),
            added = createdAt.epochSeconds,
            addedByUserId = addedByUserId,
            tagline = tagline,
            tmdbRating = tmdbRating,
            contentRating = contentRating,
        )
    }

    fun toTvEpisodeModel(): Episode {
        check(mediaType == Type.TV_EPISODE) {
            "MetadataDb item '$gid' is of type '$mediaType', cannot convert to TvEpisode model."
        }
        return Episode(
            id = id,
            gid = gid,
            showId = checkNotNull(rootGid),
            seasonId = checkNotNull(parentGid),
            name = checkNotNull(title),
            tmdbId = tmdbId ?: -1,
            overview = overview.orEmpty(),
            airDate = firstAvailableAt?.instantToTmdbDate(),
            number = checkNotNull(index),
            seasonNumber = checkNotNull(parentIndex),
            stillPath = posterPath.orEmpty(),
            tmdbRating = tmdbRating,
        )
    }

    fun toTvSeasonModel(): TvSeason {
        check(mediaType == Type.TV_SEASON) {
            "MetadataDb item '$gid' is of type '$mediaType', cannot convert to TvSeason model."
        }
        return TvSeason(
            id = id,
            gid = gid,
            name = checkNotNull(title),
            overview = overview.orEmpty(),
            seasonNumber = checkNotNull(index),
            airDate = firstAvailableAt?.instantToTmdbDate(),
            tmdbId = tmdbId ?: -1,
            posterPath = posterPath,
        )
    }

    companion object {
        fun fromMovie(movie: Movie): MetadataDb {
            val createTime = Clock.System.now()
            return MetadataDb(
                id = movie.id,
                gid = movie.gid,
                title = movie.title,
                overview = movie.overview,
                tmdbId = movie.tmdbId,
                imdbId = movie.imdbId,
                runtime = movie.runtime,
                posterPath = movie.posterPath,
                backdropPath = movie.backdropPath,
                firstAvailableAt = movie.releaseDate?.tmdbDateToInstant(),
                createdAt = createTime,
                updatedAt = createTime,
                addedByUserId = movie.addedByUserId,
                mediaKind = MediaKind.MOVIE,
                mediaType = Type.MOVIE,
                tagline = movie.tagline,
                tmdbRating = movie.tmdbRating,
                genres = emptyList(),
                contentRating = movie.contentRating,
            )
        }

        fun fromTvShow(tvShow: TvShow): MetadataDb {
            val createTime = Clock.System.now()
            return MetadataDb(
                id = tvShow.id,
                gid = tvShow.gid,
                title = tvShow.name,
                overview = tvShow.overview,
                tmdbId = tvShow.tmdbId,
                posterPath = tvShow.posterPath,
                firstAvailableAt = tvShow.firstAirDate?.tmdbDateToInstant(),
                createdAt = createTime,
                updatedAt = createTime,
                addedByUserId = tvShow.addedByUserId,
                mediaKind = MediaKind.TV,
                mediaType = Type.TV_SHOW,
                tagline = tvShow.tagline,
                tmdbRating = tvShow.tmdbRating,
                genres = emptyList(),
                contentRating = tvShow.contentRating,
            )
        }

        fun fromTvSeason(tvShowRecord: MetadataDb, tvSeason: TvSeason): MetadataDb {
            val createTime = Clock.System.now()
            return MetadataDb(
                id = tvSeason.id,
                gid = tvSeason.gid,
                rootId = tvShowRecord.id,
                rootGid = tvShowRecord.gid,
                parentId = tvShowRecord.id,
                parentGid = tvShowRecord.gid,
                title = tvSeason.name,
                overview = tvSeason.overview,
                tmdbId = tvSeason.tmdbId,
                index = tvSeason.seasonNumber,
                posterPath = tvSeason.posterPath,
                firstAvailableAt = tvSeason.airDate?.tmdbDateToInstant(),
                createdAt = createTime,
                updatedAt = createTime,
                addedByUserId = tvShowRecord.addedByUserId,
                mediaKind = MediaKind.TV,
                mediaType = Type.TV_SEASON,
                genres = emptyList(),
            )
        }

        fun fromTvEpisode(tvShowRecord: MetadataDb, tvSeasonRecord: MetadataDb, tvEpisode: Episode): MetadataDb {
            val createTime = Clock.System.now()
            return MetadataDb(
                id = tvEpisode.id,
                gid = tvEpisode.gid,
                rootId = tvShowRecord.id,
                rootGid = tvShowRecord.gid,
                parentId = tvSeasonRecord.id,
                parentGid = tvSeasonRecord.gid,
                title = tvEpisode.name,
                overview = tvEpisode.overview,
                tmdbId = tvEpisode.tmdbId,
                index = tvEpisode.number,
                parentIndex = tvSeasonRecord.index,
                posterPath = tvEpisode.stillPath,
                firstAvailableAt = tvEpisode.airDate?.tmdbDateToInstant(),
                createdAt = createTime,
                updatedAt = createTime,
                addedByUserId = tvShowRecord.addedByUserId,
                mediaKind = MediaKind.TV,
                mediaType = Type.TV_EPISODE,
                tmdbRating = tvShowRecord.tmdbRating,
                genres = emptyList(),
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
