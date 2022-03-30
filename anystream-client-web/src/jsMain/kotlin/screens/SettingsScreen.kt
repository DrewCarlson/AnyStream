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
package anystream.frontend.screens

import androidx.compose.runtime.*
import anystream.frontend.LocalAnyStreamClient
import anystream.frontend.models.toMediaItem
import anystream.frontend.util.tooltip
import anystream.models.api.PlaybackSessionsResponse
import anystream.util.formatProgressAndRuntime
import app.softwork.routingcompose.BrowserRouter
import kotlinx.coroutines.flow.retry
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.time.Duration.Companion.seconds

@Composable
fun SettingsScreen(subscreen: String) {
    val scope = rememberCoroutineScope()
    Div({ classes("d-flex", "p-2", "h-100") }) {
        when (subscreen) {
            "activity" -> ActiveStreamsList()
            "import" -> ImportMediaScreen(scope)
        }
    }
}

@Composable
fun SettingsSideMenu() {
    Div({
        classes("d-inline-block", "mx-2", "py-2")
        style {
            property("transition", "width .2s ease-in-out 0s")
            width(250.px)
            minWidth(250.px)
        }
    }) {
        Ul({
            classes("nav", "flex-column", "h-100", "py-2", "mb-auto", "rounded", "shadow")
            style {
                overflow("hidden")
                backgroundColor(rgba(0, 0, 0, 0.3))
            }
        }) {
            Li({ classes("nav-item") }) {
                NavLink("Activity", "/settings/activity", true)
            }
            Li({ classes("nav-item") }) {
                NavLink("Import Media", "/settings/import", true)
            }
        }
    }
}

@Composable
private fun NavLink(
    text: String,
    path: String,
    expanded: Boolean,
) {
    val currentPath = BrowserRouter.getPath("/")
    var hovering by remember { mutableStateOf(false) }
    A(attrs = {
        classes("nav-link", "nav-link-small")
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
        if (expanded) {
            Text(text)
        }
    }
}

@Composable
private fun ActiveStreamsList() {
    val client = LocalAnyStreamClient.current
    val sessionsResponse by client.observeStreams().retry().collectAsState(PlaybackSessionsResponse())

    Div({ classes("d-flex", "flex-column") }) {
        Div { H3 { Text("Activity") } }
        Div({ classes("d-flex", "flex-row", "gap-1") }) {
            sessionsResponse.playbackStates.forEach { playbackState ->
                val user = sessionsResponse.users.getValue(playbackState.userId)
                val mediaLookup = sessionsResponse.mediaLookups.getValue(playbackState.mediaId)
                val mediaItem = checkNotNull(
                    mediaLookup.run { movie?.toMediaItem() ?: episode?.toMediaItem() }
                )
                Div({
                    classes("d-flex", "flex-column", "p-3", "rounded")
                    style {
                        backgroundColor(rgba(0, 0, 0, 0.2))
                        width(300.px)
                    }
                }) {
                    Div({
                        classes("d-flex", "flex-row", "align-items-center", "justify-content-between")
                    }) {
                        Div { Text(mediaItem.contentTitle) }
                        Div {
                            I({
                                classes("bi", "bi-filetype-json")
                                onClick {
                                    console.log("playbackState", playbackState)
                                    console.log("transcoding", sessionsResponse.transcodeSessions[playbackState.id])
                                }
                            })
                        }
                    }
                    Div({
                        classes("overflow-hidden", "text-nowrap")
                        style {
                            property("text-overflow", "ellipsis")
                        }
                    }) {
                        mediaItem.subtitle1?.let { subtitle1 ->
                            Text(subtitle1.replace("Season ", "S"))
                        }
                        mediaItem.subtitle2?.let { subtitle2 ->
                            Text(subtitle2.replace("Episode ", "E"))
                        }
                    }
                    Div { Text("User: ${user.displayName}") }
                    Div {
                        val progress = playbackState.position.seconds
                        val runtime = playbackState.runtime.seconds
                        Text(formatProgressAndRuntime(progress, runtime))
                    }
                }
            }
        }
    }
}
