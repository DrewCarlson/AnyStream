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

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.skia.*
import sun.misc.Unsafe
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.lang.reflect.Field
import java.nio.Buffer
import java.nio.ByteBuffer

class SkiaImageVideoSurface : VideoSurface(VideoSurfaceAdapters.getVideoSurfaceAdapter()) {

    private val videoSurface = SkiaBitmapVideoSurface()
    private lateinit var pixmap: Pixmap
    private val skiaImage = mutableStateOf<Image?>(null)

    val frame: State<Image?> = skiaImage

    override fun attach(mediaPlayer: MediaPlayer) {
        videoSurface.attach(mediaPlayer)
    }

    private inner class SkiaBitmapBufferFormatCallback : BufferFormatCallback {
        private var sourceWidth: Int = 0
        private var sourceHeight: Int = 0

        override fun newFormatSize(
            bufferWidth: Int,
            bufferHeight: Int,
            displayWidth: Int,
            displayHeight: Int
        ) {
        }

        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            this.sourceWidth = sourceWidth
            this.sourceHeight = sourceHeight
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun allocatedBuffers(buffers: Array<ByteBuffer>) {
            val buffer = buffers[0]
            val pointer = ByteBufferFactory.getAddress(buffer)
            val imageInfo = ImageInfo.makeN32Premul(sourceWidth, sourceHeight, ColorSpace.sRGB)
            pixmap = Pixmap.make(imageInfo, pointer, sourceWidth * 4)
        }
    }

    private inner class SkiaBitmapRenderCallback : RenderCallback {

        override fun display(
            mediaPlayer: MediaPlayer?,
            nativeBuffers: Array<out ByteBuffer>,
            bufferFormat: BufferFormat?,
            displayWidth: Int,
            displayHeight: Int
        ) {
            skiaImage.value = Image.makeFromPixmap(pixmap)
        }

        override fun lock(mediaPlayer: MediaPlayer?) {
        }

        override fun unlock(mediaPlayer: MediaPlayer?) {
        }
    }

    private inner class SkiaBitmapVideoSurface : CallbackVideoSurface(
        SkiaBitmapBufferFormatCallback(),
        SkiaBitmapRenderCallback(),
        true,
        videoSurfaceAdapter,
    )
}

internal object ByteBufferFactory {
    private val addressOffset = getAddressOffset()

    fun getAddress(buffer: ByteBuffer?): Long {
        return UnsafeAccess.UNSAFE.getLong(buffer, addressOffset)
    }

    @Suppress("DEPRECATION")
    private fun getAddressOffset(): Long {
        try {
            return UnsafeAccess.UNSAFE.objectFieldOffset(Buffer::class.java.getDeclaredField("address"))
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Suppress("DiscouragedPrivateApi")
    private object UnsafeAccess {
        val UNSAFE: Unsafe = run {
            val field: Field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.setAccessible(true)
            field.get(null) as Unsafe
        }
    }
}
