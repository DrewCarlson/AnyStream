/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun QrScanner(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        QrScannerNative(
            onScanned = onScanned,
            onClose = onClose,
        )
        QrCodeScannerOverlay()
    }
}

@Composable
internal expect fun QrScannerNative(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
private fun QrCodeScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val margin = 48.dp.toPx()
        val radius = 40.dp.toPx()
        val viewFinderSize = min(size.width, size.height) - margin * 2
        val viewFinderLeft = (size.width - viewFinderSize) / 2
        val viewFinderTop = (size.height - viewFinderSize) / 2
        val viewFinderRect = Rect(
            offset = Offset(viewFinderLeft, viewFinderTop),
            size = Size(viewFinderSize, viewFinderSize)
        )

        val path = Path().apply {
            fillType = PathFillType.EvenOdd

            // Outer full-screen rectangle
            addRect(Rect(Offset.Zero, size))

            // Inner rounded rectangle
            addRoundRect(
                RoundRect(
                    rect = viewFinderRect,
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }

        drawPath(
            path = path,
            color = Color.Black.copy(alpha = 0.6f)
        )

        drawRoundRect(
            color = Color.White,
            topLeft = viewFinderRect.topLeft,
            size = viewFinderRect.size,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}