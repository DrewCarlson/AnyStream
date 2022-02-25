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
import anystream.frontend.components.SearchResultsList
import anystream.frontend.libs.PopperElement
import anystream.frontend.util.ExternalClickMask
import anystream.frontend.util.rememberDomElement
import anystream.models.Permission
import anystream.models.api.SearchResponse
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

private const val MENU_EXPANDED_KEY = "menu_expanded"
val searchQuery = MutableStateFlow<String?>(null)

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
                    SearchBar(client)
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
    var hovering by remember { mutableStateOf(false) }
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
private fun SecondaryMenu(client: AnyStreamClient, permissions: Set<Permission>) {
    val scope = rememberCoroutineScope()
    val authMutex = remember { Mutex() }
    Div({ classes("navbar-nav", "ms-auto") }) {
        if (Permission.check(Permission.ConfigureSystem, permissions)) {
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
private fun SearchBar(client: AnyStreamClient) {
    var focused by remember { mutableStateOf(false) }
    var elementValue by remember { mutableStateOf<String?>(null) }
    val inputRef = remember { mutableStateOf<HTMLInputElement?>(null) }
    val queryState by searchQuery
        .debounce(500)
        .collectAsState(null)
    val isDisplayingSearch = searchQuery
        .map { it != null }
        .collectAsState(queryState != null)

    val searchResponse by produceState<SearchResponse?>(null, queryState) {
        value = queryState?.let { query ->
            try {
                client.search(query)
            } catch (e: Throwable) {
                null
            }
        }
    }

    Form(null, {
        onSubmit { it.preventDefault() }
        classes("d-flex", "flex-row", "mx-4", "p-1", "rounded-pill")
        style {
            width(320.px)
            maxWidth(320.px)
            backgroundColor(if (focused) Color.white else hsla(0, 0, 100, .08))
            property("transition", "background-color .2s")
        }
    }) {
        val formRef by rememberDomElement()
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
            }
            onInput { event ->
                searchQuery.value = event.value.takeUnless(String::isNullOrBlank)
                elementValue = event.value
            }
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
        if (isDisplayingSearch.value) {
            searchResponse?.also { response ->
                formRef?.also { element ->
                    SearchResultPopper(
                        formRef = element,
                        focused = focused,
                        response = response,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultPopper(
    formRef: HTMLElement,
    focused: Boolean,
    response: SearchResponse,
) {
    var globalClickHandler by remember { mutableStateOf<ExternalClickMask?>(null) }
    PopperElement(
        formRef,
        attrs = {
            style {
                property("z-index", 100)
            }
        }
    ) {
        DisposableEffect(Unit) {
            globalClickHandler = ExternalClickMask(scopeElement) { remove ->
                // Hide search only if we're also unfocusing input
                if (!focused) {
                    searchQuery.value = null
                    remove()
                }
            }
            globalClickHandler?.attachListener()
            onDispose {
                globalClickHandler?.dispose()
                globalClickHandler = null
            }
        }
        SearchResultsList(response)
    }
}

@Composable
private fun ButtonIcon(
    icon: String,
    attrs: AttrsScope<HTMLElement>.() -> Unit = {},
    style: (StyleScope.() -> Unit) = {},
) {
    I({
        classes("bi", icon)
        attrs()
        style(style)
    })
}

@Composable
fun SideMenu(
    permissions: Set<Permission>,
) {
    var expanded by remember {
        mutableStateOf(localStorage.getItem(MENU_EXPANDED_KEY)?.toBoolean() ?: false)
    }
    Div({
        classes("d-inline-block", "mx-2", "py-2")
        style {
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

            // NavLink("Discover", "bi-search", false)
            Li {
                NavLink("Home", "bi-house", "/home", expanded)
            }
            if (Permission.check(Permission.ViewCollection, permissions)) {
                Li {
                    NavLink("Movies", "bi-film", "/movies", expanded)
                }
                Li {
                    NavLink("TV", "bi-tv", "/tv", expanded)
                }
            }
            if (Permission.check(Permission.ManageTorrents, permissions)) {
                Li {
                    NavLink("Downloads", "bi-cloud-arrow-down", "/downloads", expanded)
                }
            }
            Li({ classes("mt-auto") }) {
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
