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
package anystream.screens

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.components.*
import anystream.libs.PopperElement
import anystream.libs.popperOptions
import anystream.models.*
import anystream.models.MediaItem
import anystream.models.api.MediaLookupResponse
import anystream.models.toMediaItem
import anystream.util.ExternalClickMask
import anystream.util.get
import anystream.util.tooltip
import app.softwork.routingcompose.Router
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val backdropImageUrl = MutableStateFlow<String?>(null)

@Composable
fun MediaScreen(mediaId: String) {
    val client = get<AnyStreamClient>()
    val lookupIdFlow = remember(mediaId) { MutableStateFlow<Int?>(null) }
    val analyzeFiles: () -> Unit = remember(lookupIdFlow) { { lookupIdFlow.update { (it ?: 0) + 1 } } }
    val mediaResponse by produceState<MediaLookupResponse?>(null, mediaId) {
        value = runCatching { client.lookupMedia(mediaId) }.getOrNull()

        val updateLock = Mutex()
        lookupIdFlow.filterNotNull().filter { !updateLock.isLocked }.collect {
            updateLock.withLock {
                try {
                    value?.mediaLinks
                        ?.filter { it.descriptor.isMediaFileLink() }
                        ?.forEach { mediaLink ->
                            client.analyzeMediaLink(mediaLink.gid)
                        }
                    value = client.lookupMedia(mediaId)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }
    }
    val onFixMatch: (() -> Unit)? = remember(mediaResponse) { null }
    DisposableEffect(mediaId) {
        onDispose { backdropImageUrl.value = null }
    }

    Div({
        classes("d-flex", "flex-column", "h-100")
        style { overflow("hidden scroll") }
    }) {
        if (mediaResponse == null) {
            FullSizeCenteredLoader()
        }
        mediaResponse?.movie?.let { response ->
            val mediaItem = remember(response) {
                response.toMediaItem().also {
                    backdropImageUrl.value = "https://image.tmdb.org/t/p/w1280/${it.backdropPath}"
                }
            }
            BaseDetailsView(
                mediaItem = mediaItem,
                analyzeFiles = analyzeFiles,
                onFixMatch = onFixMatch,
            )
        }

        mediaResponse?.tvShow?.let { response ->
            val mediaItem = remember(response) { response.toMediaItem() }
            BaseDetailsView(
                mediaItem = mediaItem,
                analyzeFiles = analyzeFiles,
                onFixMatch = onFixMatch,
            )

            if (response.seasons.isNotEmpty()) {
                SeasonRow(response.seasons)
            }
        }

        mediaResponse?.season?.let { response ->
            val mediaItem = remember(response) { response.toMediaItem() }
            BaseDetailsView(
                mediaItem = mediaItem,
                analyzeFiles = null,
                onFixMatch = onFixMatch,
            )

            if (response.episodes.isNotEmpty()) {
                EpisodeGrid(
                    response.episodes,
                    response.mediaLinks,
                )
            }
        }

        mediaResponse?.episode?.let { response ->
            val mediaItem = remember(response) { response.toMediaItem() }
            BaseDetailsView(
                mediaItem = mediaItem,
                analyzeFiles = analyzeFiles,
                onFixMatch = onFixMatch,
            )
        }
    }
}

@Composable
private fun BaseDetailsView(
    mediaItem: MediaItem,
    analyzeFiles: (() -> Unit)?,
    onFixMatch: (() -> Unit)?,
) {
    Div({ classes("d-flex") }) {
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
                    window.location.hash = "!play:${mediaItem.playableMediaLink?.gid}"
                }.takeUnless { mediaItem.playableMediaLink == null },
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
            Div({ classes("d-flex", "align-items-center") }) {
                Div({ classes("fs-3") }) { Text(mediaItem.contentTitle) }
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

            Div({ classes("d-flex", "gap-2") }) {
                mediaItem.contentRating?.also { contentRating ->
                    Div({ classes("d-flex", "align-items-center") }) {
                        Div({
                            classes("rounded-2", "p-1", "bg-dark-translucent")
                            style {
                                fontSize(14.px)
                            }
                        }) {
                            Text(contentRating)
                        }
                    }
                }
                mediaItem.tmdbRating?.also { tmdbRating ->
                    Div({
                        classes("d-flex", "align-items-center", "gap-2", "rounded-2", "p-1", "bg-dark-translucent")
                        style {
                            fontSize(14.px)
                        }
                    }) {
                        Img("/images/tmdb-small.svg") {
                            style {
                                width(21.px)
                                height(21.px)
                            }
                        }
                        Div { Text("$tmdbRating%") }
                    }
                }
            }

            Div({ classes("d-flex", "flex-row", "py-3", "gap-2") }) {
                val mediaLink = mediaItem.playableMediaLink
                if (mediaLink != null) {
                    Button({
                        classes("btn", "btn-sm", "btn-primary")
                        onClick {
                            window.location.hash = "!play:${mediaLink.gid}"
                        }
                    }) {
                        I({
                            classes("bi", "bi-play-fill", "pe-1")
                            style { property("pointer-events", "none") }
                        })
                        Text(if (mediaItem.playbackState == null) "Play" else "Resume")
                    }
                }

                var isMenuVisible by remember { mutableStateOf(false) }
                var overflowMenuButtonElement by remember { mutableStateOf<HTMLElement?>(null) }

                Button({
                    classes("btn", "btn-sm", "btn-dark")
                    tooltip("More", "top")
                    onClick { isMenuVisible = !isMenuVisible }
                }) {
                    DisposableEffect(Unit) {
                        overflowMenuButtonElement = scopeElement
                        onDispose { overflowMenuButtonElement = null }
                    }
                    I({
                        classes("bi", "bi-three-dots")
                        style { property("pointer-events", "none") }
                    })
                }
                if (isMenuVisible) {
                    overflowMenuButtonElement?.let { element ->
                        OptionsPopper(
                            element,
                            onAnalyzeFilesClicked = analyzeFiles,
                            onFixMatch = onFixMatch,
                            onClose = { isMenuVisible = false },
                        )
                    }
                }
            }

            Div({
                classes("pt-2", "pb-4")
            }) { Text(mediaItem.overview) }

            Div({ classes("d-flex", "flex-row", "gap-2") }) {
                mediaItem.genres.forEach { genre ->
                    Div({
                        classes("rounded-2", "p-2", "bg-dark-translucent")
                        style {
                            fontSize(14.px)
                        }
                    }) {
                        Text(genre.name)
                    }
                }
            }

            val hasStreamDetails = remember(mediaItem.mediaLinks) {
                mediaItem.mediaLinks.flatMap { it.streams }.isNotEmpty()
            }
            if (hasStreamDetails) {
                StreamDetails(mediaItem)
            }
        }
    }
}

@Composable
private fun StreamDetails(mediaItem: MediaItem) {
    val videoStreams = remember(mediaItem.mediaLinks) {
        mediaItem.mediaLinks.flatMap {
            it.streams.filterIsInstance<StreamEncodingDetails.Video>()
        }
    }
    val audioStreams = remember(mediaItem.mediaLinks) {
        mediaItem.mediaLinks.flatMap {
            it.streams.filterIsInstance<StreamEncodingDetails.Audio>()
        }
    }
    val subtitlesStreams = remember(mediaItem.mediaLinks) {
        mediaItem.mediaLinks.flatMap {
            it.streams.filterIsInstance<StreamEncodingDetails.Subtitle>()
        }
    }

    Div({ classes("d-flex", "flex-column") }) {
        val selectedVideoStream = remember(videoStreams) {
            mutableStateOf(videoStreams.firstOrNull { it.default } ?: videoStreams.firstOrNull())
        }
        val selectedAudioStream = remember(audioStreams) {
            mutableStateOf(audioStreams.firstOrNull { it.default } ?: audioStreams.firstOrNull())
        }
        val selectedSubsStream = remember(subtitlesStreams) {
            mutableStateOf(subtitlesStreams.firstOrNull { it.default })
        }

        EncodingDetailsItem(
            selectedItem = selectedVideoStream,
            title = { Text("Video") },
            value = { stream -> Text("${stream.width}x${stream.height} (${stream.codecName})") },
            items = videoStreams,
        )

        EncodingDetailsItem(
            selectedItem = selectedAudioStream,
            title = { Text("Audio") },
            value = { stream ->
                Text("${stream.languageName} (${stream.codecName} ${stream.channelLayout})")
            },
            items = audioStreams,
        )

        EncodingDetailsItem(
            selectedItem = selectedSubsStream,
            title = { Text("Subtitles") },
            value = { stream -> Text("${stream.languageName} (${stream.codecName})") },
            items = subtitlesStreams,
            allowDisable = true,
        )
    }
}

@Composable
private fun <T : StreamEncodingDetails> EncodingDetailsItem(
    selectedItem: MutableState<T?>,
    title: @Composable (stream: T?) -> Unit,
    value: @Composable (stream: T) -> Unit,
    items: List<T>,
    allowDisable: Boolean = false,
) {
    var isListVisible by remember { mutableStateOf(false) }
    var element by remember { mutableStateOf<HTMLElement?>(null) }
    val menuClickMask = remember { mutableStateOf<ExternalClickMask?>(null) }
    val canSelectStream = items.size > 1 || allowDisable
    Div({ classes("d-flex", "flex-row", "align-items-center", "py-1") }) {
        val item = selectedItem.value
        Div({
            classes("pe-2")
            style { fontSize(20.px) }
        }) { title(item) }
        Div({
            classes("d-flex", "flex-row", "align-items-center")
            onClick { event ->
                event.stopImmediatePropagation()
                isListVisible = !isListVisible
            }
            style { if (canSelectStream) cursor("pointer") }
        }) {
            DisposableEffect(Unit) {
                element = scopeElement
                onDispose { element = null }
            }
            Span {
                when {
                    items.isEmpty() -> Text("None")
                    item == null -> Text("Off")
                    else -> value(item)
                }
            }
            if (canSelectStream) {
                I({
                    val menuIconDirection = if (isListVisible) "up" else "down"
                    classes("px-1", "bi", "bi-caret-$menuIconDirection-fill")
                    style { fontSize(10.px) }
                })
            }
        }

        if (canSelectStream) {
            element?.let { element ->
                StreamSelectionMenu(
                    element = element,
                    isVisible = isListVisible,
                    closeMenu = { isListVisible = false },
                    setSelectedItem = {
                        selectedItem.value = it
                        isListVisible = false
                    },
                    menuClickMask = menuClickMask,
                    items = items,
                    value = value,
                    allowDisable = allowDisable,
                )
            }
        }
    }
}

@Composable
private fun <T : StreamEncodingDetails> StreamSelectionMenu(
    element: HTMLElement,
    isVisible: Boolean,
    closeMenu: () -> Unit,
    setSelectedItem: (T?) -> Unit,
    menuClickMask: MutableState<ExternalClickMask?>,
    items: List<T>,
    allowDisable: Boolean,
    value: @Composable (stream: T) -> Unit,
) {
    if (isVisible) {
        PopperElement(
            element,
            popperOptions("bottom-start"),
            attrs = { style { property("z-index", 100) } },
        ) { popper ->
            Div({ classes("rounded", "shadow", "animate-popup") }) {
                DisposableEffect(isVisible) {
                    popper.update()
                    menuClickMask.value = ExternalClickMask(scopeElement) { remove ->
                        closeMenu()
                        remove()
                    }.apply { attachListener() }
                    onDispose {
                        menuClickMask.value?.dispose()
                        menuClickMask.value = null
                    }
                }
                Ul({ classes("dropdown-menu", "dropdown-menu-dark", "show") }) {
                    if (allowDisable) {
                        Li {
                            A(null, {
                                classes("dropdown-item")
                                style { cursor("pointer") }
                                onClick { setSelectedItem(null) }
                            }) { Text("Off") }
                        }
                    }
                    items.forEach { stream ->
                        Li {
                            A(null, {
                                classes("dropdown-item")
                                style { cursor("pointer") }
                                onClick { setSelectedItem(stream) }
                            }) { value(stream) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonRow(seasons: List<TvSeason>) {
    val router = Router.current
    BaseRow(
        title = { Text("${seasons.size} Seasons") },
        wrap = true,
    ) {
        seasons.forEach { season ->
            PosterCard(
                title = {
                    LinkedText("/media/${season.gid}") {
                        Text(season.name)
                    }
                },
                posterPath = season.posterPath,
                onBodyClicked = {
                    router.navigate("/media/${season.gid}")
                },
            )
        }
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    mediaLinks: Map<String, MediaLink>,
) {
    val router = Router.current
    BaseRow(
        title = { Text("${episodes.size} Episodes") },
        wrap = true,
    ) {
        episodes.forEach { episode ->
            val link = mediaLinks[episode.gid]
            PosterCard(
                title = {
                    LinkedText("/media/${episode.gid}") {
                        Text(episode.name)
                    }
                },
                subtitle1 = {
                    LinkedText("/media/${episode.gid}") {
                        Text("Episode ${episode.number}")
                    }
                },
                posterPath = episode.stillPath,
                heightAndWidth = 178.px to 318.px,
                onPlayClicked = if (link == null) {
                    null
                } else {
                    { window.location.hash = "!play:${link.gid}" }
                },
                onBodyClicked = {
                    router.navigate("/media/${episode.gid}")
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

@Composable
private fun OptionsPopper(
    element: HTMLElement,
    onDelete: (() -> Unit)? = null,
    onViewInfo: (() -> Unit)? = null,
    onFixMatch: (() -> Unit)? = null,
    onRefreshMetadata: (() -> Unit)? = null,
    onAnalyzeFilesClicked: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var globalClickHandler by remember { mutableStateOf<ExternalClickMask?>(null) }
    PopperElement(
        element,
        popperOptions(placement = "bottom-start"),
        attrs = { style { property("z-index", 100) } },
    ) { popper ->
        Div({ classes("bg-dark", "rounded", "shadow", "animate-popup") }) {
            DisposableEffect(Unit) {
                globalClickHandler = ExternalClickMask(scopeElement) { remove ->
                    onClose()
                    remove()
                }
                globalClickHandler?.attachListener()
                onDispose {
                    globalClickHandler?.dispose()
                    globalClickHandler = null
                }
            }
            Ul({ classes("dropdown-menu", "dropdown-menu-dark", "show") }) {
                if (onRefreshMetadata != null) {
                    Li {
                        A(null, {
                            classes("dropdown-item", "fs-6")
                            style { cursor("pointer") }
                            onClick { onRefreshMetadata() }
                        }) {
                            Text("Refresh Metadata")
                        }
                    }
                }
                if (onAnalyzeFilesClicked != null) {
                    Li {
                        A(null, {
                            classes("dropdown-item", "fs-6")
                            style { cursor("pointer") }
                            onClick { onAnalyzeFilesClicked() }
                        }) {
                            Text("Analyze Files")
                        }
                    }
                }
                if (onFixMatch != null) {
                    Li {
                        A(null, {
                            classes("dropdown-item", "fs-6")
                            style { cursor("pointer") }
                            onClick { onFixMatch() }
                        }) {
                            Text("Fix Match")
                        }
                    }
                }
                if (onDelete != null) {
                    Li {
                        A(null, {
                            classes("dropdown-item", "fs-6")
                            style { cursor("pointer") }
                            onClick { }
                        }) {
                            Text("Delete")
                        }
                    }
                }
                if (onViewInfo != null) {
                    Li {
                        A(null, {
                            classes("dropdown-item", "fs-6")
                            style { cursor("pointer") }
                            onClick { }
                        }) {
                            Text("View Info")
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { popper.update() }
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
