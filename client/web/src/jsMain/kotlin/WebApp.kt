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
package anystream

import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import anystream.client.AnyStreamClient
import anystream.client.coreModule
import anystream.components.Navbar
import anystream.components.SideMenu
import anystream.screens.*
import anystream.screens.settings.SettingsScreen
import anystream.screens.settings.SettingsSideMenu
import anystream.util.get
import anystream.util.getKoin
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import kotlinx.browser.document
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable
import org.koin.core.context.startKoin
import org.w3c.dom.HTMLDivElement

val playerMediaLinkId = MutableStateFlow<String?>(null)
val LocalAnyStreamClient = compositionLocalOf<AnyStreamClient> { error("AnyStream client not provided") }

fun webApp() = renderComposable(rootElementId = "root") {
    startKoin {
        modules(coreModule())
    }
    // Consume session token from cookie after oauth flow
    val client = get<AnyStreamClient>()
    LaunchedEffect(Unit) {
        if (client.user.isAuthenticated()) return@LaunchedEffect
        val cookies = document.cookie.split(';').toMutableSet()
        cookies.forEach { cookie ->
            if (cookie.startsWith("as_user_session")) {
                cookies.remove(cookie)
                document.cookie = cookies.joinToString(";")
                client.user.completeOauth(cookie.substringAfter('='))
                return@forEach
            }
        }
    }
    Div({
        classes("flex", "flex-col", "size-full")
        style {
            backgroundImage("url(\"images/noise.webp\")")
            backgroundRepeat("repeat")
        }
    }) {
        val backgroundUrl by backdropImageUrl.collectAsState(null)
        var visible by remember { mutableStateOf(false) }
        val actualBackgroundUrl by produceState(backgroundUrl, backgroundUrl) {
            visible = backgroundUrl != null
            value = backgroundUrl ?: value
        }

        Div({
            classes("absolute", "size-full", "fade-in")
            style {
                opacity(if (visible) 0.1 else 0)
                if (actualBackgroundUrl != null) {
                    backgroundImage("url('$actualBackgroundUrl')")
                }
                backgroundPosition("center center")
                backgroundSize("cover")
                backgroundRepeat("no-repeat")
                property("pointer-events", "none")
            }
        })

        CompositionLocalProvider(
            LocalAnyStreamClient provides getKoin().get()
        ) {
            ContentContainer()
        }
    }
}

@Composable
private fun ContentContainer(client: AnyStreamClient = get()) {
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
        route("library") {
            string { id ->
                ScreenContainer {
                    LibraryScreen(libraryId = id)
                }
            }
            noMatch { redirect("/") }
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
        noMatch { redirect(if (client.user.isAuthenticated()) "/home" else "/login") }

        val metadataId by playerMediaLinkId.collectAsState()
        metadataId?.let { PlayerScreen(it) }
    }
}

@Composable
private fun ScreenContainer(
    menu: @Composable () -> Unit = { SideMenu() },
    content: ContentBuilder<HTMLDivElement>,
) {
    val authRoutes = remember { listOf("/signup", "/login") }
    val client = get<AnyStreamClient>()
    val isAuthenticated by client.user.authenticated.collectAsState(client.user.isAuthenticated())
    val router = Router.current
    val currentPath by router.getPath("/")

    Div { Navbar() }
    Div({
        classes(
            "w-full",
            "flex",
            "flex-row",
            "flex-grow-1",
            "flex-shrink-1",
            "px-0",
            "overflow-hidden",
        )
        style {
            flexBasis("auto")
        }
    }) {
        menu()

        Div({
            classes("flex", "flex-col", "w-full")
            style { overflowX("hidden") }
        }, content)
    }

    remember(isAuthenticated, currentPath) {
        if (!isAuthenticated && !authRoutes.any { currentPath.startsWith(it) }) {
            router.navigate("/login")
        }
    }
}
