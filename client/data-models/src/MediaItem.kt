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

import anystream.models.api.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

data class MediaItem(
    val mediaId: String,
    val contentTitle: String,
    val subtitle1: String? = null,
    val subtitle2: String? = null,
    val overview: String,
    val releaseDate: Instant?,
    val mediaLinks: List<MediaLink>,
    val wide: Boolean = false,
    val playbackState: PlaybackState? = null,
    val genres: List<Genre> = emptyList(),
    val companies: List<ProductionCompany> = emptyList(),
    val cast: List<CastCredit> = emptyList(),
    val crew: List<CrewCredit> = emptyList(),
    val tmdbRating: Int? = null,
    val contentRating: String? = null,
    val runtime: Duration? = null,
    val parentMetadataId: String? = null,
    val rootMetadataId: String? = null,
    val mediaType: MediaType,
    val streamEncodings: Map<String, List<StreamEncoding>> = emptyMap(),
) {
    val playableMediaLink: MediaLink? =
        mediaLinks.firstOrNull {
            it.descriptor == Descriptor.VIDEO ||
                    it.descriptor == Descriptor.AUDIO
        }

    val releaseYear: String?
        get() = releaseDate
            ?.toLocalDateTime(TimeZone.currentSystemDefault())
            ?.year
            ?.toString()

    val hasStreamEncodings: Boolean =
        mediaLinks.isNotEmpty() &&
                streamEncodings.isNotEmpty() &&
                streamEncodings.values.first().isNotEmpty()

    val directors: List<CrewCredit> = crew.filter { it.job == CreditJob.DIRECTOR }
    val creators: List<CrewCredit> = crew.filter { it.job == CreditJob.CREATOR }
    val writers: List<CrewCredit> = crew.filter { it.job == CreditJob.WRITER }
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
        mediaId = movie.id,
        contentTitle = movie.title,
        overview = movie.overview,
        releaseDate = movie.releaseDate,
        mediaLinks = mediaLinks,
        playbackState = playbackState,
        genres = genres,
        companies = companies,
        crew = crew,
        cast = cast,
        tmdbRating = movie.tmdbRating,
        runtime = movie.runtime,
        contentRating = movie.contentRating,
        mediaType = MediaType.MOVIE,
        streamEncodings = streamEncodings,
    )
}

fun TvShowResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = tvShow.id,
        contentTitle = tvShow.name,
        overview = tvShow.overview,
        releaseDate = tvShow.firstAirDate,
        mediaLinks = mediaLinks,
        playbackState = playbackState,
        genres = genres,
        companies = companies,
        crew = crew,
        cast = cast,
        tmdbRating = tvShow.tmdbRating,
        contentRating = tvShow.contentRating,
        mediaType = MediaType.TV_SHOW,
    )
}

fun EpisodeResponse.toMediaItem(concise: Boolean = false): MediaItem {
    return MediaItem(
        mediaId = episode.id,
        contentTitle = show.name,
        overview = episode.overview,
        releaseDate = episode.airDate,
        mediaLinks = mediaLinks,
        genres = genres,
        companies = companies,
        crew = crew,
        cast = cast,
        playbackState = playbackState,
        wide = true,
        parentMetadataId = episode.seasonId,
        rootMetadataId = show.id,
        mediaType = MediaType.TV_EPISODE,
        streamEncodings = streamEncodings,
        subtitle1 = if (concise) {
            "S${episode.seasonNumber}"
        } else {
            "Season ${episode.seasonNumber}"
        },
        subtitle2 = if (concise) {
            "E${episode.number} - ${episode.name}"
        } else {
            "Episode ${episode.number} - ${episode.name}"
        },
    )
}

fun SeasonResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = season.id,
        contentTitle = show.name,
        subtitle1 = "Season ${season.seasonNumber}",
        overview = season.overview,
        releaseDate = season.airDate,
        mediaLinks = emptyList(),
        genres = genres,
        companies = companies,
        crew = crew,
        cast = cast,
        playbackState = playbackState,
        parentMetadataId = show.id,
        rootMetadataId = show.id,
        mediaType = MediaType.TV_SEASON,
        streamEncodings = streamEncodings,
    )
}

fun MoviesResponse.toMediaItems(): List<MediaItem> {
    return movies.map { movie ->
        MediaItem(
            mediaId = movie.id,
            contentTitle = movie.title,
            overview = movie.overview,
            releaseDate = movie.releaseDate,
            mediaLinks = listOfNotNull(mediaLinks[movie.id]),
            playbackState = null,
            tmdbRating = movie.tmdbRating,
            runtime = movie.runtime,
            contentRating = movie.contentRating,
            mediaType = MediaType.MOVIE,
        )
    }
}

fun TvShowsResponse.toMediaItems(): List<MediaItem> {
    return tvShows.map { tvShow ->
        MediaItem(
            mediaId = tvShow.id,
            contentTitle = tvShow.name,
            overview = tvShow.overview,
            releaseDate = tvShow.firstAirDate,
            mediaLinks = mediaLinks,
            playbackState = null,
            tmdbRating = tvShow.tmdbRating,
            contentRating = tvShow.contentRating,
            mediaType = MediaType.TV_SHOW,
        )
    }
}
