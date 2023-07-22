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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import anystream.client.AnyStreamClient
import anystream.router.BackStack
import anystream.routing.Routes
import anystream.ui.components.AppTopBar
import anystream.ui.util.PLAYER_CONTROLS_VISIBILITY
import anystream.ui.util.noRippleClickable
import kotlinx.coroutines.delay

@Composable
internal fun SharedVideoPlayer(
    route: Routes.Player,
    stack: BackStack<Routes>,
    client: AnyStreamClient,
    toggleFullScreen: (Boolean) -> Unit,
) {
    var shouldShowControls by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(key1 = shouldShowControls, key2 = isPlaying) {
//        if (!isPlaying) {
//            shouldShowControls = true
//        }

        if (shouldShowControls) {
            delay(PLAYER_CONTROLS_VISIBILITY)
            shouldShowControls = false
        }
    }

    Scaffold(Modifier) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(padding)
                .background(Color.Black)
                .fillMaxSize()
                .noRippleClickable(onClick = { shouldShowControls = !shouldShowControls }),
        ) {
            VideoPlayer(Modifier.fillMaxSize(), route.mediaLinkId, isPlaying) {
                toggleFullScreen(false)
            }

            AnimatedVisibility(
                visible = shouldShowControls,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(),
                exit = slideOutVertically(),
            ) {
                AppTopBar(client = client, backStack = stack, showBackButton = true) {
                    toggleFullScreen(false)
                }
            }

            AnimatedVisibility(
                visible = shouldShowControls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(.15f)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                    ) {
                        Crossfade(targetState = isPlaying) { isPlaying ->
                            // note that it's required to use the value passed by Crossfade
                            // instead of your state value=
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isFullScreen = !isFullScreen
                            toggleFullScreen(isFullScreen)
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
                            tint = Color.Red
                        )
                    }
                }
            }
        }
    }
}
