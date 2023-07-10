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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import anystream.client.getClient
import anystream.models.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer


private const val PLAYER_STATE_REMOTE_UPDATE_INTERVAL = 5_000L

public val LocalAppWindow: ProvidableCompositionLocal<ComposeWindow> =
    compositionLocalOf { error("LocalAppWindow not provided") }

// TODO: Move anystream logic into the anystream-client-ui VideoPlayer
//  Rely on this module for simple playback and controls to hide VLCJ
@Composable
public fun SkiaVlcjVideoPlayer(modifier: Modifier, mediaLinkId: String) {
    val client = getClient()
    var position by rememberSaveable { mutableStateOf(0L) }
    val mediaPlayerComponent = remember { EmbeddedMediaPlayerComponent() }
    val mediaPlayer = remember { mediaPlayerComponent.mediaPlayer() }
    val surface = remember {
        SkiaBitmapVideoSurface().also {
            mediaPlayer.videoSurface().set(it)
        }
    }
    DisposableEffect(Unit) { onDispose(mediaPlayer::release) }
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
                modifier = Modifier.fillMaxSize(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.Center,
            )
        }
    }
}

public class SkiaBitmapVideoSurface(
    private val adapter: VideoSurfaceAdapter = VideoSurfaceAdapters.getVideoSurfaceAdapter(),
) : VideoSurface(adapter) {

    public var sourceWidth: Int = 0
    public var sourceHeight: Int = 0

    private lateinit var imageInfo: ImageInfo

    private var useFrame1 = true
    private lateinit var frameBytes1: ByteArray
    private lateinit var frameBytes2: ByteArray
    private val skiaBitmap1: Bitmap = Bitmap()
    private val skiaBitmap2: Bitmap = Bitmap()

    private val composeBitmap = mutableStateOf<ImageBitmap?>(null)

    public val bitmap: State<ImageBitmap?> = composeBitmap

    private val bufferFormatCallback = SkiaBitmapBufferFormatCallback()
    private val renderCallback = SkiaBitmapRenderCallback()
    private val videoSurface = SkiaBitmapVideoSurface()

    override fun attach(mediaPlayer: MediaPlayer) {
        videoSurface.attach(mediaPlayer)
    }

    private inner class SkiaBitmapBufferFormatCallback : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            this@SkiaBitmapVideoSurface.sourceWidth = sourceWidth
            this@SkiaBitmapVideoSurface.sourceHeight = sourceHeight
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun allocatedBuffers(buffers: Array<ByteBuffer>) {
            frameBytes1 = ByteArray(sourceWidth * sourceHeight * 4)
            frameBytes2 = frameBytes1.copyOf()
            imageInfo = ImageInfo(
                sourceWidth,
                sourceHeight,
                ColorType.BGRA_8888,
                ColorAlphaType.PREMUL,
            )
        }
    }

    private inner class SkiaBitmapRenderCallback : RenderCallback {
        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<ByteBuffer>,
            bufferFormat: BufferFormat,
        ) {
            nativeBuffers[0].rewind()
            val rowBytes = sourceWidth * 4
            composeBitmap.value = if (useFrame1) {
                nativeBuffers[0].get(frameBytes1)
                skiaBitmap1.installPixels(imageInfo, frameBytes1, rowBytes)
                skiaBitmap1.asComposeImageBitmap()
            } else {
                nativeBuffers[0].get(frameBytes2)
                skiaBitmap2.installPixels(imageInfo, frameBytes2, rowBytes)
                skiaBitmap2.asComposeImageBitmap()
            }
            useFrame1 = !useFrame1
        }
    }

    private inner class SkiaBitmapVideoSurface : CallbackVideoSurface(
        bufferFormatCallback,
        renderCallback,
        true,
        adapter,
    )
}
