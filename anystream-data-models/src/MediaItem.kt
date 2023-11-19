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
package anystream.models

import anystream.models.api.EpisodeResponse
import anystream.models.api.MovieResponse
import anystream.models.api.SeasonResponse
import anystream.models.api.TvShowResponse
import kotlin.time.Duration

data class MediaItem(
    val mediaId: String,
    val contentTitle: String,
    val subtitle1: String? = null,
    val subtitle2: String? = null,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String,
    val releaseDate: String?,
    val mediaLinks: List<MediaLink>,
    val wide: Boolean = false,
    val playbackState: PlaybackState? = null,
    val genres: List<Genre> = emptyList(),
    val tmdbRating: Int? = null,
    val contentRating: String? = null,
    val runtime: Int? = null,
) {
    val playableMediaLink: MediaLink? =
        mediaLinks.firstOrNull {
            it.descriptor == MediaLink.Descriptor.VIDEO ||
                it.descriptor == MediaLink.Descriptor.AUDIO
        }

    val tmdbBackdropUrl: String
        get() = "https://image.tmdb.org/t/p/w500$backdropPath"

    val tmdbPosterUrl: String
        get() = "https://image.tmdb.org/t/p/w500$posterPath"

    val releaseYear: String?
        get() = releaseDate?.split("-")?.firstOrNull()?.toString()
}

fun Duration.asFriendlyString(): String {
    return buildString {
        val hasHours = inWholeHours > 0
        val minutes = inWholeMinutes % 60
        if (hasHours) {
            append(inWholeHours)
            append(" hr")
        }
        if (minutes > 0) {
            if (hasHours) append(' ')
            append(minutes)
            append(" min")
        }
    }
}

fun MovieResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = movie.gid,
        contentTitle = movie.title,
        posterPath = movie.posterPath,
        backdropPath = movie.backdropPath,
        overview = movie.overview,
        releaseDate = movie.releaseDate,
        mediaLinks = mediaLinks,
        playbackState = playbackState,
        genres = movie.genres,
        tmdbRating = movie.tmdbRating,
        runtime = movie.runtime,
        contentRating = movie.contentRating,
    )
}

fun TvShowResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = tvShow.gid,
        contentTitle = tvShow.name,
        posterPath = tvShow.posterPath,
        backdropPath = tvShow.backdropPath,
        overview = tvShow.overview,
        releaseDate = tvShow.firstAirDate,
        mediaLinks = mediaLinks,
        playbackState = playbackState,
        genres = tvShow.genres,
        tmdbRating = tvShow.tmdbRating,
        contentRating = tvShow.contentRating,
    )
}

fun EpisodeResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = episode.gid,
        contentTitle = show.name,
        posterPath = episode.stillPath,
        backdropPath = null,
        overview = episode.overview,
        subtitle1 = "Season ${episode.seasonNumber}",
        subtitle2 = "Episode ${episode.number} · ${episode.name}",
        releaseDate = episode.airDate,
        mediaLinks = mediaLinks,
        wide = true,
        playbackState = playbackState,
    )
}

fun SeasonResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = season.gid,
        contentTitle = show.name,
        posterPath = season.posterPath,
        backdropPath = null,
        subtitle1 = "Season ${season.seasonNumber}",
        overview = season.overview,
        releaseDate = season.airDate,
        mediaLinks = emptyList(),
        playbackState = playbackState,
    )
}
