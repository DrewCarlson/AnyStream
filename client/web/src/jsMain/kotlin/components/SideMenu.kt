/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.client.json
import anystream.models.Library
import anystream.models.Permission
import anystream.util.bootstrapIcon
import anystream.util.get
import anystream.util.tooltip
import app.softwork.routingcompose.Router
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement

private const val MENU_EXPANDED_KEY = "menu_expanded"

@Composable
fun SideMenu() {
    val client = get<AnyStreamClient>()
    val permissions by remember {
        client.user
            .permissions
            .map { it.orEmpty() }
    }.collectAsState(client.user.userPermissions())
    var expanded by remember {
        mutableStateOf(localStorage.getItem(MENU_EXPANDED_KEY)?.toBoolean() == true)
    }
    val libraries by produceState(emptyList<Library>()) {
        value =
            try {
                localStorage.getItem("LIBRARIES_LIST")
                    ?.run(json::decodeFromString)
            } catch (_: Throwable) {
                localStorage.removeItem("LIBRARIES_LIST")
                null
            } ?: try {
                client.getLibraries()
            } catch (e: Throwable) {
                e.printStackTrace()
                emptyList()
            }
        if (value.isNotEmpty()) {
            localStorage.setItem("LIBRARIES_LIST", json.encodeToString(value))
        }
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
                "nav",
                "nav-pills",
                "bg-dark-translucent",
                "flex-column",
                "h-100",
                "py-2",
                "mb-auto",
                "rounded",
                "shadow",
            )
            attr("role", "navigation")
            style {
                overflow("hidden")
            }
        }) {
            // NavLink("Discover", "bi-search", false)
            Li({ classes("nav-item") }) {
                NavLink("Home", "bi-house", "/home", expanded)
            }
            if (Permission.check(Permission.ManageTorrents, permissions)) {
                Li({ classes("nav-item") }) {
                    NavLink("Downloads", "bi-cloud-arrow-down", "/downloads", expanded)
                }
            }
            if (Permission.check(Permission.ViewCollection, permissions)) {
                libraries.forEach { library ->
                    Li({ classes("nav-item") }) {
                        NavLink(
                            text = library.name,
                            icon = library.mediaKind.bootstrapIcon,
                            path = "/${library.name.lowercase()}",
                            expanded = expanded
                        )
                    }
                }
            }
            Li({ classes("nav-item", "mt-auto") }) {
                A(attrs = {
                    classes("nav-link", "nav-link-large")
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

@Composable
fun NavLink(
    text: String,
    icon: String,
    path: String,
    expanded: Boolean = true,
    attrs: AttrsScope<HTMLAnchorElement>.() -> Unit = {},
) {
    val router = Router.current
    val currentPath by router.getPath("/")
    val isActive = remember(currentPath) { currentPath.startsWith(path) }
    A(attrs = {
        classes("nav-link", "nav-link-large")
        attrs()
        if (isActive) {
            classes("active")
            attr("aria-current", "page")
        }
        onClick { router.navigate(path) }
        if (!expanded) {
            tooltip(text, "right")
        }
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
