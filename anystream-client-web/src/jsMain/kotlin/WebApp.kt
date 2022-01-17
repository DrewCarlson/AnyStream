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
package anystream.frontend

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.client.SessionManager
import anystream.frontend.util.Js
import app.softwork.routingcompose.BrowserRouter
import io.ktor.client.*
import kotlinx.browser.window
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HashChangeEvent

private val urlHashFlow = MutableStateFlow(window.location.hash)

fun webApp() = renderComposable(rootElementId = "root") {
    val client = remember {
        AnyStreamClient(
            serverUrl = window.location.run { "$protocol//$host" },
            sessionManager = SessionManager(JsSessionDataStore),
            http = HttpClient(Js),
        )
    }
    Div(
        attrs = {
            id("main-panel")
            classes("d-flex", "flex-column", "h-100", "w-100")
        },
    ) {
        DomSideEffect {
            val listener = { event: HashChangeEvent ->
                urlHashFlow.value = event.newURL.substringAfter("!")
                true
            }
            window.onhashchange = listener
            onDispose {
                window.onhashchange = null
            }
        }
        val hashValue by urlHashFlow
            .map { hash ->
                if (hash.contains("close")) {
                    null
                } else {
                    hash.substringAfter(":")
                        .takeIf(String::isNotBlank)
                }
            }
            .collectAsState(null)

        val backgroundUrl by backdropImageUrl.collectAsState(null)

        if (backgroundUrl != null) {
            Div({
                classes("position-absolute", "h-100", "w-100")
                style {
                    opacity(0.1)
                    backgroundImage("url('$backgroundUrl')")
                    backgroundPosition("center center")
                    backgroundSize("cover")
                    backgroundRepeat("no-repeat")
                    property("transition", "background 0.8s linear")
                }
            })
        }

        Div { Navbar(client) }
        ContentContainer(client)

        hashValue?.run {
            PlayerScreen(client, this)
        }
    }
}

@Composable
private fun ContentContainer(
    client: AnyStreamClient,
) {
    Div({
        classes(
            "container-fluid",
            "d-flex",
            "flex-row",
            "flex-grow-1",
            "flex-shrink-1",
            "px-0",
        )
        style {
            flexBasis("auto")
            overflowY("auto")
            property("z-index", "1")
        }
    }) {
        val authRoutes = listOf("/signup", "/login")
        val isAuthenticated by client.authenticated.collectAsState(client.isAuthenticated())
        val currentPath = BrowserRouter.getPath("/")
        val permissions by client.permissions.collectAsState(client.userPermissions())

        if (isAuthenticated) {
            SideMenu(
                permissions = permissions.orEmpty(),
            )
        }

        Div({
            classes("h-100", "w-100")
            style { overflowY("scroll") }
        }) {
            BrowserRouter("/") {
                route("home") {
                    noMatch { HomeScreen(client) }
                }
                route("login") {
                    noMatch { LoginScreen(client) }
                }
                route("signup") {
                    noMatch { SignupScreen(client) }
                }
                route("movies") {
                    noMatch { MoviesScreen(client) }
                }
                route("tv") {
                    noMatch { TvShowScreen(client) }
                }
                route("downloads") {
                    noMatch { DownloadsScreen(client) }
                }
                route("usermanager") {
                    noMatch { UserManagerScreen(client) }
                }
                route("settings") {
                    noMatch { SettingsScreen(client) }
                }
                route("media") {
                    string { id ->
                        MediaScreen(client, id)
                    }
                    noMatch { BrowserRouter.navigate("/home") }
                }
                noMatch {
                    BrowserRouter.navigate(if (isAuthenticated) "/home" else "/login")
                }
            }
        }
        if (!isAuthenticated && !authRoutes.contains(currentPath.value)) {
            BrowserRouter.navigate("/login")
        }
    }
}
