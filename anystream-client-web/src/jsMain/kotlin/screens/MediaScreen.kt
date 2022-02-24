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
package anystream.frontend.screens

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.frontend.components.*
import anystream.frontend.libs.PopperElement
import anystream.frontend.models.MediaItem
import anystream.frontend.models.toMediaItem
import anystream.frontend.util.ExternalClickMask
import anystream.models.*
import anystream.models.api.*
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.window
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val backdropImageUrl = MutableStateFlow<String?>(null)

@Composable
fun MediaScreen(
    client: AnyStreamClient,
    mediaId: String,
) {
    val lookupIdFlow = remember(mediaId) { MutableStateFlow<Int?>(null) }
    val refreshMetadata: () -> Unit = remember {
        { lookupIdFlow.update { (it ?: 0) + 1 } }
    }
    val mediaResponse by produceState<MediaLookupResponse?>(null, mediaId) {
        value = try {
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
                    value = client.refreshMetadata(mediaId)
                } catch (_: Throwable) {
                }
            }
    }
    DisposableEffect(mediaId) {
        onDispose { backdropImageUrl.value = null }
    }

    Div({ classes("d-flex", "flex-column", "h-100") }) {
        if (mediaResponse == null) {
            FullSizeCenteredLoader()
        }
        mediaResponse?.movie?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem().also { mediaItem ->
                    // TODO: Support tv show backdrops, requests extra images api call on media import
                    backdropImageUrl.value =
                        "https://image.tmdb.org/t/p/w1280/${mediaItem.backdropPath}"
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

            if (response.seasons.isNotEmpty()) {
                SeasonRow(response.seasons)
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
    Div({ classes("d-flex", "flex-row") }) {
        Div({ classes("d-flex", "flex-column", "align-items-center", "flex-shrink-0") }) {
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
                }.takeIf { mediaItem.mediaRefs.isNotEmpty() }
            )

            mediaItem.playbackState?.run {
                Div {
                    val remaining = remember(runtime, position) {
                        val remaining = (runtime - position).seconds
                        "${remaining.asFriendlyString()} left"
                    }
                    Text(remaining)
                }
            }
        }
        Div({ classes("d-flex", "flex-column", "flex-grow-1", "p-4") }) {
            Div({ classes("d-flex", "flex-row", "align-items-center") }) {
                Div({ classes("fs-3") }) { Text(mediaItem.contentTitle) }
                if (client.hasPermission(Permission.ManageCollection)) {
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
                Div({ classes("fs-5") }) { Text(subtitle1) }
            }
            mediaItem.subtitle2?.also { subtitle2 ->
                Div({ classes("fs-5") }) { Text(subtitle2) }
            }

            Div({ classes("d-flex", "flex-row", "align-items-center", "gap-3", "py-1") }) {
                mediaItem.releaseDate?.also { releaseDate ->
                    val year = releaseDate.split("-").firstOrNull { it.length == 4 }
                    if (year != null) {
                        Div({ classes("fs-6") }) { Text(year) }
                    }
                }
                mediaItem.runtime?.also { runtime ->
                    val runtimeString = remember(runtime) {
                        runtime.toDouble().toDuration(DurationUnit.MINUTES).asFriendlyString()
                    }
                    Div({ style { fontSize(13.px) } }) { Text(runtimeString) }
                }
            }

            Div({ classes("d-flex", "flex-row", "gap-2") }) {
                mediaItem.tmdbRating?.also { tmdbRating ->
                    Div({
                        classes("d-flex", "flex-row", "align-items-center", "gap-2")
                        style {
                            fontSize(14.px)
                        }
                    }) {
                        Img("/images/tmdb-small.svg") {
                            style { width(38.px); height(38.px) }
                        }
                        Div { Text("$tmdbRating%") }
                    }
                }
            }

            Div({ classes("d-flex", "flex-row", "py-3") }) {
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

            Div({ classes("d-flex", "flex-row", "gap-2") }) {
                mediaItem.genres.forEach { genre ->
                    Div({
                        classes("rounded-2", "p-2")
                        style {
                            fontSize(14.px)
                            backgroundColor(rgba(0, 0, 0, 0.3))
                        }
                    }) {
                        Text(genre.name)
                    }
                }
            }

            val videoStreams = mediaItem.mediaRefs.flatMap {
                it.streams.filterIsInstance<StreamEncodingDetails.Video>()
            }
            val audioStreams = mediaItem.mediaRefs.flatMap {
                it.streams.filterIsInstance<StreamEncodingDetails.Audio>()
            }
            val subtitlesStreams = mediaItem.mediaRefs.flatMap {
                it.streams.filterIsInstance<StreamEncodingDetails.Subtitle>()
            }

            Div({ classes("d-flex", "flex-column") }) {
                val selectedVideoStream = remember { mutableStateOf(videoStreams.firstOrNull()) }
                val selectedAudioStream = remember { mutableStateOf(audioStreams.firstOrNull()) }
                val selectedSubsStream = remember { mutableStateOf(subtitlesStreams.firstOrNull()) }

                @Suppress("UNCHECKED_CAST")
                EncodingDetailsItem(
                    selectedItem = selectedVideoStream as MutableState<StreamEncodingDetails?>,
                    title = { Text("Video") },
                    value = { stream ->
                        check(stream is StreamEncodingDetails.Video)
                        Text("${stream.width}x${stream.height} (${stream.codecName})")
                    },
                    items = videoStreams,
                )

                @Suppress("UNCHECKED_CAST")
                EncodingDetailsItem(
                    selectedItem = selectedAudioStream as MutableState<StreamEncodingDetails?>,
                    title = { Text("Audio") },
                    value = { stream ->
                        Text("${stream.languageName} (${stream.codecName})")
                    },
                    items = audioStreams,
                )

                @Suppress("UNCHECKED_CAST")
                EncodingDetailsItem(
                    selectedItem = selectedSubsStream as MutableState<StreamEncodingDetails?>,
                    title = { Text("Subtitles") },
                    value = { stream ->
                        Text("${stream.languageName} (${stream.codecName})")
                    },
                    items = subtitlesStreams,
                )
            }
        }
    }
}

@Composable
private fun EncodingDetailsItem(
    selectedItem: MutableState<StreamEncodingDetails?>,
    title: @Composable (stream: StreamEncodingDetails) -> Unit,
    value: @Composable (stream: StreamEncodingDetails) -> Unit,
    items: List<StreamEncodingDetails>,
) {
    var isListVisible by remember { mutableStateOf(false) }
    var element by remember { mutableStateOf<HTMLElement?>(null) }
    val menuClickMask = remember { mutableStateOf<ExternalClickMask?>(null) }
    Div({ classes("d-flex", "flex-row", "align-items-center", "py-1") }) {
        val item = selectedItem.value
        if (item != null) {
            Div({
                classes("pe-2")
                style {
                    fontSize(20.px)
                }
            }) { title(item) }
            Div({
                classes("d-flex", "flex-row", "align-items-center")
                onClick { event ->
                    event.stopImmediatePropagation()
                    isListVisible = !isListVisible
                    if (isListVisible) menuClickMask.value?.attachListener()
                }
                style {
                    if (items.size > 1) {
                        cursor("pointer")
                    }
                }
            }) {
                DisposableEffect(Unit) {
                    element = scopeElement
                    onDispose { element = null }
                }
                Span { value(item) }
                if (items.size > 1) {
                    I({
                        val menuIconDirection = if (isListVisible) "up" else "down"
                        classes("px-1", "bi", "bi-caret-$menuIconDirection-fill")
                        style {
                            fontSize(10.px)
                        }
                    })
                }
            }
        }

        if (items.size > 1) {
            element?.let { element ->
                StreamSelectionMenu(
                    element = element,
                    isVisible = isListVisible,
                    closeMenu = { isListVisible = false },
                    setSelectedItem = { selectedItem.value = it },
                    menuClickMask = menuClickMask,
                    items = (items - selectedItem.value).filterNotNull(),
                    value = value,
                )
            }
        }
    }
}

@Composable
private fun StreamSelectionMenu(
    element: HTMLElement,
    isVisible: Boolean,
    closeMenu: () -> Unit,
    setSelectedItem: (StreamEncodingDetails) -> Unit,
    menuClickMask: MutableState<ExternalClickMask?>,
    items: List<StreamEncodingDetails>,
    value: @Composable (stream: StreamEncodingDetails) -> Unit,
) {
    PopperElement(
        element,
        attrs = {
            style {
                if (isVisible) {
                    property("z-index", 100)
                } else {
                    property("z-index", -100)
                    opacity(0)
                    property("pointer-events", "none")
                }
            }
        }
    ) {
        Div({
            classes("d-flex", "flex-column", "p-2", "rounded")
            style {
                gap(8.px)
                backgroundColor(rgb(35, 36, 38))
            }
        }) {
            DisposableEffect(Unit) {
                menuClickMask.value = ExternalClickMask(scopeElement) { remove ->
                    closeMenu()
                    remove()
                }
                onDispose {
                    menuClickMask.value?.dispose()
                    menuClickMask.value = null
                }
            }
            items.forEach { stream ->
                Div({
                    onClick { setSelectedItem(stream) }
                    style {
                        cursor("pointer")
                    }
                }) {
                    value(stream)
                }
            }
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
        classes("d-flex", "flex-row")
        style {
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
