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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

private val RECT_ZERO = Rect(0f, 0f, 0f, 0f)

@Composable
internal actual fun PlatformVideoPlayer(
    playerHandle: PlayerHandle,
    modifier: Modifier,
) {
    val vlcjPlayerHandle = remember(playerHandle) { playerHandle as VlcjPlayerHandle }
    val surface = remember {
        SkiaImageVideoSurface().also {
            vlcjPlayerHandle.mediaPlayer.videoSurface().set(it)
        }
    }

    val frame by surface.frame
    var outputRect by remember { mutableStateOf(RECT_ZERO) }

    LaunchedEffect(frame?.imageInfo) {
        outputRect = RECT_ZERO
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { outputRect = RECT_ZERO }
    ) {
        fun createOutputRect(currentFrame: Image): Rect {
            val frameWidth = currentFrame.width.toFloat()
            val frameHeight = currentFrame.height.toFloat()
            val canvasWidth = size.width
            val canvasHeight = size.height

            val imageAspectRatio = frameWidth / frameHeight
            val canvasAspectRatio = canvasWidth / canvasHeight

            return if (imageAspectRatio > canvasAspectRatio) {
                val scaledHeight = canvasWidth / imageAspectRatio
                val topOffset = (canvasHeight - scaledHeight) / 2f
                Rect(0f, topOffset, canvasWidth, topOffset + scaledHeight)
            } else {
                val scaledWidth = canvasHeight * imageAspectRatio
                val leftOffset = (canvasWidth - scaledWidth) / 2f
                Rect(leftOffset, 0f, leftOffset + scaledWidth, canvasHeight)
            }
        }
        frame?.let { currentFrame ->
            drawIntoCanvas { canvas ->
                if (outputRect == RECT_ZERO) {
                    outputRect = createOutputRect(currentFrame)
                }
                canvas.nativeCanvas.drawImageRect(currentFrame, outputRect)
            }
        }
    }
}
