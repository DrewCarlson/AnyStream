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
import anystream.models.Permissions
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.localStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.web.attributes.AttrsBuilder
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

private const val MENU_EXPANDED_KEY = "menu_expanded"
val searchQuery = MutableStateFlow<String?>(null)
val searchWindowPosition = MutableStateFlow(Triple(0, 0, 0))

@Composable
fun Navbar(client: AnyStreamClient) {
    val isAuthenticated = client.authenticated.collectAsState(client.isAuthenticated())
    Nav({
        classes(
            "navbar", "navbar-expand-lg", "navbar-dark",
            "rounded", "shadow", "m-2"
        )
        style {
            backgroundColor(rgba(0, 0, 0, 0.3))
        }
    }) {
        Div({ classes("container-fluid") }) {
            A(attrs = {
                classes("navbar-brand", "mx-2")
                style {
                    cursor("pointer")
                }
                onClick { BrowserRouter.navigate("/home") }
            }) {
                Img(src = "/images/as-logo.svg")
            }
            Div({ classes("collapse", "navbar-collapse") }) {
                val permissionsState = client.permissions.collectAsState(null)
                if (isAuthenticated.value) {
                    SearchBar()
                    SecondaryMenu(client, permissionsState.value ?: emptySet())
                }
            }
        }
    }
}

@Composable
private fun NavLink(
    text: String,
    icon: String,
    path: String,
    expanded: Boolean,
) {
    val currentPath = BrowserRouter.getPath("/")
    var hovering by mutableStateOf(false)
    A(attrs = {
        classes("nav-link")
        style {
            backgroundColor(Color.transparent)
            val isActive = currentPath.value.startsWith(path)
            when {
                hovering && isActive -> color(Color.white) // TODO: active indicator icon
                hovering -> color(Color.white)
                isActive -> color(rgb(255, 8, 28)) // TODO: active indicator icon
                else -> color(rgba(255, 255, 255, 0.7))
            }
        }
        onMouseEnter { hovering = true }
        onMouseLeave { hovering = false }
        onClick { BrowserRouter.navigate(path) }
    }) {
        ButtonIcon(icon)
        if (expanded) {
            Span({ classes("ms-3") }) {
                Text(text)
            }
        }
    }
}

@Composable
private fun SecondaryMenu(client: AnyStreamClient, permissions: Set<String>) {
    val scope = rememberCoroutineScope()
    val authMutex = Mutex()
    Div({ classes("navbar-nav", "ms-auto") }) {
        if (Permissions.check(Permissions.CONFIGURE_SYSTEM, permissions)) {
            A(attrs = {
                classes("nav-link")
                onClick { BrowserRouter.navigate("/usermanager") }
            }) {
                I({ classes("bi", "bi-people") })
            }
            A(attrs = {
                classes("nav-link")
                onClick { BrowserRouter.navigate("/settings") }
            }) {
                I({ classes("bi", "bi-gear") })
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
            I({ classes("bi", "bi-box-arrow-right") }) { }
        }
    }
}

@Composable
private fun SearchBar() {
    var focused by remember { mutableStateOf(false) }
    var elementValue by remember { mutableStateOf<String?>(null) }
    val inputRef = mutableStateOf<HTMLInputElement?>(null)
    val scope = rememberCoroutineScope()
    Form(null, {
        onSubmit { it.preventDefault() }
        classes("mx-4", "p-1", "rounded-pill")
        style {
            width(320.px)
            maxWidth(320.px)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            backgroundColor(if (focused) Color.white else hsla(0, 0, 100, .08))
            property("transition", "background-color .2s")
        }
    }) {
        DomSideEffect { element ->
            scope.launch {
                delay(100)// fixme: Delay to ensure correct position is provided
                val rect = element.getBoundingClientRect()
                val searchTop = rect.bottom.toInt()
                val searchLeft = rect.left.toInt()
                val searchWidth = element.clientWidth
                searchWindowPosition.value = Triple(searchTop, searchLeft, searchWidth)
            }
            onDispose {
                searchWindowPosition.value = Triple(0, 0, 0)
            }
        }
        I({
            classes("bi", "bi-search", "p-1")
            style {
                if (focused) {
                    color(rgba(0, 0, 0, .8))
                }
                property("transition", "color .2s")
            }
        })
        SearchInput {
            ref { newRef ->
                inputRef.value = newRef
                onDispose {
                    inputRef.value = null
                }
            }
            onFocus {
                focused = true
                searchQuery.value = (it.target as? HTMLInputElement)
                    ?.value
                    ?.takeUnless(String::isNullOrBlank)
            }
            onFocusOut {
                focused = false

                scope.launch {
                    // TODO: Improve search result clear behavior
                    delay(100)
                    searchQuery.value = null
                }
            }
            onInput { event ->
                searchQuery.value = event.value.takeUnless(String::isNullOrBlank)
                elementValue = event.value
            }
            value(elementValue ?: "")
            classes("w-100")
            style {
                backgroundColor(Color.transparent)
                outline("0")
                property("border", 0)
                property("transition", "color .2s")
                if (focused) {
                    color(rgba(0, 0, 0, .8))
                } else {
                    color(Color.white)
                }
            }
        }
        I({
            classes("bi", "bi-x-circle-fill", "p-1")
            onClick {
                inputRef.value?.run {
                    value = ""
                    elementValue = null
                    focus()
                }
            }
            style {
                property("transition", "color .2s")
                if (focused) {
                    color(rgba(0, 0, 0, .8))
                }
                if (elementValue.isNullOrBlank()) {
                    opacity(0)
                } else {
                    cursor("pointer")
                }
            }
        })
    }
}

@Composable
private fun ButtonIcon(
    icon: String,
    attrs: AttrsBuilder<HTMLElement>.() -> Unit = {},
    style: (StyleBuilder.() -> Unit) = {},
) {
    I({
        classes("bi", icon)
        attrs()
        style(style)
    })
}

@Composable
fun SideMenu(
    client: AnyStreamClient,
    permissions: Set<String>,
) {
    var expanded by mutableStateOf(localStorage.getItem(MENU_EXPANDED_KEY)?.toBoolean() ?: false)
    Div({
        classes("mx-2", "py-2")
        style {
            display(DisplayStyle.InlineBlock)
            property("transition", "width .2s ease-in-out 0s")
            if (expanded) {
                width(250.px)
                minWidth(250.px)
            } else {
                width(53.px)
                minWidth(53.px)
            }
        }
    }) {
        Ul({
            classes(
                "nav", "nav-pills", "flex-column",
                "h-100", "mb-auto", "rounded", "shadow"
            )
            style {
                overflow("hidden")
                backgroundColor(rgba(0, 0, 0, 0.3))
            }
        }) {

            //NavLink("Discover", "bi-search", false)
            Li {
                NavLink("Home", "bi-house", "/home", expanded)
            }
            if (Permissions.check(Permissions.VIEW_COLLECTION, permissions)) {
                Li {
                    NavLink("Movies", "bi-film", "/movies", expanded)
                }
                Li {
                    NavLink("TV", "bi-tv", "/tv", expanded)
                }
            }
            if (Permissions.check(Permissions.TORRENT_MANAGEMENT, permissions)) {
                Li {
                    NavLink("Downloads", "bi-cloud-arrow-down", "/downloads", expanded)
                }
            }
            Li {
                A(attrs = {
                    classes("nav-link")
                    style {
                        backgroundColor(Color.transparent)
                        color(rgba(255, 255, 255, 0.7))
                    }
                    onClick {
                        expanded = !expanded
                        localStorage.setItem(MENU_EXPANDED_KEY, expanded.toString())
                    }
                }) {
                    ButtonIcon(if (expanded) "bi-arrow-bar-left" else "bi-arrow-bar-right")
                    if (expanded) {
                        Span({ classes("ms-3") }) {
                            Text("Hide")
                        }
                    }
                }
            }
        }
    }
}