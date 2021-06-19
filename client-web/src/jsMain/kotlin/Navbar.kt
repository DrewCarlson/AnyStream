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
package anystream.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import anystream.client.AnyStreamClient
import com.soywiz.korio.async.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.web.attributes.AttrsBuilder
import org.jetbrains.compose.web.css.StyleBuilder
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Nav
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

@Composable
fun Navbar(client: AnyStreamClient) {
    val isAuthenticated = client.authenticated.collectAsState(client.isAuthenticated())
    Nav({ classes("navbar", "navbar-expand-lg", "navbar-dark", "bg-dark") }) {
        Div({ classes("container-fluid") }) {
            A(attrs = { classes("navbar-brand", "mx-2") }) {
                Img(src = "/images/as-logo.svg")
            }
            Div({ classes("collapse", "navbar-collapse", "pt-2") }) {
                if (isAuthenticated.value) {
                    MainMenu(client)
                    SecondaryMenu(client)
                }
            }
        }
    }
}

@Composable
private fun MainMenu(client: AnyStreamClient) {
    Div({ classes("navbar-nav") }) {
        //NavLink("Discover", "bi-search", false)
        NavLink("Home", "bi-house", true)
        NavLink("Movies", "bi-film", false)
        NavLink("TV", "bi-tv", false)
        NavLink("Download", "bi-cloud-arrow-down", false)
    }
}

@Composable
private fun NavLink(text: String, icon: String, isActive: Boolean) {
    A(attrs = {
        if (isActive) {
            classes("nav-link", "mx-2", "active")
        } else {
            classes("nav-link", "mx-2")
        }
    }) {
        ButtonIcon(icon)
        Text(text)
    }
}

@Composable
private fun SecondaryMenu(client: AnyStreamClient) {
    val scope = rememberCoroutineScope()
    val authMutex = Mutex()
    Div({ classes("navbar-nav", "ms-auto") }) {
        A(attrs = { classes("nav-link") }) {
            I(attrs = { classes("bi-people") }) { }
        }
        A(attrs = {
            onClick {
                scope.launch {
                    authMutex.withLock { client.logout() }
                }
            }
            classes("nav-link")
        }) {
            I(attrs = { classes("bi-box-arrow-right") }) { }
        }
    }
}

@Composable
private fun ButtonIcon(
    icon: String,
    attrs: AttrsBuilder<HTMLElement>.() -> Unit = {},
    style: (StyleBuilder.() -> Unit) = {},
) {
    I({
        classes(icon, "mx-2")
        attrs()
        style(style)
    }, {

    })
}