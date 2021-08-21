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
import anystream.frontend.models.MediaItem
import anystream.frontend.models.toMediaItem
import anystream.models.Episode
import anystream.models.MediaReference
import anystream.models.StreamEncodingDetails
import anystream.models.TvSeason
import anystream.models.api.*
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun MediaScreen(
    client: AnyStreamClient,
    mediaId: String,
) {
    val mediaResponse by produceState<MediaLookupResponse?>(null, mediaId) {
        value = try {
            client.lookupMedia(mediaId)
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
        mediaResponse?.movie?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem()
            )
        }

        mediaResponse?.tvShow?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem()
            )

            if (response.tvShow.seasons.isNotEmpty()) {
                SeasonRow(response.tvShow.seasons)
            }
        }

        mediaResponse?.season?.let { response ->
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

        mediaResponse?.episode?.let { response ->
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
                    window.location.hash = "!play:${mediaItem.mediaRefs.firstOrNull()?.id}"
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
            Div({
                classes("py-4")
            }) { Text(mediaItem.overview) }

            val videoStreams = mediaItem.mediaRefs.flatMap {
                it.streams.filterIsInstance<StreamEncodingDetails.Video>()
            }
            val audioStreams = mediaItem.mediaRefs.flatMap {
                it.streams.filterIsInstance<StreamEncodingDetails.Audio>()
            }
            val subtitlesStreams = mediaItem.mediaRefs.flatMap {
                it.streams.filterIsInstance<StreamEncodingDetails.Subtitle>()
            }

            Div({
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
            }) {
                videoStreams.take(1).forEach { details ->
                    EncodingDetailsItem(
                        title = { Text("Video") },
                        value = { Text("${details.width}x${details.height} (${details.codecName})") },
                    )
                }

                audioStreams.take(1).forEach { details ->
                    EncodingDetailsItem(
                        title = { Text("Audio") },
                        // TODO: Display language when possible
                        value = { Text("Unknown (${details.codecName})") },
                    )
                }

                subtitlesStreams.take(1).forEach { details ->
                    EncodingDetailsItem(
                        title = { Text("Subtitles") },
                        value = { Text(details.codecName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EncodingDetailsItem(
    title: @Composable () -> Unit,
    value: @Composable () -> Unit,
) {
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            gap(15.px)
        }
    }) {
        Div { H5 { title() } }
        Div { value() }
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
