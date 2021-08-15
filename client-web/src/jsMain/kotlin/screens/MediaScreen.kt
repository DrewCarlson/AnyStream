/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.frontend.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import anystream.client.AnyStreamClient
import anystream.frontend.components.LinkedText
import anystream.frontend.components.PosterCard
import anystream.models.Episode
import anystream.models.MediaReference
import anystream.models.TvSeason
import anystream.models.api.EpisodeResponse
import anystream.models.api.MovieResponse
import anystream.models.api.SeasonResponse
import anystream.models.api.TvShowResponse
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun MediaScreen(
    client: AnyStreamClient,
    mediaId: String,
) {
    // TODO: Create a single polymorphic endpoint for media lookup
    val movieState by produceState<MovieResponse?>(null, mediaId) {
        value = try {
            client.getMovie(mediaId)
        } catch (e: Throwable) {
            null
        }
    }
    val showState by produceState<TvShowResponse?>(null, mediaId) {
        value = try {
            client.getTvShow(mediaId)
        } catch (e: Throwable) {
            null
        }
    }
    val seasonState by produceState<SeasonResponse?>(null, mediaId) {
        value = try {
            client.getSeason(mediaId)
        } catch (e: Throwable) {
            null
        }
    }
    val episodeState by produceState<EpisodeResponse?>(null, mediaId) {
        value = try {
            client.getEpisode(mediaId)
        } catch (e: Throwable) {
            null
        }
    }

    Div({
        classes("p-4")
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
        }
    }) {
        movieState?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem()
            )
        }

        showState?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem()
            )

            if (response.tvShow.seasons.isNotEmpty()) {
                SeasonRow(response.tvShow.seasons)
            }
        }

        seasonState?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem()
            )

            if (response.episodes.isNotEmpty()) {
                EpisodeGrid(
                    response.episodes,
                    response.mediaRefs,
                )
            }
        }

        episodeState?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem()
            )
        }
    }
}

@Composable
private fun BaseDetailsView(
    mediaItem: MediaItem,
) {
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
        }
    }) {
        Div({
            style {
                flexShrink(0)
            }
        }) {
            PosterCard(
                title = null,
                posterPath = mediaItem.posterPath,
                wide = mediaItem.wide,
                onPlayClicked = {
                    window.location.hash = "!play:${mediaItem.mediaRefs?.firstOrNull()?.id}"
                }.takeIf { !mediaItem.mediaRefs.isNullOrEmpty() }
            )
        }
        Div({
            classes("p-4")
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                flexGrow(1)
            }
        }) {
            Div { H3 { Text(mediaItem.contentTitle) } }
            mediaItem.subtitle1?.also { subtitle1 ->
                Div { H5 { Text(subtitle1) } }
            }
            mediaItem.subtitle2?.also { subtitle2 ->
                Div { H5 { Text(subtitle2) } }
            }
            mediaItem.releaseDate?.also { releaseDate ->
                val year = releaseDate.split("-").firstOrNull { it.length == 4 }
                if (year != null) {
                    Div { H6 { Text(year) } }
                }
            }
            Div { Text(mediaItem.overview) }
        }
    }
}

@Composable
private fun SeasonRow(seasons: List<TvSeason>) {
    BaseRow(
        title = { Text("${seasons.size} Seasons") },
        wrap = true
    ) {
        seasons.forEach { season ->
            PosterCard(
                title = {
                    LinkedText("/media/${season.id}") {
                        Text(season.name)
                    }
                },
                posterPath = season.posterPath,
                onBodyClicked = {
                    BrowserRouter.navigate("/media/${season.id}")
                }
            )
        }
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    mediaRefs: Map<String, MediaReference>,
) {
    BaseRow(
        title = { Text("${episodes.size} Episodes") },
        wrap = true
    ) {
        episodes.forEach { episode ->
            val ref = mediaRefs[episode.id]
            PosterCard(
                title = {
                    LinkedText("/media/${episode.id}") {
                        Text(episode.name)
                    }
                },
                subtitle1 = {
                    LinkedText("/media/${episode.id}") {
                        Text("Episode ${episode.number}")
                    }
                },
                posterPath = episode.stillPath,
                wide = true,
                onPlayClicked = if (ref == null) {
                    null
                } else {
                    { window.location.hash = "!play:${ref.id}" }
                },
                onBodyClicked = {
                    BrowserRouter.navigate("/media/${episode.id}")
                },
            )
        }
    }
}

@Composable
private fun BaseRow(
    title: @Composable () -> Unit,
    wrap: Boolean = false,
    buildItems: @Composable () -> Unit,
) {
    Div {
        H4({
            classes("px-3", "pt-3", "pb-1")
        }) {
            title()
        }
    }
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            if (wrap) {
                flexWrap(FlexWrap.Wrap)
            } else {
                property("overflow-x", "auto")
                property("scrollbar-width", "none")
            }
        }
    }) {
        buildItems()
    }
}

private data class MediaItem(
    val mediaId: String,
    val contentTitle: String,
    val subtitle1: String? = null,
    val subtitle2: String? = null,
    val posterPath: String?,
    val overview: String,
    val releaseDate: String?,
    val mediaRefs: List<MediaReference>?,
    val wide: Boolean = false,
)

private fun MovieResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = movie.id,
        contentTitle = movie.title,
        posterPath = movie.posterPath,
        overview = movie.overview,
        releaseDate = movie.releaseDate,
        mediaRefs = mediaRefs,
    )
}

private fun TvShowResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = tvShow.id,
        contentTitle = tvShow.name,
        posterPath = tvShow.posterPath,
        overview = tvShow.overview,
        releaseDate = tvShow.firstAirDate,
        mediaRefs = null,
    )
}

private fun EpisodeResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = episode.id,
        contentTitle = show.name,
        posterPath = episode.stillPath,
        overview = episode.overview,
        subtitle1 = "Season ${episode.seasonNumber}",
        subtitle2 = "Episode ${episode.number} · ${episode.name}",
        releaseDate = episode.airDate,
        mediaRefs = mediaRefs,
        wide = true,
    )
}

private fun SeasonResponse.toMediaItem(): MediaItem {
    return MediaItem(
        mediaId = season.id,
        contentTitle = show.name,
        posterPath = season.posterPath,
        subtitle1 = "Season ${season.seasonNumber}",
        overview = season.overview,
        releaseDate = season.airDate,
        mediaRefs = null,
    )
}
