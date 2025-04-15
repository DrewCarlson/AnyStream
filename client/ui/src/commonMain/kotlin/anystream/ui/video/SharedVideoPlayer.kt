/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import anystream.router.BackStack
import anystream.routing.Routes
import anystream.ui.generated.resources.*
import anystream.ui.util.EnableFullscreen
import anystream.ui.util.PLAYER_CONTROLS_VISIBILITY
import anystream.ui.util.noRippleClickable
import anystream.util.formatted
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.getKoin

/**
 * Hold [PlayerHandle]s in a [ViewModel] to bind them to
 * the current route, managing cleanup and config changes.
 */
private class PlayerViewModel(
    val playerHandle: PlayerHandle,
) : ViewModel() {
    override fun onCleared() {
        playerHandle.dispose()
    }
}

@Composable
internal fun VideoPlayer(
    route: Routes.Player,
    stack: BackStack<Routes>,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val playerViewModel = viewModel { PlayerViewModel(playerHandle = koin.get()) }
    val playerHandle = playerViewModel.playerHandle
    var shouldShowControls by remember { mutableStateOf(true) }
    val isPlaying by playerHandle.playWhenReadyFlow.collectAsState()

    EnableFullscreen(isUserRequested = false)

    LaunchedEffect(shouldShowControls) {
        if (shouldShowControls) {
            delay(PLAYER_CONTROLS_VISIBILITY)
            shouldShowControls = !isPlaying
        }
    }

    LaunchedEffect(route) {
        playerHandle.loadMediaLink(route.mediaLinkId)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var lastPosition: Offset? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Exit -> {
                                shouldShowControls = false
                            }

                            PointerEventType.Move -> {
                                val change = event.changes.first()
                                if (lastPosition != change.position) {
                                    shouldShowControls = true
                                    lastPosition = change.position
                                }
                            }

                            PointerEventType.Press -> {
                                shouldShowControls = true
                            }

                            else -> Unit
                        }
                    }
                }
            },
    ) {
        PlatformVideoPlayer(
            playerHandle = playerHandle,
            modifier = Modifier
                .fillMaxSize(),
        )

        // Top bar controls
        AnimatedVisibility(
            visible = shouldShowControls,
            modifier = Modifier
                .align(Alignment.TopCenter),
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181A20).copy(alpha = 0.6f))
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(26.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    Icons.Rounded.ArrowDropDown,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = "Pause and minimize player",
                    modifier = Modifier
                        .size(38.dp)
                        .noRippleClickable { stack.pop() },
                )

                /*Text(
                    text = "TODO: Metadata title",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )*/

                Spacer(Modifier.weight(1f))

                Image(
                    painterResource(Res.drawable.ic_cast),
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = "Cast playback to device",
                    modifier = Modifier
                        .size(38.dp)
                        .noRippleClickable { },
                )

                Image(
                    Icons.Rounded.Close,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = "Stop playback and close player",
                    modifier = Modifier
                        .size(38.dp)
                        .rotate(180f)
                        .noRippleClickable { stack.pop() },
                )
            }
        }

        // Bottom bar controls
        AnimatedVisibility(
            visible = shouldShowControls,
            modifier = Modifier
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            val enabledAlpha = 0.8f
            val disabledAlpha = 0.5f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181A20).copy(alpha = 0.6f))
                    .padding(bottom = 20.dp)
            ) {
                var overrideProgressPercent by remember { mutableStateOf<Float?>(null) }
                // Progress bar
                BoxWithConstraints(
                    modifier = Modifier
                        .height(14.dp)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Spacer(
                        Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    )

                    val progressPercent by playerHandle.progressPercentFlow.collectAsState()
                    val bufferProgressPercent by playerHandle.bufferProgressPercentFlow.collectAsState()
                    Spacer(
                        Modifier
                            .fillMaxHeight()
                            .width(maxWidth * (overrideProgressPercent ?: bufferProgressPercent))
                            .background(Color.Red.copy(alpha = 0.5f))
                    )
                    Spacer(
                        Modifier
                            .fillMaxHeight()
                            .width(maxWidth * (overrideProgressPercent ?: progressPercent))
                            .background(Color.Red)
                    )
                    Spacer(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                val change = event.changes.first()
                                                overrideProgressPercent = change.position.x / constraints.maxWidth
                                                change.consume()
                                            }
                                            PointerEventType.Release -> {
                                                val change = event.changes.first()
                                                playerHandle.seekToPercent(change.position.x / constraints.maxWidth)
                                                overrideProgressPercent = null
                                                change.consume()
                                            }
                                            PointerEventType.Move -> {
                                                val change = event.changes.first()
                                                if (event.buttons.isPrimaryPressed || change.type == PointerType.Touch) {
                                                    overrideProgressPercent = change.position.x / constraints.maxWidth
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    )
                }

                // Progress labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    val currentProgress by playerHandle.progressFlow.collectAsState()
                    val currentDuration by playerHandle.durationFlow.collectAsState()

                    Text(
                        text = currentProgress.formatted(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = currentDuration.formatted(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val canSkipForward by playerHandle.canSkipForward.collectAsState()
                    val canSkipBackward by playerHandle.canSkipBackward.collectAsState()

                    // Previous button
                    Image(
                        painter = painterResource(Res.drawable.ic_next),
                        colorFilter = ColorFilter.tint(Color.White),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(disabledAlpha)
                            .rotate(180f)
                            .noRippleClickable {},
                    )

                    // Backward button
                    Image(
                        painter = painterResource(Res.drawable.ic_backward),
                        colorFilter = ColorFilter.tint(Color.White),
                        contentDescription = null,
                        modifier = Modifier
                            .alpha(if (canSkipBackward) enabledAlpha else disabledAlpha)
                            .size(24.dp)
                            .noRippleClickable { playerHandle.skipBackward() },
                    )

                    // Play/Pause button
                    Image(
                        painter = painterResource(
                            if (isPlaying) {
                                Res.drawable.ic_pause
                            } else {
                                Res.drawable.ic_play
                            }
                        ),
                        colorFilter = ColorFilter.tint(Color.White),
                        contentDescription = null,
                        modifier = Modifier
                            .alpha(enabledAlpha)
                            .size(42.dp)
                            .noRippleClickable { playerHandle.togglePlaying() },
                    )

                    // Forward button
                    Image(
                        painter = painterResource(Res.drawable.ic_forward),
                        colorFilter = ColorFilter.tint(Color.White),
                        contentDescription = null,
                        modifier = Modifier
                            .alpha(if (canSkipForward) enabledAlpha else disabledAlpha)
                            .size(24.dp)
                            .noRippleClickable { playerHandle.skipForward() },
                    )

                    // Next button
                    Image(
                        painter = painterResource(Res.drawable.ic_next),
                        colorFilter = ColorFilter.tint(Color.White),
                        contentDescription = null,
                        modifier = Modifier
                            .alpha(disabledAlpha)
                            .size(24.dp)
                            .noRippleClickable {},
                    )
                }
            }
        }
    }
}
