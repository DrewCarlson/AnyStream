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

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.frontend.components.LinkedText
import anystream.frontend.components.PosterCard
import anystream.frontend.models.MediaItem
import anystream.frontend.models.toMediaItem
import anystream.models.*
import anystream.models.api.*
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.window
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.time.Duration

val backdropImageUrl = MutableStateFlow<String?>(null)

@Composable
fun MediaScreen(
    client: AnyStreamClient,
    mediaId: String,
) {
    val lookupIdFlow = remember(mediaId) { MutableStateFlow<Int?>(null) }
    val refreshMetadata: () -> Unit = { lookupIdFlow.update { (it ?: 0) + 1 } }
    var mediaResponse by remember(mediaId) { mutableStateOf<MediaLookupResponse?>(null) }
    LaunchedEffect(mediaId) {
        mediaResponse = try {
            client.lookupMedia(mediaId)
        } catch (e: Throwable) {
            null
        }
        lookupIdFlow
            .filterNotNull()
            .debounce(1_000L)
            .collect {
                try {
                    client.refreshStreamDetails(mediaId)
                    mediaResponse = client.refreshMetadata(mediaId)
                } catch (_: Throwable) {
                }
            }
    }
    DisposableEffect(mediaId) {
        onDispose { backdropImageUrl.value = null }
    }

    Div({
        classes("h-100")
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
        }
    }) {
        mediaResponse?.movie?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem().also { mediaItem ->
                    // TODO: Support tv show backdrops, requests extra images api call on media import
                    backdropImageUrl.value = "https://image.tmdb.org/t/p/w1280/${mediaItem.backdropPath}"
                },
                refreshMetadata = refreshMetadata,
                client = client,
            )
        }

        mediaResponse?.tvShow?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem(),
                refreshMetadata = refreshMetadata,
                client = client,
            )

            if (response.tvShow.seasons.isNotEmpty()) {
                SeasonRow(response.tvShow.seasons)
            }
        }

        mediaResponse?.season?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem(),
                refreshMetadata = refreshMetadata,
                client = client,
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
                mediaItem = response.toMediaItem(),
                refreshMetadata = refreshMetadata,
                client = client,
            )
        }
    }
}

@Composable
private fun BaseDetailsView(
    mediaItem: MediaItem,
    refreshMetadata: () -> Unit,
    client: AnyStreamClient,
) {
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
        }
    }) {
        Div({
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                flexShrink(0)
                alignItems(AlignItems.Center)
            }
        }) {
            PosterCard(
                title = null,
                posterPath = mediaItem.posterPath,
                wide = mediaItem.wide,
                heightAndWidth = if (mediaItem.wide) {
                    168.px to 300.px
                } else {
                    375.px to 250.px
                },
                completedPercent = mediaItem.playbackState?.completedPercent,
                onPlayClicked = {
                    window.location.hash = "!play:${mediaItem.mediaRefs.firstOrNull()?.id}"
                }.takeIf { !mediaItem.mediaRefs.isNullOrEmpty() }
            )

            mediaItem.playbackState?.run {
                Div {
                    Text(buildString {
                        val remaining = Duration.seconds(runtime - position)
                        if (remaining.inWholeHours >= 1) {
                            append(remaining.inWholeHours)
                            append(" hr ")
                        }
                        append(remaining.inWholeMinutes % 60)
                        append(" min left")
                    })
                }
            }
        }
        Div({
            classes("p-4")
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                flexGrow(1)
            }
        }) {
            Div({
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Row)
                    alignItems(AlignItems.Center)
                }
            }) {
                H3 { Text(mediaItem.contentTitle) }
                if (client.hasPermission(Permissions.MANAGE_COLLECTION)) {
                    I({
                        classes("bi", "bi-arrow-repeat", "p-1")
                        style {
                            cursor("pointer")
                            onClick { refreshMetadata() }
                        }
                    })
                }
            }
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
                classes("py-2")
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Row)
                }
            }) {
                // TODO: Allow user mediaRef selection, order refs on server
                val mediaRef = mediaItem.mediaRefs.firstOrNull()
                if (mediaRef != null) {
                    Button({
                        classes("btn", "btn-primary")
                        style {
                            backgroundColor(rgb(199, 8, 28))
                            property("border-color", rgb(199, 8, 28))
                        }
                        onClick {
                            window.location.hash = "!play:${mediaRef.id}"
                        }
                    }) {
                        I({
                            classes("bi", "bi-play-fill", "pe-1")
                            style { property("pointer-events", "none") }
                        })
                        Text(if (mediaItem.playbackState == null) "Play" else "Resume")
                    }
                }
            }

            Div({
                classes("pt-2", "pb-4")
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
                heightAndWidth = 178.px to 318.px,
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
