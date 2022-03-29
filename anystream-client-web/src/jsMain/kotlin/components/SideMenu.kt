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
package anystream.frontend.components

import androidx.compose.runtime.*
import anystream.frontend.util.tooltip
import anystream.models.Permission
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.localStorage
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement

private const val MENU_EXPANDED_KEY = "menu_expanded"

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
