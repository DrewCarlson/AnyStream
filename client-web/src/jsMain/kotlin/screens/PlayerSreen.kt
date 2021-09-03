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
import anystream.frontend.models.MediaItem
import anystream.frontend.models.toMediaItem
import anystream.models.PlaybackState
import com.videojs.VideoJs
import com.videojs.VjsOptions
import com.videojs.VjsPlayer
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.css.keywords.auto
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Video
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.get

private val playerControlsColor = rgba(35, 36, 38, .45)

private const val CONTROL_HIDE_DELAY = 2_250L

@Composable
fun PlayerScreen(
    client: AnyStreamClient,
    mediaRefId: String,
) {
    var player: VjsPlayer? by mutableStateOf(null)
    var playerIsPlaying by mutableStateOf(false)
    val isInMiniMode = mutableStateOf(false)
    val mouseMoveFlow = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
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
    var sessionHandle by mutableStateOf<AnyStreamClient.PlaybackSessionHandle?>(null)
    val mediaItem = mutableStateOf<MediaItem?>(null)
    val playbackState by produceState<PlaybackState?>(null) {
        var initialState: PlaybackState? = null
        sessionHandle = client.playbackSession(mediaRefId) { state ->
            println("[player] $state")
            value = state
            if (initialState == null) {
                launch {
                    mediaItem.value = try {
                        client.lookupMedia(state.mediaId)
                            .run { movie?.toMediaItem() ?: episode?.toMediaItem() }
                    } catch (e: Throwable) {
                        null
                    }
                }
                initialState = state
                player?.currentTime(state.position.toFloat())
            }
        }
        awaitDispose {
            sessionHandle?.cancel?.invoke()
            sessionHandle = null
        }
    }
    val isFullscreen = mutableStateOf(document.fullscreenElement != null)
    val setFullscreen = mutableStateOf<((Boolean) -> Unit)?>(null)
    var duration by mutableStateOf(1f)
    var progress by mutableStateOf(0f)
    val progressScale = derivedStateOf {
        ((progress / duration) * 100)
    }
    Div({
        style {
            classes("w-100")
            if (isInMiniMode.value) {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Row)
            } else {
                overflow("hidden")
                classes("h-100")
                position(Position.Absolute)
            }
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
                classes("w-100")
                style {
                    position(Position.Absolute)
                    left(0.px)
                }
            }) {
                player?.also { player ->
                    SeekBar(player, progressScale)
                }
            }
        }

        val miniPlayerHeight = mutableStateOf(120)
        val miniPlayerWidth = derivedStateOf { (miniPlayerHeight.value * 1.7777f).toInt() }
        Div({
            if (isInMiniMode.value) {
                classes("shadow")
                style {
                    width(miniPlayerWidth.value.px)
                    height(miniPlayerHeight.value.px)
                    margin(12.px)
                    flexShrink(0)
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
            Video({
                id("player")
                classes("video-js", "h-100", "w-100")
                style {
                    cursor(if (areControlsVisible) "pointer" else "none")
                }
                attr("poster", "https://image.tmdb.org/t/p/w1920_and_h800_multi_faces${mediaItem.value?.backdropPath}")
                onClick {
                    if (player?.paused() == true) {
                        player?.play()
                    } else {
                        player?.pause()
                    }
                }
                onDoubleClick {
                    setFullscreen.value?.invoke(document.fullscreenElement == null)
                }
                ref { element ->
                    window.document.onmousemove = {
                        if (!isInMiniMode.value) {
                            mouseMoveFlow.tryEmit(Unit)
                        }
                    }
                    player = VideoJs.default(element, VjsOptions())
                    element.onloadedmetadata = {
                        duration = player?.duration() ?: 1f
                        element.currentTime = playbackState?.position?.toDouble() ?: 0.0
                        true
                    }
                    element.ontimeupdate = {
                        sessionHandle?.update?.tryEmit(element.currentTime.toLong())
                        progress = player?.currentTime() ?: 0f
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
                    classes("bi-play-circle-fill")
                    style {
                        position(Position.Absolute)
                        left(50.percent)
                        top(50.percent)
                        fontSize(82.px)
                        property("pointer-events", "none")
                        property("transform", "translate(-50%, -50%)")
                        color(rgba(199, 8, 28, 0.8))
                    }
                })
                I({
                    classes("bi-play-circle")
                    style {
                        position(Position.Absolute)
                        left(50.percent)
                        top(50.percent)
                        fontSize(82.px)
                        color(Color.white)
                        property("pointer-events", "none")
                        property("transform", "translate(-50%, -50%)")
                    }
                })
            }
        }

        player?.also { currentPlayer ->
            PlaybackControls(
                areControlsVisible = areControlsVisible,
                player = currentPlayer,
                mediaItemState = mediaItem,
                progressScale = progressScale,
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
    var isVisible by mutableStateOf(false)
    Div({
        style {
            position(Position.Absolute)
            property("z-index", "1")
            width(miniPlayerWidth.value.px)
            height(miniPlayerHeight.value.px)
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            alignItems(AlignItems.Center)
            backgroundColor(rgba(0, 0, 0, .6))
            opacity(if (isVisible) 100 else 0)
        }
        onMouseEnter { isVisible = true }
        onMouseLeave { isVisible = false }
        onClick {
            isInMiniMode.value = false
        }
    }) {
        I({ classes("bi-chevron-up") })
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
        style {
            position(Position.Absolute)
            display(DisplayStyle.Flex)
            classes("w-100", "p-3")
            height(60.px)
            top(0.px)
            flexDirection(FlexDirection.Row)
            justifyContent(JustifyContent.SpaceBetween)
            alignItems(AlignItems.Center)
            backgroundColor(playerControlsColor)
            fontSize(20.px)
            property("transition", "transform .2s,background .2s")
            if (!areControlsVisible) {
                property("transform", "translateY(-100%)")
            }
        }
    }) {
        I({
            classes("bi-chevron-down")
            style {
                cursor("pointer")
            }
            onClick {
                isInMiniMode.value = !isInMiniMode.value
            }
        })
        I({
            classes(if (isFullscreen.value) "bi-arrows-angle-contract" else "bi-arrows-angle-expand")
            style {
                cursor("pointer")
            }
            onClick {
                setFullscreen.value?.invoke(!isFullscreen.value)
            }
        })
    }
}

@Composable
private fun PlaybackControls(
    areControlsVisible: Boolean,
    player: VjsPlayer,
    mediaItemState: State<MediaItem?>,
    progressScale: State<Float>,
    overlayMode: Boolean,
) {
    Div({
        style {
            display(DisplayStyle.Flex)
            classes("w-100", "p-3")
            flexDirection(FlexDirection.Row)
            justifyContent(JustifyContent.SpaceBetween)
            alignItems(AlignItems.Center)
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
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                flexBasis(33.percent)

                if (mediaItemState.value == null) {
                    opacity(0)
                }
            }
        }) {
            val mediaItem = mediaItemState.value
            Div {
                mediaItem?.run {
                    Text(contentTitle)
                }
            }
            Div({
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Row)
                    justifyContent(JustifyContent.Start)
                    alignItems(AlignItems.Start)
                    gap(10.px)
                }
            }) {
                mediaItem?.subtitle1?.also { subtitle ->
                    Div { Text(subtitle) }
                }
                mediaItem?.subtitle2?.also { subtitle ->
                    Div { Text(subtitle) }
                }
            }
        }

        Div({
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Row)
                justifyContent(JustifyContent.Center)
                flexBasis(33.percent)
                fontSize(24.px)
                gap(6.px)
                if (mediaItemState.value == null) {
                    opacity(0)
                }
            }
        }) {
            Div({
                style {
                    cursor("pointer")
                    opacity(0.6)
                }
                onClick { }
            }) {
                I({
                    classes("bi-skip-start-fill")
                    style {
                        property("pointer-events", "none")
                    }
                })
            }

            var isPlaying by mutableStateOf(!player.paused())
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
                    classes(if (isPlaying) "bi-pause-fill" else "bi-play-fill")
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
                    classes("bi-skip-end-fill")
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
                    classes("bi-stop-fill")
                    style {
                        property("pointer-events", "none")
                    }
                })
            }
        }

        var isPipAvailable by mutableStateOf(false)
        var isInPipMode by mutableStateOf(false)
        Div({
            style {
                flexBasis(33.percent)
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.RowReverse)
                alignItems(AlignItems.Center)
                fontSize(20.px)
                padding(12.px)
                gap(6.px)
                if (mediaItemState.value == null) {
                    opacity(0)
                }
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
                onDispose {  }
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
                        classes(if (isInPipMode) "bi-pip-fill" else "bi-pip")
                        style {
                            property("pointer-events", "none")
                        }
                    })
                }
            }

            var muted by mutableStateOf(player.muted())
            var volume by mutableStateOf(player.volume())
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
                    }
                }
            }) {
                Div({
                    style {
                        height(50.px)
                        position(Position.Absolute)
                        backgroundColor(Color.white)
                        width(2.px)
                        property("pointer-events", "none")
                    }
                })
                Div({
                    style {
                        height(50.px)
                        position(Position.Absolute)
                        backgroundColor(rgb(199, 8, 28))
                        width(2.px)
                        property("transform", "scaleY(${volumeScale})")
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
                    onDispose {  }
                }
            }) {
                I({
                    val icon = when {
                        muted -> "bi-volume-mute-fill"
                        volume == 0f -> "bi-volume-off-fill"
                        volume < 0.5f -> "bi-volume-down-fill"
                        else -> "bi-volume-up-fill"
                    }
                    classes(icon)
                    style {
                        property("pointer-events", "none")
                    }
                })
            }
        }

        if (overlayMode) {
            Div({
                classes("w-100")
                style {
                    position(Position.Absolute)
                    height(auto)
                    left(0.px)
                    top(0.px)
                    if (!areControlsVisible) {
                        property("transform", "translateY(100%)")
                    }
                }
            }) {
                SeekBar(
                    player = player,
                    progressScale = progressScale,
                )
            }
        }
    }
}

@Composable
private fun SeekBar(
    player: VjsPlayer,
    progressScale: State<Float>,
) {
    var isThumbVisible by mutableStateOf(false)
    var barHeight by mutableStateOf(4)
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

        var isMouseDown by mutableStateOf(false)
        onMouseEnter { isThumbVisible = true }
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
        Div({
            style {
                position(Position.Absolute)
                marginLeft(progressScale.value.percent)
                fontSize(12.px)
                left((-5).px)
                opacity(if (isThumbVisible) 1 else 0)
                property("z-index", "1")
                property("pointer-events", "none")
                property("transform", "translateY(-50%)")
            }
        }) {
            I({
                classes("bi-circle-fill")
                style {
                    color(Color.white)
                    property("pointer-events", "none")
                }
            })
        }
        Div({
            classes("w-100")
            style {
                height(barHeight.px)
                property("transform", "translateY(-50%)")
            }
        }) {
            Div({
                classes("w-100")
                style {
                    position(Position.Absolute)
                    backgroundColor(Color.white)
                    height(barHeight.px)
                    property("pointer-events", "none")
                }
            })
            Div({
                style {
                    position(Position.Absolute)
                    backgroundColor(Color("#C7081C"))
                    width(progressScale.value.percent)
                    height(barHeight.px)
                    property("pointer-events", "none")
                }
            })
        }
    }
}