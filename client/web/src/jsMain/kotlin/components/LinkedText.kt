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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.overflow
import org.jetbrains.compose.web.css.textDecoration
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLDivElement

@Composable
fun LinkedText(
    url: String,
    router: Router = Router.current,
    afterClick: (() -> Unit)? = null,
    attrs: AttrBuilderContext<HTMLAnchorElement>? = null,
    content: @Composable () -> Unit,
) {
    var hovering by remember { mutableStateOf(false) }
    A(url, {
        attrs?.invoke(this)
        onClick {
            it.preventDefault()
            router.navigate(url)
            afterClick?.invoke()
        }
        onMouseEnter { hovering = true }
        onMouseLeave { hovering = false }
        classes("plain-link")
        style {
            cursor("pointer")
            textDecoration(if (hovering) "underline" else "none")
            property("text-overflow", "ellipsis")
            overflow("hidden")
        }
    }) {
        content()
    }
}
