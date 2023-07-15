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
package anystream.ui.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Toolkit

actual val cardWidth: Dp
    get() {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val screenWidth: Int = screenSize.width
        return (screenWidth.dp / 5).coerceAtMost(250.dp)
    }

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.pointerMover(
    onHover: (Boolean) -> Unit,
): Modifier = composed {
    onPointerEvent(PointerEventType.Enter) { onHover(true) }
        .onPointerEvent(PointerEventType.Exit) { onHover(false) }
}
