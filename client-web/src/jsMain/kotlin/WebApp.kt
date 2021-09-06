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

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.client.SessionManager
import anystream.frontend.components.SearchResultsList
import anystream.frontend.screens.*
import anystream.models.api.SearchResponse
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.window
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HashChangeEvent

private val urlHashFlow = MutableStateFlow(window.location.hash)

fun webApp() = renderComposable(rootElementId = "root") {
    val client = AnyStreamClient(
        serverUrl = window.location.run { "$protocol//$host" },
        sessionManager = SessionManager(JsSessionDataStore)
    )
    Div(
        attrs = {
            id("main-panel")
            classes("h-100", "w-100")
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
            }
        },
    ) {
        DomSideEffect {
            val listener = { event: HashChangeEvent ->
                urlHashFlow.value = event.newURL.substringAfter("!")
                true
            }
            window.onhashchange = listener
            onDispose { window.onhashchange = null }
        }
        val queryState by searchQuery
            .debounce(500)
            .collectAsState(null)
        val isDisplayingSearch = searchQuery
            .map { it != null }
            .collectAsState(queryState != null)
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

        Div { Navbar(client) }
        ContentContainer(client)

        hashValue?.run {
            PlayerScreen(client, this)
        }

        val searchResponse by produceState<SearchResponse?>(null, queryState) {
            value = queryState?.let { query ->
                try {
                    client.search(query)
                } catch (e: Throwable) {
                    null
                }
            }
        }

        if (isDisplayingSearch.value) {
            searchResponse?.also { response ->
                SearchResultsList(response)
            }
        }
    }
}

@Composable
private fun ContentContainer(
    client: AnyStreamClient,
) {
    Div({
        classes("container-fluid", "px-0")
        style {
            flexGrow(1)
            flexShrink(1)
            flexBasis("auto")
            overflowY("auto")
        }
    }) {
        val authRoutes = listOf("/signup", "/login")
        val isAuthenticated by client.authenticated.collectAsState(client.isAuthenticated())
        val currentPath = BrowserRouter.getPath("/")
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
        if (!isAuthenticated && !authRoutes.contains(currentPath.value)) {
            BrowserRouter.navigate("/login")
        }
    }
}
