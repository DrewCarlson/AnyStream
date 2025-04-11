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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.cValue
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIColor.Companion.blackColor
import platform.UIKit.UIView

@Composable
internal actual fun PlatformVideoPlayer(
    playerHandle: PlayerHandle,
    modifier: Modifier,
) {
    val avPlayerHandle = remember(playerHandle) { playerHandle as AvPlayerHandle }

    val factory = remember(avPlayerHandle) {
        {
            object : UIView(cValue { CGRectZero }) {
                private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(avPlayerHandle.player)

                init {
                    backgroundColor = blackColor
                    layer.addSublayer(playerLayer)
                }

                override fun layoutSubviews() {
                    super.layoutSubviews()
                    playerLayer.setFrame(bounds)
                }
            }
        }
    }
    UIKitView(
        factory = factory,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        properties = UIKitInteropProperties(
            isInteractive = false,
            isNativeAccessibilityEnabled = false
        )
    )
}

