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
package anystream.libs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.jetbrains.compose.web.ExperimentalComposeWebApi
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.dom.Canvas
import org.w3c.dom.HTMLCanvasElement

// https://www.npmjs.com/package/qrcode#browser-api
@JsModule("qrcode")
@JsNonModule
external object QRCode {

    fun toCanvas(
        element: HTMLCanvasElement,
        text: String,
        callback: (error: dynamic) -> Unit = definedExternally,
    )
}

@OptIn(ExperimentalComposeWebApi::class)
@Composable
fun QRCodeImage(
    text: String?,
    attrs: AttrsScope<HTMLCanvasElement>.() -> Unit,
) {
    Canvas(attrs) {
        DisposableEffect(text) {
            if (!text.isNullOrBlank()) {
                QRCode.toCanvas(scopeElement, text)
            }
            onDispose { }
        }
    }
}
