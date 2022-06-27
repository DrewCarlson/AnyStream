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
package anystream.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement

@Composable
fun LoadingIndicator(
    small: Boolean = false,
    attrs: AttrBuilderContext<HTMLDivElement>? = null
) {
    Div({
        attr("role", "status")
        classes("spinner-border", "text-primary")
        if (small) {
            classes("spinner-border-sm")
        } else {
            style {
                height(3.em)
                width(3.em)
            }
        }
        attrs?.invoke(this)
    }) {
        Span({
            classes("visually-hidden")
        }) {
            Text("Loading")
        }
    }
}

@Composable
fun FullSizeCenteredLoader() {
    Div({ classes("d-flex", "justify-content-center", "align-items-center", "h-100", "h-100") }) {
        LoadingIndicator()
    }
}
