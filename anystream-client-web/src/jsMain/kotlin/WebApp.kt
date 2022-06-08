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
import anystream.frontend.components.Navbar
import anystream.frontend.components.SideMenu
import anystream.frontend.screens.*
import anystream.frontend.util.Js
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import io.ktor.client.*
import kotlinx.browser.window
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HashChangeEvent

private val urlHashFlow = MutableStateFlow(window.location.hash)

val LocalAnyStreamClient = compositionLocalOf<AnyStreamClient> { error("Client not yet created.") }

fun webApp() = renderComposable(rootElementId = "root") {
    val client = remember {
        AnyStreamClient(
            serverUrl = window.location.run { "$protocol//$host" },
            sessionManager = SessionManager(JsSessionDataStore),
            http = HttpClient(Js),
        )
    }
    CompositionLocalProvider(
        LocalAnyStreamClient provides client
    ) {
        Div(
            attrs = {
                id("main-panel")
                classes("d-flex", "flex-column", "h-100", "w-100")
            },
        ) {
            DisposableEffect(Unit) {
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

            ContentContainer()

            hashValue?.run { PlayerScreen(this) }
        }
    }
}

@Composable
private fun ContentContainer() {
    val client = LocalAnyStreamClient.current
    BrowserRouter("/") {
        route("home") {
            noMatch { ScreenContainer { HomeScreen() } }
        }
        route("login") {
            noMatch { ScreenContainer({}) { LoginScreen() } }
        }
        route("signup") {
            noMatch { ScreenContainer({}) { SignupScreen() } }
        }
        route("movies") {
            noMatch { ScreenContainer { MoviesScreen() } }
        }
        route("tv") {
            noMatch { ScreenContainer { TvShowScreen() } }
        }
        route("downloads") {
            noMatch { ScreenContainer { DownloadsScreen() } }
        }
        route("settings") {
            string { subScreen ->
                ScreenContainer({ SettingsSideMenu() }) {
                    SettingsScreen(subScreen)
                }
            }
        }
        route("media") {
            string { id -> ScreenContainer { MediaScreen(id) } }
            noMatch { redirect("/home") }
        }
        noMatch { redirect(if (client.isAuthenticated()) "/home" else "/login") }
    }
}

@Composable
private fun ScreenContainer(
    menu: @Composable () -> Unit = { SideMenu() },
    content: ContentBuilder<HTMLDivElement>
) {
    val authRoutes = remember { listOf("/signup", "/login") }
    val client = LocalAnyStreamClient.current
    val isAuthenticated by client.authenticated.collectAsState(client.isAuthenticated())
    val router = Router.current
    val currentPath by router.getPath("/")

    Div { Navbar() }
    Div({
        classes(
            "container-fluid",
            "d-flex",
            "flex-row",
            "flex-grow-1",
            "flex-shrink-1",
            "px-0",
            "overflow-hidden"
        )
        style {
            flexBasis("auto")
            property("z-index", "1")
        }
    }) {
        menu()

        Div({ classes("vstack", "h-100", "w-100", "overflow-hidden") }, content)
    }

    remember(isAuthenticated, currentPath) {
        if (!isAuthenticated && !authRoutes.any { currentPath.startsWith(it) }) {
            router.navigate("/login")
        }
    }
}
