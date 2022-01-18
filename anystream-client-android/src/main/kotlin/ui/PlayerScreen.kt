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
import androidx.lifecycle.*
import anystream.client.AnyStreamClient
import anystream.frontend.models.MediaItem
import anystream.frontend.models.toMediaItem
import anystream.models.MediaReference
import anystream.models.Movie
import anystream.models.PlaybackState
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.exoplayer2.MediaItem as ExoMediaItem

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
    var sessionHandle by remember { mutableStateOf<AnyStreamClient.PlaybackSessionHandle?>(null) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            var updateStateJob: Job? = null
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    updateStateJob?.cancel()
                    updateStateJob = when (state) {
                        Player.STATE_READY -> {
                            scope.launch {
                                while (true) {
                                    window = currentMediaItemIndex
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
            setMediaItem(ExoMediaItem.fromUri("https://anystream.dev/api/stream/$mediaRefId/direct"))
            prepare()
            seekTo(window, position)
        }
    }
    val mediaItem = produceState<MediaItem?>(null) {
        value = try {
            client.lookupMediaByRefId(mediaRefId).let {
                it.movie?.toMediaItem() ?: it.episode?.toMediaItem()
            }
        } catch (e: Throwable) {
            null
        }
    }
    val playbackState by produceState<PlaybackState?>(null) {
        var initialState: PlaybackState? = null
        sessionHandle = client.playbackSession(mediaRefId) { state ->
            println("[player] $state")
            value = state
            if (initialState == null) {
                initialState = state
                player.seekTo((state.position * 1000).toLong())
            }
        }
        awaitDispose {
            sessionHandle?.cancel?.invoke()
            sessionHandle = null
        }
    }
    LaunchedEffect(playbackState?.id, player) {
        playbackState?.also { state ->
            val url = client.createHlsStreamUrl(mediaRefId, state.id)
            println("[player] $url")
            player.setMediaItem(ExoMediaItem.fromUri(url))
            player.play()
        }
    }

    fun updateState() {
        autoPlay = player.playWhenReady
        window = player.currentMediaItemIndex
        position = player.contentPosition.coerceAtLeast(0L)
    }

    val playerView = remember {
        PlayerView(context).apply {
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    super.onStart(owner)
                    onResume()
                    player.playWhenReady = autoPlay
                }

                override fun onStop(owner: LifecycleOwner) {
                    super.onStop(owner)
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
            player.seekTo((initialState.position * 1000).toLong())
        }

        while (true) {
            if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                handle.update.tryEmit((player.currentPosition / 1000.0).coerceAtLeast(0.0))
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
