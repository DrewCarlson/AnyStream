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
package anystream.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ktor.util.encodeBase64
import js.buffer.ArrayBuffer
import js.typedarrays.Int8Array
import kotlinx.browser.window
import kotlinx.io.Buffer
import org.jetbrains.compose.web.css.dppx
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.w3c.files.get

@Composable
fun BifViewer() {
    var bif by remember { mutableStateOf<BifFileReader?>(null) }
    Div(attrs = {
        ref { element ->
            element.ondrop = {
                it.preventDefault()
                val file = it.dataTransfer?.files?.get(0)
                if (file != null) {
                    val reader = org.w3c.files.FileReader()
                    reader.onload = { e ->
                        val data = Int8Array(reader.result as ArrayBuffer)
                        val bytes = data.unsafeCast<ByteArray>()
                        val buffer = Buffer()
                        buffer.write(bytes)
                        bif = BifFileReader.open(buffer)
                    }
                    reader.readAsArrayBuffer(file)
                }
            }
            onDispose {
                element.ondrop = null
            }
        }
    }) {
        bif?.apply {
            Div({
                classes("absolute", "size-full", "bg-dark")
            }) {
                Div({
                    classes("flex", "flex-row", "gap-2", "overflow-scroll")
                }) {
                    repeat(header.imageCount) { i ->
                        Img(
                            src = "data:image/webp;base64,${readFrame(i).bytes.encodeBase64()}",
                            attrs = {
                                style {
                                    height(100.dppx)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        window.ondragover = { it.preventDefault() }
        window.ondrag = { it.preventDefault() }
        onDispose {
            window.ondrag = null
            window.ondragover = null
        }
    }
}