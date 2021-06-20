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
import anystream.models.Permissions
import app.softwork.routingcompose.BrowserRouter
import kotlinx.coroutines.launch
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
            Div({ classes("collapse", "navbar-collapse") }) {
                val permissionsState = client.permissions.collectAsState(null)
                if (isAuthenticated.value) {
                    MainMenu(permissionsState.value ?: emptySet())
                    SecondaryMenu(client, permissionsState.value ?: emptySet())
                }
            }
        }
    }
}

@Composable
private fun MainMenu(permissions: Set<String>) {
    Div({ classes("navbar-nav") }) {
        //NavLink("Discover", "bi-search", false)
        NavLink("Home", "bi-house", "/home")
        if (Permissions.check(Permissions.VIEW_COLLECTION, permissions)) {
            NavLink("Movies", "bi-film", "/movies")
            NavLink("TV", "bi-tv", "/tv")
        }
        if (Permissions.check(Permissions.TORRENT_MANAGEMENT, permissions)) {
            NavLink("Downloads", "bi-cloud-arrow-down", "/downloads")
        }
    }
}

@Composable
private fun NavLink(text: String, icon: String, path: String) {
    val currentPath = BrowserRouter.getPath("/")
    A(attrs = {
        if (currentPath.value.startsWith(path)) {
            classes("nav-link", "mx-2", "active")
        } else {
            classes("nav-link", "mx-2")
        }
        onClick { BrowserRouter.navigate(path) }
    }) {
        ButtonIcon(icon)
        Text(text)
    }
}

@Composable
private fun SecondaryMenu(client: AnyStreamClient, permissions: Set<String>) {
    val scope = rememberCoroutineScope()
    val authMutex = Mutex()
    Div({ classes("navbar-nav", "ms-auto") }) {
        if (Permissions.check(Permissions.GLOBAL, permissions)) {
            A(attrs = {
                classes("nav-link")
                onClick { BrowserRouter.navigate("/usermanager") }
            }) {
                I({ classes("bi-people") })
            }
        }
        A(attrs = {
            onClick {
                scope.launch {
                    authMutex.withLock { client.logout() }
                }
            }
            classes("nav-link")
        }) {
            I({ classes("bi-box-arrow-right") }) { }
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
    })
}