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
package anystream.frontend.util

import androidx.compose.runtime.*
import org.jetbrains.compose.web.dom.ElementScope
import org.w3c.dom.HTMLElement

@Composable
fun <T : HTMLElement> ElementScope<T>.rememberDomElement(key: Any? = null): State<HTMLElement?> {
    val state = remember { mutableStateOf<HTMLElement?>(null) }
    DisposableEffect(key) {
        state.value = scopeElement
        onDispose {
            state.value = null
        }
    }
    return state
}
