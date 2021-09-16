/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.frontend.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement


@Composable
fun LoadingIndicator(
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
) {
    Div({
        attrs?.invoke(this)
        classes("spinner-border")
        attr("role", "status")
        style {
            height(3.em)
            width(3.em)
            color(rgba(199, 8, 28, 0.8))
        }
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
    Div({
        classes("h-100", "h-100")
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            alignItems(AlignItems.Center)
        }
    }) {
        LoadingIndicator()
    }
}