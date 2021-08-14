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
package anystream.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import anystream.client.AnyStreamClient
import anystream.models.MediaReference
import anystream.models.Movie
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val PLAYER_STATE_UPDATE_INTERVAL = 250L
private const val PLAYER_STATE_REMOTE_UPDATE_INTERVAL = 5_000L

@Composable
fun PlayerScreen(
    client: AnyStreamClient,
    mediaRefId: String,
    modifier: Modifier = Modifier,
    mediaReference: MediaReference? = null,
    movie: Movie? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var autoPlay by rememberSaveable { mutableStateOf(true) }
    var window by rememberSaveable { mutableStateOf(0) }
    var position by rememberSaveable { mutableStateOf(0L) }

    val player = remember {
        SimpleExoPlayer.Builder(context).build().apply {
            var updateStateJob: Job? = null
            addListener(object : Player.EventListener {
                override fun onPlaybackStateChanged(state: Int) {
                    updateStateJob?.cancel()
                    updateStateJob = when (state) {
                        Player.STATE_READY -> {
                            scope.launch {
                                while (true) {
                                    window = currentWindowIndex
                                    position = contentPosition.coerceAtLeast(0L)
                                    delay(PLAYER_STATE_UPDATE_INTERVAL)
                                }
                            }
                        }
                        else -> null
                    }
                }
            })
            playWhenReady = autoPlay
            setMediaItem(MediaItem.fromUri("https://anystream.dev/api/stream/$mediaRefId/direct"))
            prepare()
            seekTo(window, position)
        }
    }

    fun updateState() {
        autoPlay = player.playWhenReady
        window = player.currentWindowIndex
        position = player.contentPosition.coerceAtLeast(0L)
    }

    val playerView = remember {
        PlayerView(context).apply {
            lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                fun onStart() {
                    onResume()
                    player.playWhenReady = autoPlay
                }

                @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
                fun onStop() {
                    updateState()
                    onPause()
                    player.playWhenReady = false
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            updateState()
            player.release()
        }
    }

    LaunchedEffect(mediaRefId) {
        val handle = client.playbackSession(mediaRefId) { initialState ->
            player.seekTo(initialState.position * 1000)
        }

        while (true) {
            if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                handle.update((player.currentPosition / 1000).coerceAtLeast(0L))
            }
            delay(PLAYER_STATE_REMOTE_UPDATE_INTERVAL)
        }
    }

    Scaffold {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(Color.Black)
                .fillMaxSize()
        ) {
            AndroidView(
                factory = { playerView },
                modifier = Modifier
                    .fillMaxSize()
            ) {
                playerView.player = player
            }
        }
    }
}