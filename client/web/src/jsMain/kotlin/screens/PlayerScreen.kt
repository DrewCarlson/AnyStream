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
import anystream.client.api.PlaybackSessionHandle
import anystream.components.LinkedText
import anystream.libs.*
import anystream.models.MediaItem
import anystream.models.PlaybackState
import anystream.models.api.EpisodeResponse
import anystream.models.api.MovieResponse
import anystream.models.toMediaItem
import anystream.playerMediaLinkId
import anystream.util.BifFileReader
import anystream.util.formatProgressAndRuntime
import anystream.util.formatted
import anystream.util.get
import io.ktor.client.fetch.*
import io.ktor.util.encodeBase64
import js.objects.unsafeJso
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS

private val playerControlsColor = rgba(35, 36, 38, .45)

private const val CONTROL_HIDE_DELAY = 2_750L

@Composable
fun PlayerScreen(mediaLinkId: String) {
    val client = get<AnyStreamClient>()
    var player: VjsPlayer? by remember { mutableStateOf(null) }
    var playerIsPlaying by remember { mutableStateOf(false) }
    var isInMiniMode by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(document.fullscreenElement != null) }
    var setFullscreen by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var duration by remember { mutableStateOf(1.0) }
    var bufferedProgress by remember { mutableStateOf(0.0) }
    var progress by remember { mutableStateOf(0.0) }
    val progressScale by remember { derivedStateOf { progress / duration } }
    val mouseMoveFlow = remember {
        MutableSharedFlow<Unit>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    val isMouseOnPlayer by remember {
        mouseMoveFlow
            .transformLatest {
                emit(true)
                delay(CONTROL_HIDE_DELAY)
                emit(false)
            }
    }.collectAsState(false)
    val areControlsVisible by remember {
        derivedStateOf {
            isMouseOnPlayer || !playerIsPlaying
        }
    }
    var sessionHandle by remember { mutableStateOf<PlaybackSessionHandle?>(null) }
    val mediaItem by produceState<MediaItem?>(null) {
        value = try {
            client.library.findMediaLink(mediaLinkId).let { (_, metadata) ->
                (metadata as? MovieResponse)?.toMediaItem()
                    ?: (metadata as? EpisodeResponse)?.toMediaItem(concise = true)
            }
        } catch (e: Throwable) {
            null
        }
    }
    val playbackState by produceState<PlaybackState?>(null) {
        var initialState: PlaybackState? = null
        sessionHandle = client.stream.playbackSession(
            scope = this,
            mediaLinkId = mediaLinkId,
            onClosed = { playerMediaLinkId.value = null }
        ) { state ->
            println("[player] $state")
            if (initialState == null) {
                initialState = state
                player?.currentTime(state.position.toDouble(SECONDS))
            }
            value = state
        }
        awaitDispose {
            sessionHandle?.cancel?.invoke()
            sessionHandle = null
        }
    }
    Div({
        classes("w-100")
        if (isInMiniMode) {
            classes("d-flex")
        } else {
            classes("position-absolute", "h-100", "overflow-hidden")
        }
        style {
            property("z-index", 100)
            backgroundColor(playerControlsColor)
        }
        ref { element ->
            setFullscreen = { fullscreen ->
                if (fullscreen) element.requestFullscreen() else document.exitFullscreen()
                isFullscreen = fullscreen
            }
            onDispose {
                setFullscreen = null
            }
        }
    }) {
        if (isInMiniMode) {
            Div({
                classes("position-absolute", "w-100")
                style {
                    left(0.px)
                }
            }) {
                player?.also { player ->
                    SeekBar(player, mediaLinkId, progressScale, bufferedProgress)
                }
            }
        }

        val miniPlayerHeight by remember { mutableStateOf(100.px) }
        val miniPlayerWidth by remember { derivedStateOf { (miniPlayerHeight * 1.7777f) } }
        Div({
            if (isInMiniMode) {
                classes("flex-shrink-0", "m-2", "shadow")
                style {
                    width(miniPlayerWidth)
                    height(miniPlayerHeight)
                }
            } else {
                classes("h-100", "w-100")
            }
        }) {
            if (isInMiniMode) {
                MiniModeOverlay(
                    width = miniPlayerWidth,
                    height = miniPlayerHeight,
                    onExitMiniMode = { isInMiniMode = false },
                )
            }
            /*val posterPath by remember {
                derivedStateOf {
                    "https://image.tmdb.org/t/p/w1920_and_h800_multi_faces${mediaItem.value?.backdropPath}"
                }
            }*/
            Video({
                id("player")
                classes("video-js", "h-100", "w-100")
                style {
                    cursor(if (areControlsVisible) "pointer" else "none")
                }
                //attr("poster", posterPath)
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
                    setFullscreen?.invoke(document.fullscreenElement == null)
                }
                ref { element ->
                    window.document.onmousemove = {
                        if (!isInMiniMode) {
                            mouseMoveFlow.tryEmit(Unit)
                        }
                    }
                    player = VideoJs.default(
                        element,
                        VjsOptions {
                            errorDisplay = false
                            controlBar = false
                            loadingSpinner = false
                        },
                    )
                    element.onprogress = {
                        val timeSpans = List(element.buffered.length) { i ->
                            element.buffered.start(i)..element.buffered.end(i)
                        }

                        val closestBufferProgress = timeSpans
                            .filter { span -> element.currentTime in span }
                            .maxOfOrNull { span -> span.endInclusive }
                            ?.div(element.duration)

                        bufferedProgress = closestBufferProgress ?: 0.0
                        true
                    }
                    element.onloadedmetadata = {
                        duration = player?.duration() ?: 1.0
                        element.currentTime = playbackState?.position?.toDouble(SECONDS) ?: 0.0
                        true
                    }
                    element.ontimeupdate = {
                        sessionHandle?.update?.tryEmit(element.currentTime.seconds)
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
                        player?.dispose()
                        player = null
                        element.ontimeupdate = null
                        element.onloadedmetadata = null
                    }
                }
            }) {
                LaunchedEffect(playbackState?.id, player) {
                    playbackState?.also { state ->
                        val url = client.stream.createHlsStreamUrl(mediaLinkId, state.id)
                        println("[player] $url")
                        player?.src(url)
                        player?.play()
                    }
                }
            }
        }

        if (!isInMiniMode) {
            MaxPlayerTopBar(
                areControlsVisible = areControlsVisible,
                isFullscreen = isFullscreen,
                onToggleFullscreen = { setFullscreen?.invoke(!isFullscreen) },
                onToggleMiniMode = { isInMiniMode = !isInMiniMode }
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
                mediaLinkId = mediaLinkId,
                mediaItem = mediaItem,
                progressScale = progressScale,
                bufferedPercent = bufferedProgress,
                overlayMode = !isInMiniMode,
                onEnterMiniMode = { isInMiniMode = true },
            )
        }
    }
}

@Composable
private fun MiniModeOverlay(
    width: CSSpxValue,
    height: CSSpxValue,
    onExitMiniMode: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    Div({
        classes("position-absolute", "d-flex", "justify-content-center", "align-items-center")
        style {
            property("z-index", "1")
            width(width)
            height(height)
            backgroundColor(rgba(0, 0, 0, .6))
            cursor("pointer")
            opacity(if (isVisible) 100 else 0)
        }
        onMouseEnter { isVisible = true }
        onMouseLeave { isVisible = false }
        onClick { onExitMiniMode() }
    }) {
        I({ classes("bi", "bi-chevron-up", "user-select-none") })
    }
}

@Composable
private fun MaxPlayerTopBar(
    areControlsVisible: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleMiniMode: () -> Unit,
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
                if (isFullscreen) {
                    opacity(0)
                }
            }
            onClick { onToggleMiniMode() }
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
                onToggleFullscreen()
            }
        }) {
            I({
                classes(
                    "user-select-none",
                    "bi",
                    if (isFullscreen) {
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
    mediaLinkId: String,
    mediaItem: MediaItem?,
    progressScale: Double,
    bufferedPercent: Double,
    onEnterMiniMode: () -> Unit,
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
            classes("d-flex", "flex-column", "overflow-hidden")
            style {
                flexBasis(33.percent)
            }
        }) {
            mediaItem?.run {
                LinkedText(
                    url = "/media/${rootMetadataId ?: mediaId}",
                    afterClick = onEnterMiniMode,
                    singleLine = true,
                ) {
                    Text(contentTitle)
                }
            }
            Div({
                classes("d-flex", "justify-content-start", "align-items-center")
                style {
                    gap(10.px)
                }
            }) {
                mediaItem?.apply {
                    if (!subtitle1.isNullOrBlank() && !subtitle2.isNullOrBlank()) {
                        LinkedText(
                            url = "/media/${parentMetadataId}",
                            afterClick = onEnterMiniMode,
                            singleLine = true,
                        ) {
                            Text("$subtitle1")
                        }
                        Text(" · ")
                        LinkedText(
                            url = "/media/${mediaId}",
                            afterClick = onEnterMiniMode,
                            singleLine = true,
                        ) {
                            Text("$subtitle2")
                        }
                    } else {
                        subtitle1?.also { subtitle ->
                            LinkedText(
                                url = "/media/${mediaId}",
                                afterClick = onEnterMiniMode,
                                singleLine = true,
                            ) {
                                Text(subtitle)
                            }
                        }
                        subtitle2?.also { subtitle ->
                            LinkedText(
                                url = "/media/${parentMetadataId}",
                                afterClick = onEnterMiniMode,
                                singleLine = true,
                            ) {
                                Text(subtitle)
                            }
                        }
                    }
                }
            }
            val playProgressString = remember(progressScale) {
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
                        "user-select-none",
                        "bi",
                        if (isPlaying) "bi-pause-fill" else "bi-play-fill",
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
                    playerMediaLinkId.value = null
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
                            "bi",
                            if (isInPipMode) "bi-pip-fill" else "bi-pip",
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
            LaunchedEffect(Unit) {
                localStorage.getItem("PLAYER_VOLUME")
                    ?.toFloatOrNull()
                    ?.run(player::volume)
                localStorage.getItem("PLAYER_MUTED")
                    ?.toBoolean()
                    ?.run(player::muted)
            }
             LaunchedEffect(volume, muted) {
                 localStorage.setItem("PLAYER_VOLUME", volume.toString())
                 localStorage.setItem("PLAYER_MUTED", muted.toString())
             }
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
                val icon = remember(muted, volume) {
                    when {
                        muted -> "bi-volume-mute-fill"
                        volume == 0f -> "bi-volume-off-fill"
                        volume < 0.5f -> "bi-volume-down-fill"
                        else -> "bi-volume-up-fill"
                    }
                }
                I({
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
                    mediaLinkId = mediaLinkId,
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
    mediaLinkId: String,
    progressScale: Double,
    bufferedPercent: Double,
) {
    val client = get<AnyStreamClient>()
    var isThumbVisible by remember { mutableStateOf(false) }
    var isMouseDown by remember { mutableStateOf(false) }
    var mouseHoverX by remember { mutableStateOf(0) }
    var mouseHoverProgress by remember { mutableStateOf(ZERO) }
    var popperVirtualElement by remember {
        mutableStateOf(popperFixedPosition(-1000, -1000))
    }
    var bif by remember { mutableStateOf<BifFileReader?>(null) }
    LaunchedEffect(mediaLinkId) {
        bif = client.images.getPreviewBif(mediaLinkId)
            ?.run(BifFileReader::open)
    }
    DisposableEffect(mediaLinkId) {
        onDispose {
            bif?.close()
            bif = null
        }
    }
    val previewUrl by remember {
        derivedStateOf {
            val currentBif = bif
            if (currentBif == null) {
                ""
            } else {
                val index =
                    (mouseHoverProgress.inWholeSeconds / 5)
                        .coerceIn(0, currentBif.header.imageCount.toLong()).toInt()
                "data:image/webp;base64,${currentBif.readFrame(index).bytes.encodeBase64()}"
            }
        }
    }
    val hasPreview by produceState(false, mediaLinkId) {
        value = try {
            fetch(previewUrl).await().ok
        } catch (_: Throwable) {
            false
        }
    }
    Div({
        classes("w-100")
        style {
            paddingTop(8.px)
            paddingBottom(8.px)
            cursor("pointer")
            property("transform", "translateY(-50%)")
        }
        onClick { event ->
            val percent = event.clientX.toDouble() / (event.target as HTMLDivElement).clientWidth
            player.currentTime(player.duration() * percent.toFloat())
        }

        onMouseUp { isMouseDown = false }
        onMouseDown { isMouseDown = true }
        onMouseEnter {
            isThumbVisible = player.duration() > 0
        }
        onMouseLeave {
            mouseHoverX = 0
            mouseHoverProgress = ZERO
            isThumbVisible = false
            isMouseDown = false
        }
        onMouseMove { event ->
            val percent = event.clientX.toDouble() / (event.target as HTMLDivElement).clientWidth
            val progress = player.duration() * percent
            mouseHoverProgress = if (progress.isNaN()) ZERO else progress.seconds
            mouseHoverX = event.clientX
            isThumbVisible = player.duration() > 0

            if (isMouseDown) {
                player.currentTime(player.duration() * percent.toFloat())
            }
        }
    }) {
        I({
            classes("position-absolute", "bi", "bi-circle-fill", "user-select-none")
            style {
                opacity(if (isThumbVisible) 1 else 0)
                color(Color.white)
                marginLeft((progressScale * 100).percent)
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
                    property("transform", "scaleX(${bufferedPercent})")
                    property("transform-origin", "left")
                    property("transition", "transform .1s")
                }
            })
            Div({
                classes("position-absolute", "w-100", "h-100")
                style {
                    backgroundColor(Color("#C7081C"))
                    property("pointer-events", "none")
                    property("transform", "scaleX(${progressScale})")
                    property("transform-origin", "left")
                    property("transition", "transform .1s")
                }
            })
        }
        DisposableEffect(Unit) {
            popperVirtualElement = object : PopperVirtualElement {
                override val contextElement: HTMLElement = scopeElement
                override fun getBoundingClientRect(): PopperRect {
                    return unsafeJso {
                        right = mouseHoverX
                        left = mouseHoverX
                        width = 0
                        height = 0
                    }
                }
            }
            onDispose {
                popperVirtualElement = popperFixedPosition(-1000, -1000)
            }
        }
        if (hasPreview) {
            PopperElement(
                popperVirtualElement,
                popperOptions(placement = "top"),
                attrs = {
                    style { property("pointer-events", "none") }
                },
            ) { popper ->
                LaunchedEffect(mouseHoverX) { popper.update() }
                Div({
                    style {
                        property("pointer-events", "none")
                        opacity(if (isThumbVisible) 1 else 0)
                        width(240.px)
                    }
                }) {
                    PreviewImage(previewUrl)
                }
            }
        }
        val timestamp by remember { derivedStateOf { mouseHoverProgress.formatted() } }
        PopperElement(
            popperVirtualElement,
            popperOptions(placement = "top"),
            attrs = {
                style {
                    property("pointer-events", "none")
                    opacity(if (isThumbVisible) 1 else 0)
                }
            },
        ) { popper ->
            LaunchedEffect(mouseHoverX) { popper.update() }
            Div({
                classes("px-2", "py-1", "shadow", "bg-dark-translucent")
                style {
                    property("transform", "translateY(-100%)")
                }
            }) {
                Text(timestamp)
            }
        }
    }
}

@Composable
private fun PreviewImage(src: String) {
    Div({
        classes("position-absolute")
        style {
            property("transform", "translateY(-120%)")
        }
    }) {
        Img(src = src, attrs = {
            style { width(240.px) }
        })
    }
}
