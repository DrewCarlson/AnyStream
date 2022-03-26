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
import anystream.frontend.LocalAnyStreamClient
import anystream.frontend.libs.*
import anystream.frontend.models.MediaItem
import anystream.frontend.models.toMediaItem
import anystream.models.PlaybackState
import anystream.util.formatProgressAndRuntime
import anystream.util.formatted
import app.softwork.routingcompose.BrowserRouter
import io.ktor.client.fetch.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

private val playerControlsColor = rgba(35, 36, 38, .45)

private const val CONTROL_HIDE_DELAY = 2_750L

@Composable
fun PlayerScreen(mediaRefId: String) {
    val client = LocalAnyStreamClient.current
    var player: VjsPlayer? by remember { mutableStateOf(null) }
    var playerIsPlaying by remember { mutableStateOf(false) }
    val isInMiniMode = remember { mutableStateOf(false) }
    val mouseMoveFlow = remember {
        MutableSharedFlow<Unit>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val isMouseOnPlayer by mouseMoveFlow
        .transformLatest {
            emit(true)
            delay(CONTROL_HIDE_DELAY)
            emit(false)
        }
        .distinctUntilChanged()
        .collectAsState(false)
    val areControlsVisible by derivedStateOf {
        isMouseOnPlayer || !playerIsPlaying
    }
    var sessionHandle by remember { mutableStateOf<AnyStreamClient.PlaybackSessionHandle?>(null) }
    val mediaItem = produceState<MediaItem?>(null) {
        value = try {
            client.lookupMediaByRefId(mediaRefId).run {
                movie?.toMediaItem() ?: episode?.toMediaItem()
            }
        } catch (e: Throwable) {
            null
        }
    }
    val playbackState by produceState<PlaybackState?>(null) {
        var initialState: PlaybackState? = null
        sessionHandle = client.playbackSession(mediaRefId) { state ->
            println("[player] $state")
            if (initialState == null) {
                initialState = state
                player?.currentTime(state.position)
            }
            value = state
        }
        awaitDispose {
            sessionHandle?.cancel?.invoke()
            sessionHandle = null
        }
    }
    val isFullscreen = remember { mutableStateOf(document.fullscreenElement != null) }
    val setFullscreen = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var duration by remember { mutableStateOf(1.0) }
    var progress by remember { mutableStateOf(0.0) }
    val progressScale = derivedStateOf { progress / duration }
    val bufferedProgress = remember { mutableStateOf(0.0) }
    Div({
        classes("w-100")
        if (isInMiniMode.value) {
            classes("d-flex")
        } else {
            classes("position-absolute", "h-100", "overflow-hidden")
        }
        style {
            property("z-index", 100)
            backgroundColor(playerControlsColor)
        }
        ref { element ->
            setFullscreen.value = { fullscreen ->
                if (fullscreen) element.requestFullscreen() else document.exitFullscreen()
                isFullscreen.value = fullscreen
            }
            onDispose {
                setFullscreen.value = null
            }
        }
    }) {
        if (isInMiniMode.value) {
            Div({
                classes("position-absolute", "w-100")
                style {
                    left(0.px)
                }
            }) {
                player?.also { player ->
                    SeekBar(player, mediaRefId, progressScale, bufferedProgress)
                }
            }
        }

        val miniPlayerHeight = remember { mutableStateOf(100) }
        val miniPlayerWidth = derivedStateOf { (miniPlayerHeight.value * 1.7777f).toInt() }
        Div({
            if (isInMiniMode.value) {
                classes("flex-shrink-0", "m-2", "shadow")
                style {
                    width(miniPlayerWidth.value.px)
                    height(miniPlayerHeight.value.px)
                }
            } else {
                classes("h-100", "w-100")
            }
        }) {
            if (isInMiniMode.value) {
                MiniModeOverlay(
                    isInMiniMode = isInMiniMode,
                    miniPlayerHeight = miniPlayerHeight,
                    miniPlayerWidth = miniPlayerWidth,
                )
            }
            val posterPath by derivedStateOf {
                "https://image.tmdb.org/t/p/w1920_and_h800_multi_faces${mediaItem.value?.backdropPath}"
            }
            Video({
                id("player")
                classes("video-js", "h-100", "w-100")
                style {
                    cursor(if (areControlsVisible) "pointer" else "none")
                }
                attr("poster", posterPath)
                onClick {
                    if (player?.paused() == true) {
                        player?.play()
                    } else {
                        player?.pause()
                    }
                }
                onTouchStart { mouseMoveFlow.tryEmit(Unit) }
                onTouchMove { mouseMoveFlow.tryEmit(Unit) }
                onDoubleClick {
                    setFullscreen.value?.invoke(document.fullscreenElement == null)
                }
                ref { element ->
                    window.document.onmousemove = {
                        if (!isInMiniMode.value) {
                            mouseMoveFlow.tryEmit(Unit)
                        }
                    }
                    player = VideoJs.default(
                        element,
                        VjsOptions {
                            errorDisplay = false
                            controlBar = false
                            loadingSpinner = false
                        }
                    )
                    element.onprogress = {
                        val timeSpans = List(element.buffered.length) { i ->
                            element.buffered.start(i)..element.buffered.end(i)
                        }

                        val closestBufferProgress = timeSpans
                            .filter { span -> element.currentTime in span }
                            .maxOfOrNull { span -> span.endInclusive }
                            ?.div(element.duration)

                        bufferedProgress.value = closestBufferProgress ?: 0.0
                        true
                    }
                    element.onloadedmetadata = {
                        duration = player?.duration() ?: 1.0
                        element.currentTime = playbackState?.position ?: 0.0
                        true
                    }
                    element.ontimeupdate = {
                        sessionHandle?.update?.tryEmit(element.currentTime)
                        progress = player?.currentTime() ?: 0.0
                        true
                    }
                    element.onplay = {
                        playerIsPlaying = true
                        true
                    }
                    element.onpause = {
                        playerIsPlaying = false
                        true
                    }

                    onDispose {
                        player = null
                        element.ontimeupdate = null
                        element.onloadedmetadata = null
                    }
                }
            }) {
                LaunchedEffect(playbackState?.id, player) {
                    playbackState?.also { state ->
                        val url = client.createHlsStreamUrl(mediaRefId, state.id)
                        println("[player] $url")
                        player?.src(url)
                        player?.play()
                    }
                }
            }
        }

        if (!isInMiniMode.value) {
            MaxPlayerTopBar(
                isInMiniMode,
                areControlsVisible,
                isFullscreen,
                setFullscreen,
            )

            if (!playerIsPlaying) {
                I({
                    classes(
                        "bi",
                        "bi-play-circle-fill",
                        "user-select-none",
                        "position-absolute",
                        "start-50",
                        "top-50",
                    )
                    onClick { if (player?.paused() == true) player?.play() else player?.pause() }
                    style {
                        fontSize(82.px)
                        cursor("pointer")
                        property("transform", "translate(-50%, -50%)")
                        color(rgba(199, 8, 28, 0.8))
                    }
                })
                I({
                    classes("bi", "bi-play-circle", "user-select-none")
                    classes("position-absolute", "start-50", "top-50")
                    onClick { if (player?.paused() == true) player?.play() else player?.pause() }
                    style {
                        fontSize(82.px)
                        color(Color.white)
                        cursor("pointer")
                        property("transform", "translate(-50%, -50%)")
                    }
                })
            }
        }

        player?.also { currentPlayer ->
            PlaybackControls(
                areControlsVisible = areControlsVisible,
                player = currentPlayer,
                mediaRefId = mediaRefId,
                mediaItem = mediaItem,
                progressScale = progressScale,
                bufferedPercent = bufferedProgress,
                overlayMode = !isInMiniMode.value,
            )
        }
    }
}

@Composable
private fun MiniModeOverlay(
    isInMiniMode: MutableState<Boolean>,
    miniPlayerHeight: State<Int>,
    miniPlayerWidth: State<Int>,
) {
    var isVisible by remember { mutableStateOf(false) }
    Div({
        classes("position-absolute", "d-flex", "justify-content-center", "align-items-center")
        style {
            property("z-index", "1")
            width(miniPlayerWidth.value.px)
            height(miniPlayerHeight.value.px)
            backgroundColor(rgba(0, 0, 0, .6))
            cursor("pointer")
            opacity(if (isVisible) 100 else 0)
        }
        onMouseEnter { isVisible = true }
        onMouseLeave { isVisible = false }
        onClick {
            isInMiniMode.value = false
        }
    }) {
        I({ classes("bi", "bi-chevron-up", "user-select-none") })
    }
}

@Composable
private fun MaxPlayerTopBar(
    isInMiniMode: MutableState<Boolean>,
    areControlsVisible: Boolean,
    isFullscreen: State<Boolean>,
    setFullscreen: State<((Boolean) -> Unit)?>,
) {
    Div({
        classes(
            "position-absolute",
            "d-flex",
            "flex-row",
            "justify-content-between",
            "align-items-center",
            "w-100",
            "p-3",
            "top-0",
        )
        style {
            height(60.px)
            backgroundColor(playerControlsColor)
            fontSize(20.px)
            property("transition", "transform .2s,background .2s")
            if (!areControlsVisible) {
                property("transform", "translateY(-100%)")
            }
        }
    }) {
        Div({
            style {
                cursor("pointer")
                if (isFullscreen.value) {
                    opacity(0)
                }
            }
            onClick {
                isInMiniMode.value = !isInMiniMode.value
            }
        }) {
            I({
                classes("bi", "bi-chevron-down", "user-select-none")
                style {
                    property("pointer-events", "none")
                }
            })
        }
        Div({
            style {
                cursor("pointer")
            }
            onClick {
                setFullscreen.value?.invoke(!isFullscreen.value)
            }
        }) {
            I({
                classes(
                    "user-select-none",
                    "bi",
                    if (isFullscreen.value) {
                        "bi-arrows-angle-contract"
                    } else {
                        "bi-arrows-angle-expand"
                    },
                )
                style {
                    property("pointer-events", "none")
                }
            })
        }
    }
}

@Composable
private fun PlaybackControls(
    areControlsVisible: Boolean,
    player: VjsPlayer,
    mediaRefId: String,
    mediaItem: State<MediaItem?>,
    progressScale: State<Double>,
    bufferedPercent: State<Double>,
    overlayMode: Boolean,
) {
    Div({
        classes("d-flex", "justify-content-between", "align-items-center", "w-100", "p-3")
        style {
            if (overlayMode) {
                position(Position.Absolute)
                height(100.px)
                bottom((-100).px)
                backgroundColor(playerControlsColor)
                property("transition", "transform .2s,background .2s")
                if (areControlsVisible) {
                    property("transform", "translateY(-100%)")
                } else {
                    property("transform", "translateY(0)")
                }
            }
        }
    }) {
        Div({
            classes("d-flex", "flex-column")
            style {
                flexBasis(33.percent)
            }
        }) {
            var hovering by remember { mutableStateOf(false) }
            Div({
                onMouseEnter { hovering = true }
                onMouseLeave { hovering = false }
                onClick {
                    BrowserRouter.navigate("/media/${mediaItem.value?.mediaId}")
                }
                style {
                    cursor("pointer")
                    if (hovering) {
                        textDecoration("underline")
                    }
                }
            }) {
                mediaItem.value?.run {
                    Text(contentTitle)
                }
            }
            Div({
                classes("d-flex", "justify-content-start", "align-items-start")
                style {
                    gap(10.px)
                }
            }) {
                mediaItem.value?.apply {
                    if (!subtitle1.isNullOrBlank() && !subtitle2.isNullOrBlank()) {
                        Text("$subtitle1 · $subtitle2")
                    } else {
                        subtitle1?.also { subtitle -> Text(subtitle) }
                        subtitle2?.also { subtitle -> Text(subtitle) }
                    }
                }
            }
            val playProgressString = remember(progressScale.value) {
                if (player.currentTime().isNaN() || player.duration().isNaN()) {
                    return@remember ""
                }
                val currentTime = player.currentTime().seconds
                val duration = player.duration().seconds
                formatProgressAndRuntime(currentTime, duration)
            }
            Div {
                Div { Text(playProgressString) }
            }
        }

        Div({
            classes("d-flex", "justify-content-center", "fs-3")
            style {
                flexBasis(33.percent)
                gap(6.px)
            }
        }) {
            Div({
                style {
                    property("pointer-events", "none")
                    opacity(0)
                }
            }) { I({ classes("bi", "bi-skip-start-fill", "user-select-none") }) }
            Div({
                style {
                    cursor("pointer")
                    opacity(0.6)
                }
                onClick { }
            }) {
                I({
                    classes("bi", "bi-skip-start-fill", "user-select-none")
                    style {
                        property("pointer-events", "none")
                    }
                })
            }

            var isPlaying by remember { mutableStateOf(!player.paused()) }
            Div({
                style {
                    cursor("pointer")
                }
                onClick {
                    if (isPlaying) player.pause() else player.play()
                }
                player.on("play") {
                    isPlaying = true
                }
                player.on("pause") {
                    isPlaying = false
                }
            }) {
                I({
                    classes(
                        "user-select-none", "bi",
                        if (isPlaying) "bi-pause-fill" else "bi-play-fill"
                    )
                    style {
                        property("pointer-events", "none")
                    }
                })
            }

            Div({
                style {
                    cursor("pointer")
                    opacity(0.6)
                }
                onClick { }
            }) {
                I({
                    classes("bi", "bi-skip-end-fill", "user-select-none")
                    style {
                        property("pointer-events", "none")
                    }
                })
            }

            Div({
                style {
                    cursor("pointer")
                }
                onClick {
                    window.location.hash = "!close"
                }
            }) {
                I({
                    classes("bi", "bi-stop-fill", "user-select-none")
                    style {
                        property("pointer-events", "none")
                    }
                })
            }
        }

        var isPipAvailable by remember { mutableStateOf(false) }
        var isInPipMode by remember { mutableStateOf(false) }
        Div({
            classes("d-flex", "flex-row-reverse", "align-items-center")
            style {
                flexBasis(33.percent)
                fontSize(20.px)
                padding(12.px)
                gap(6.px)
            }
            ref {
                player.on("loadedmetadata") {
                    isPipAvailable = document["pictureInPictureEnabled"] == true
                }
                player.on("enterpictureinpicture") {
                    isInPipMode = true
                }
                player.on("leavepictureinpicture") {
                    isInPipMode = false
                }
                onDispose { }
            }
        }) {
            if (isPipAvailable) {
                Div({
                    style {
                        cursor("pointer")
                    }
                    onClick {
                        if (isInPipMode) {
                            player.exitPictureInPicture()
                        } else {
                            player.requestPictureInPicture()
                        }
                    }
                }) {
                    I({
                        classes(
                            "user-select-none",
                            "bi", if (isInPipMode) "bi-pip-fill" else "bi-pip"
                        )
                        style {
                            property("pointer-events", "none")
                        }
                    })
                }
            }

            var muted by remember { mutableStateOf(player.muted()) }
            var volume by remember { mutableStateOf(player.volume()) }
            val volumeScale by derivedStateOf { if (muted) 0f else volume }
            Div({
                style {
                    cursor("pointer")
                    height(50.px)
                    width(2.px)
                    paddingLeft(4.px)
                    paddingRight(4.px)
                }
                onClick { event ->
                    val elementHeight = (event.target as HTMLDivElement).clientHeight
                    val percent = (elementHeight - event.offsetY) / elementHeight
                    player.volume(percent.toFloat())
                    volume = player.volume()
                }
                var isMouseDown by mutableStateOf(false)
                onMouseUp { isMouseDown = false }
                onMouseOut { isMouseDown = false }
                onMouseDown { isMouseDown = true }
                onMouseMove { event ->
                    if (isMouseDown) {
                        val elementHeight = (event.target as HTMLDivElement).clientHeight
                        val percent = (elementHeight - event.offsetY) / elementHeight
                        player.volume(percent.toFloat())
                        volume = player.volume()
                    }
                }
            }) {
                Div({
                    classes("position-absolute")
                    style {
                        height(50.px)
                        backgroundColor(Color.white)
                        width(2.px)
                        property("pointer-events", "none")
                    }
                })
                Div({
                    classes("position-absolute")
                    style {
                        height(50.px)
                        backgroundColor(rgb(199, 8, 28))
                        width(2.px)
                        property("transform", "scaleY($volumeScale)")
                        property("transform-origin", "bottom")
                        property("pointer-events", "none")
                    }
                })
            }

            Div({
                style {
                    cursor("pointer")
                }
                onClick {
                    muted = !player.muted()
                    player.muted(muted)
                }
                ref {
                    player.on("volumechange") {
                        volume = player.volume()
                        muted = player.muted()
                    }
                    onDispose { }
                }
            }) {
                I({
                    val icon = when {
                        muted -> "bi-volume-mute-fill"
                        volume == 0f -> "bi-volume-off-fill"
                        volume < 0.5f -> "bi-volume-down-fill"
                        else -> "bi-volume-up-fill"
                    }
                    classes("bi", icon, "user-select-none", "fs-4")
                    style {
                        property("pointer-events", "none")
                    }
                })
            }
        }

        if (overlayMode) {
            Div({
                classes("position-absolute", "w-100", "h-auto", "top-0", "start-0")
                style {
                    if (!areControlsVisible) {
                        property("transform", "translateY(100%)")
                    }
                }
            }) {
                SeekBar(
                    player = player,
                    mediaRefId = mediaRefId,
                    progressScale = progressScale,
                    bufferedPercent = bufferedPercent,
                )
            }
        }
    }
}

@Composable
private fun SeekBar(
    player: VjsPlayer,
    mediaRefId: String,
    progressScale: State<Double>,
    bufferedPercent: State<Double>,
) {
    val client = LocalAnyStreamClient.current
    var isThumbVisible by remember { mutableStateOf(false) }
    var isMouseDown by remember { mutableStateOf(false) }
    var mouseHoverX by remember { mutableStateOf(0) }
    var mouseHoverProgress by remember { mutableStateOf(ZERO) }
    Div({
        classes("w-100")
        style {
            paddingTop(8.px)
            paddingBottom(8.px)
            cursor("pointer")
            property("transform", "translateY(-50%)")
        }
        onClick { event ->
            val percent = event.offsetX / (event.target as HTMLDivElement).clientWidth
            player.currentTime(player.duration() * percent.toFloat())
        }

        onMouseMove { event ->
            val percent = event.offsetX / (event.target as HTMLDivElement).clientWidth
            val progress = player.duration() * percent
            mouseHoverProgress = if (progress.isNaN()) ZERO else progress.seconds
            mouseHoverX = event.offsetX.toInt()
            isThumbVisible = player.duration() > 0
        }
        onMouseEnter {
            isThumbVisible = player.duration() > 0
        }
        onMouseLeave {
            isThumbVisible = false
            isMouseDown = false
        }
        onMouseUp { isMouseDown = false }
        onMouseDown { isMouseDown = true }
        onMouseMove { event ->
            if (isMouseDown) {
                val percent = event.offsetX / (event.target as HTMLDivElement).clientWidth
                player.currentTime(player.duration() * percent.toFloat())
            }
        }
    }) {
        I({
            classes("position-absolute", "bi", "bi-circle-fill", "user-select-none")
            style {
                opacity(if (isThumbVisible) 1 else 0)
                color(Color.white)
                marginLeft((progressScale.value * 100).percent)
                fontSize(12.px)
                property("z-index", "1")
                property("transform", "translate(-50%, -50%)")
                property("pointer-events", "none")
                property("transition", "opacity 0.15s ease-in-out")
            }
        })
        Div({
            classes("w-100")
            style {
                height(4.px)
                property("transform", "translateY(-50%)")
                backgroundColor(rgba(255, 255, 255, .7))
            }
        }) {
            Div({
                classes("position-absolute", "w-100", "h-100")
                style {
                    backgroundColor(Color("#C7081C"))
                    opacity(.5)
                    property("pointer-events", "none")
                    property("transform", "scaleX(${bufferedPercent.value})")
                    property("transform-origin", "left")
                    property("transition", "transform .1s")
                }
            })
            Div({
                classes("position-absolute", "w-100", "h-100")
                style {
                    backgroundColor(Color("#C7081C"))
                    property("pointer-events", "none")
                    property("transform", "scaleX(${progressScale.value})")
                    property("transform-origin", "left")
                    property("transition", "transform .1s")
                }
            })
        }

        var popperVirtualElement by remember {
            mutableStateOf(popperFixedPosition(-100, -100))
        }
        DisposableEffect(Unit) {
            popperVirtualElement = object : PopperVirtualElement {
                override val contextElement: HTMLElement = scopeElement
                override fun getBoundingClientRect(): dynamic {
                    return kotlinext.js.js @NoLiveLiterals {
                        this.right = mouseHoverX
                        this.left = mouseHoverX
                        width = 0
                        height = 0
                    }
                }
            }
            onDispose {
                popperVirtualElement = popperFixedPosition(-100, -100)
            }
        }
        PopperElement(
            popperVirtualElement,
            popperOptions(placement = "top"),
            attrs = {
                style { property("pointer-events", "none") }
            }
        ) { popper ->
            LaunchedEffect(mouseHoverX) { popper.update() }
            val previewUrl by derivedStateOf {
                val index = (mouseHoverProgress.inWholeSeconds / 5).coerceAtLeast(1)
                "/api/image/previews/$mediaRefId/preview$index.jpg?${AnyStreamClient.SESSION_KEY}=${client.token}"
            }
            val hasPreview by produceState(false, mediaRefId) {
                value = try {
                    fetch(previewUrl).await().ok
                } catch (e: Throwable) {
                    false
                }
            }
            Div({
                style {
                    property("pointer-events", "none")
                    opacity(if (isThumbVisible) 1 else 0)
                    width(240.px)
                }
            }) {
                if (hasPreview) {
                    var nextImageHasLoaded by remember { mutableStateOf(false) }
                    val images by produceState(previewUrl to "", previewUrl, nextImageHasLoaded) {
                        value = if (nextImageHasLoaded) {
                            previewUrl to value.second
                        } else {
                            value.first to previewUrl
                        }
                    }
                    PreviewImage(images.first, !nextImageHasLoaded) { nextImageHasLoaded = false }
                    PreviewImage(images.second, nextImageHasLoaded) { nextImageHasLoaded = true }
                }
            }
        }
        var timestampWidth by remember { mutableStateOf(0.px) }
        PopperElement(
            popperVirtualElement,
            popperOptions(placement = "top"),
            attrs = {
                style {
                    property("pointer-events", "none")
                    opacity(if (isThumbVisible) 1 else 0)
                    width(timestampWidth)
                }
            }
        ) { popper ->
            val timestamp by derivedStateOf { mouseHoverProgress.formatted() }
            LaunchedEffect(mouseHoverX) { popper.update() }
            Div({
                classes("position-absolute", "px-2", "py-1", "shadow", "bg-dark")
                style {
                    left(50.percent)
                    property("transform", "translate(-50%, -100%)")
                }
            }) {
                DisposableEffect(timestamp) {
                    timestampWidth = scopeElement.clientWidth.px
                    onDispose { }
                }
                Text(timestamp)
            }
        }
    }
}

@Composable
private fun PreviewImage(src: String, isVisible: Boolean, onLoad: () -> Unit) {
    Div({
        classes("position-absolute")
        style {
            property("transform", "translateY(-100%)")
            opacity(if (isVisible) 1 else 0)
        }
    }) {
        Img(src = src, attrs = {
            style { width(240.px) }
            addEventListener("load") { onLoad() }
        })
    }
}
