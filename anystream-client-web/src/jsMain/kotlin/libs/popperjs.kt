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
package anystream.frontend.libs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NoLiveLiterals
import kotlinext.js.js
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.ElementScope
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

// https://popper.js.org/docs/v2/
// https://github.com/popperjs/popper-core/blob/2e909c71b2d09582a373aaa697152012016735ad/src/types.js
@Composable
fun PopperElement(
    target: HTMLElement,
    popperOptions: PopperOptions? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    body: @Composable ElementScope<HTMLDivElement>.() -> Unit,
) {
    Div(attrs = attrs) {
        Div {
            body()
            DisposableEffect(target) {
                val popper = if (popperOptions == null) {
                    Popper.createPopper(target, scopeElement)
                } else {
                    Popper.createPopper(target, scopeElement, popperOptions)
                }
                onDispose { popper.destroy() }
            }
        }
    }
}

@Composable
fun PopperElement(
    virtualElement: PopperVirtualElement,
    popperOptions: PopperOptions? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    body: @Composable ElementScope<HTMLDivElement>.() -> Unit,
) {
    Div(attrs = attrs) {
        Div {
            body()
            DisposableEffect(virtualElement) {
                val popper = if (popperOptions == null) {
                    Popper.createPopper(virtualElement, scopeElement)
                } else {
                    Popper.createPopper(virtualElement, scopeElement, popperOptions)
                }
                onDispose { popper.destroy() }
            }
        }
    }
}

@JsModule("@popperjs/core")
@JsNonModule
external object Popper {
    fun createPopper(
        target: HTMLElement,
        tooltip: HTMLElement,
        options: PopperOptions = definedExternally,
    ): PopperInstance

    fun createPopper(
        target: PopperVirtualElement,
        tooltip: HTMLElement,
        options: PopperOptions = definedExternally,
    ): PopperInstance
}

external class PopperInstance {
    val state: PopperState

    fun forceUpdate()
    fun update()
    // fun setOptions(options): Promise<State>
    fun destroy()
}

external class PopperState {
    var placement: String
    var strategy: String
    var rects: PopperStateRects
}

external class PopperRect {
    var width: Int
    var height: Int
    var x: Int
    var y: Int
}

external class PopperStateRects {
    var reference: PopperRect
    var popper: PopperRect
}

external interface PopperVirtualElement {
    fun getBoundingClientRect(): dynamic
}

/**
 * A fixed position element for static placement of the popper.
 */
fun popperFixedPosition(x: Int, y: Int): PopperVirtualElement {
    return object : PopperVirtualElement {
        override fun getBoundingClientRect(): dynamic {
            return js @NoLiveLiterals {
                width = 0
                height = 0
                top = y
                bottom = y
                right = x
                left = x
            }
        }
    }
}

data class PopperModifier(
    var name: String,
    var options: dynamic,
)

data class PopperOptions(
    var placement: String = "auto",
    val modifier: MutableList<PopperModifier> = mutableListOf(),
)
