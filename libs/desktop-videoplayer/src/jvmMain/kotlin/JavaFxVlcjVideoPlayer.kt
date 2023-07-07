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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import anystream.client.getClient
import anystream.models.PlaybackState
import com.sun.jna.NativeLibrary
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.io.File

private const val PLAYER_STATE_REMOTE_UPDATE_INTERVAL = 5_000L

// TODO: Move anystream logic into the anystream-client-ui VideoPlayer
//  Rely on this module for simple playback and controls to hide VLCJ
@Composable
fun JavaFxVlcjVideoPlayer(modifier: Modifier, mediaLinkId: String) {
    val client = getClient()
    var position by rememberSaveable { mutableStateOf(0L) }
    val surfaceReady = remember { MutableStateFlow(false) }
    val mediaPlayerComponent = remember { EmbeddedMediaPlayerComponent() }
    val mediaPlayer = remember { mediaPlayerComponent.mediaPlayer() }

    val factory = remember {
        {
            JFXVideoPlayer(mediaPlayer) {
                surfaceReady.value = true
            }
        }
    }

    produceState<PlaybackState?>(null) {
        surfaceReady.filter { it }.first()
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
    DisposableEffect(Unit) { onDispose(mediaPlayer::release) }
    SwingPanel(
        factory = factory,
        background = Color.Black,
        modifier = modifier,
    )
}

private const val STYLES = "-fx-background-color: #000000;"

private class JFXVideoPlayer(
    private val embeddedPlayer: EmbeddedMediaPlayer,
    private val surfaceReady: () -> Unit,
) : JFXPanel() {
    init {
        Platform.runLater(::initialize)
    }

    private fun initialize() {
        val root = BorderPane().apply { style = STYLES }
        val imageView = ImageView().apply {
            isPreserveRatio = true
            style = STYLES
        }
        imageView.fitWidthProperty().bind(root.widthProperty())
        imageView.fitHeightProperty().bind(root.heightProperty())
        root.center = imageView
        embeddedPlayer.videoSurface().set(ImageViewVideoSurface(imageView))
        scene = Scene(root)
        surfaceReady()
    }
}
