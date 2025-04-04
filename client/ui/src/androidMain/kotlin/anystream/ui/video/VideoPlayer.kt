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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import anystream.client.AnyStreamClient
import anystream.models.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
internal actual fun VideoPlayer(
    modifier: Modifier,
    mediaLinkId: String,
    isPlaying: Boolean,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val client = koinInject<AnyStreamClient>()

    var window by rememberSaveable { mutableStateOf(0) }
    var position by rememberSaveable { mutableStateOf(0L) }
    val player = remember { ExoPlayer.Builder(context).build() }
    LaunchedEffect(player) {
        player.apply {
            var updateStateJob: Job? = null
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        updateStateJob?.cancel()
                        if (state == Player.STATE_READY) {
                            updateStateJob = scope.launch {
                                while (true) {
                                    window = currentMediaItemIndex
                                    position = contentPosition.coerceAtLeast(0L)
                                    delay(PLAYER_STATE_UPDATE_INTERVAL)
                                }
                            }
                        }
                    }
                },
            )
            playWhenReady = isPlaying
            prepare()
        }
    }
    produceState<PlaybackState?>(null) {
        val initialState = MutableStateFlow<PlaybackState?>(null)
        val handle = client.playbackSession(mediaLinkId) { state ->
            println("[player] $state")
            initialState.value = state
            position = (state.position * 1000).toLong()
            value = state
        }

        val state = initialState.filterNotNull().first()
        withContext(Dispatchers.Main) {
            val url = client.createHlsStreamUrl(mediaLinkId, state.id)
            println("[player] $url")
            player.setMediaItem(MediaItem.fromUri(url))
            player.seekTo(window, position)
            player.play()
        }
        launch {
            while (true) {
                if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                    handle.update.tryEmit((player.currentPosition / 1000.0).coerceAtLeast(0.0))
                }
                delay(PLAYER_STATE_REMOTE_UPDATE_INTERVAL)
            }
        }
        awaitDispose {
            handle.cancel()
        }
    }

    fun updateState() {
        window = player.currentMediaItemIndex
        position = player.contentPosition.coerceAtLeast(0L)
    }

    val playerView = remember { PlayerView(context).apply { useController = false } }
    LaunchedEffect(playerView) {
        playerView.apply {
            lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        super.onStart(owner)
                        onResume()
                        player.playWhenReady = isPlaying
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        super.onStop(owner)
                        updateState()
                        onPause()
                        player.playWhenReady = false
                    }
                },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            updateState()
            player.release()
        }
    }

    AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize()) {
        playerView.player = player
    }
}
