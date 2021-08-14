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
import anystream.models.PlaybackState
import com.videojs.VideoJs
import com.videojs.VjsOptions
import com.videojs.VjsPlayer
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Video

@Composable
fun PlayerScreen(
    client: AnyStreamClient,
    mediaRefId: String,
) {
    val scope = rememberCoroutineScope()
    var player: VjsPlayer? by mutableStateOf(null)
    var isMouseOnPlayer by mutableStateOf(false)
    var playerIsPlaying by mutableStateOf(false)
    val areControlsVisible by derivedStateOf {
        isMouseOnPlayer || !playerIsPlaying
    }
    val isInMiniMode = mutableStateOf(false)
    var updateTime by mutableStateOf<(suspend (progress: Long) -> Unit)?>(null)
    val playbackState by produceState<PlaybackState?>(null) {
        var initialState: PlaybackState? = null
        val handle = client.playbackSession(mediaRefId) { state ->
            println("[player] $state")
            value = state
            if (initialState == null) {
                initialState = state
                player?.currentTime(state.position.toFloat())
            }
        }
        updateTime = handle.update
        awaitDispose {
            updateTime = null
            handle.cancel()
        }
    }
    Div({
        onMouseEnter {
            isMouseOnPlayer = true
        }
        onMouseLeave {
            isMouseOnPlayer = false
        }
        style {
            classes("w-100")
            if (!isInMiniMode.value) {
                overflow("hidden")
                classes("h-100")
                position(Position.Absolute)
            }
            property("z-index", 100)
            left(0.px)
            bottom(0.px)
            width(100.percent)
        }
    }) {
        val miniPlayerHeight = mutableStateOf(120)
        val miniPlayerWidth = derivedStateOf { (miniPlayerHeight.value * 1.7777f).toInt() }
        Div({
            if (isInMiniMode.value) {
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
            Video({
                id("player")
                classes("video-js", "h-100", "w-100")
                ref { element ->
                    player = VideoJs.default(element, VjsOptions())
                    element.onloadedmetadata = {
                        element.currentTime = playbackState?.position?.toDouble() ?: 0.0
                        true
                    }
                    element.ontimeupdate = {
                        scope.launch {
                            updateTime?.invoke(element.currentTime.toLong())
                        }
                    }
                    element.onplay = {
                        playerIsPlaying = true
                        true
                    }
                    element.onpause = {
                        playerIsPlaying = false
                        true
                    }

                    player?.controls(!isInMiniMode.value)

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
                areControlsVisible
            )
            PlaybackControls(
                areControlsVisible
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

private val playerControlsColor = rgba(35,36,38,.45)

@Composable
private fun MaxPlayerTopBar(
    isInMiniMode: MutableState<Boolean>,
    areControlsVisible: Boolean,
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
            fontSize(24.px)
            property("transition", "transform .2s,background .2s")
            if (!areControlsVisible) {
                property("transform", "translateY(-100%)")
            }
        }
    }) {
        I({
            classes("bi-chevron-down")
            onClick {
                isInMiniMode.value = !isInMiniMode.value
            }
        })
        I({
            classes("bi-x-lg")
            onClick {
                window.location.hash = "!close"
            }
        })
    }
}

@Composable
private fun PlaybackControls(
    areControlsVisible: Boolean,
) {
    Div({
      style {
          style {
              position(Position.Absolute)
              display(DisplayStyle.Flex)
              classes("w-100", "p-3")
              height(100.px)
              bottom((-100).px)
              flexDirection(FlexDirection.Row)
              justifyContent(JustifyContent.Center)
              alignItems(AlignItems.Center)
              backgroundColor(playerControlsColor)
              property("transition", "transform .2s,background .2s")
              if (areControlsVisible) {
                  property("transform", "translateY(-100%)")
              } else {
                  property("transform", "translateY(0)")
              }
          }
      }
    })
}