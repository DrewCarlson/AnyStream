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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import anystream.client.getClient
import anystream.models.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.MediaPlayerFactory

// Same as MediaPlayerComponentDefaults.EMBEDDED_MEDIA_PLAYER_ARGS
private val PLAYER_ARGS = listOf(
    "--video-title=vlcj video output",
    "--no-snapshot-preview",
    "--quiet",
    "--intf=dummy",
)

@Composable
internal actual fun VideoPlayer(
    modifier: Modifier,
    mediaLinkId: String,
    isPlaying: Boolean,
) {
    val client = getClient()
    var position by rememberSaveable { mutableStateOf(0L) }
    val mediaPlayerFactory = remember { MediaPlayerFactory(PLAYER_ARGS) }
    val mediaPlayer = remember {
        mediaPlayerFactory
            .mediaPlayers()
            .newEmbeddedMediaPlayer()
    }
    val surface = remember {
        SkiaBitmapVideoSurface().also {
            mediaPlayer.videoSurface().set(it)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
            mediaPlayerFactory.release()
        }
    }
    LaunchedEffect(isPlaying) {
        mediaPlayer.controls().setPause(!isPlaying)
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
        val url = client.createHlsStreamUrl(mediaLinkId, state.id)
        println("[player] $url")
        check(mediaPlayer.media().prepare(url))
        mediaPlayer.controls().play()
        mediaPlayer.controls().setTime(position)
        mediaPlayer.controls().setPause(!isPlaying)
        launch {
            while (true) {
                if (mediaPlayer.status().isPlaying) {
                    val currentTime = mediaPlayer.status().time() / 1000.0
                    handle.update.tryEmit(currentTime.coerceAtLeast(0.0))
                }
                delay(PLAYER_STATE_REMOTE_UPDATE_INTERVAL)
            }
        }
        awaitDispose { handle.cancel() }
    }

    Box(modifier = modifier) {
        surface.bitmap.value?.let { bitmap ->
            Image(
                bitmap,
                modifier = Modifier
                    .background(Color.Black)
                    .fillMaxSize(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
            )
        }
    }
}
