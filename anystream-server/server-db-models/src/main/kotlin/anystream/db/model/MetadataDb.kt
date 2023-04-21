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

/**
 * A data class representing the metadata of various media entities such as movies, TV shows, episodes, seasons,
 * music artists, albums, etc. This class is used as a database model to store and manage metadata information.
 *
 * Metadata entries may represent a hierarchy of content such as TV Show > Seasons > Episodes.
 * The metadata hierarchy for TV episodes and seasons is organized as follows:
 * - A TV show has multiple seasons.
 * - Each season has multiple episodes.
 * - The TV show metadata contains the overall information about the show, such as title, overview, and content rating.
 * - The season metadata contains information specific to the season, such as its index within the show and poster.
 * - The episode metadata contains information specific to the episode, such as its index within the season and runtime.
 *
 * @property id Unique identifier for the metadata entry.
 * @property gid Globally unique identifier for the metadata entry.
 * @property rootId Unique identifier of the root entity in the hierarchy (e.g., the TV show for a season or episode).
 * @property rootGid Globally unique identifier of the root entity in the hierarchy.
 * @property parentId Unique identifier of the parent entity in the hierarchy (e.g., the season for an episode).
 * @property parentGid Globally unique identifier of the parent entity in the hierarchy.
 * @property title Title of the media entity.
 * @property overview Overview or description of the media entity.
 * @property tagline Tagline or subtitle of the media entity.
 * @property tmdbId The Movie Database (TMDb) identifier for the media entity.
 * @property imdbId Internet Movie Database (IMDb) identifier for the media entity.
 * @property runtime Runtime duration of the media entity in minutes.
 * @property index Index or position of the entity within its parent container (e.g., season index within a show).
 * @property parentIndex Index or position of the parent entity within its container (e.g., season index for an episode).
 * @property contentRating Content rating of the media entity (e.g., PG-13, TV-MA).
 * @property posterPath Path to the poster image file for the media entity.
 * @property backdropPath Path to the backdrop image file for the media entity.
 * @property firstAvailableAt Timestamp of when the media entity was first made available.
 * @property createdAt Timestamp of when the metadata entry was created in the database.
 * @property updatedAt Timestamp of when the metadata entry was last updated in the database.
 * @property addedByUserId Unique identifier of the user who added the metadata entry.
 * @property mediaKind The kind of media (e.g., video, audio) the entity belongs to.
 * @property mediaType The specific type of the media entity (e.g., movie, TV show, TV episode, TV season).
 * @property tmdbRating The Movie Database (TMDb) rating for the media entity.
 * @property genres List of genres associated with the media entity.
 * @property companies List of production companies associated with the media entity.
 */
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
