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
package anystream.screens.settings

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.components.NavLink
import anystream.models.api.PlaybackSessions
import anystream.models.toMediaItem
import anystream.screens.settings.library.LibraryFoldersScreen
import anystream.util.formatProgressAndRuntime
import anystream.util.get
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.time.Duration.Companion.seconds

@Composable
fun SettingsScreen(subscreen: String) {
    Div({ classes("d-flex", "p-2", "h-100") }) {
        when (subscreen) {
            "activity" -> ActiveStreamsList()
            "users" -> UserManagerScreen()
            "library-folders" -> LibraryFoldersScreen()
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
            classes(
                "nav", "nav-pills", "bg-dark-translucent", "flex-column",
                "h-100", "py-2", "mb-auto", "rounded", "shadow",
            )
            style {
                overflow("hidden")
            }
        }) {
            Li({ classes("nav-item") }) {
                NavLink("Activity", "bi-activity", "/settings/activity", true) {
                    classes("fs-6")
                }
            }
            Li({ classes("nav-item") }) {
                NavLink("Users", "bi-people", "/settings/users", true) {
                    classes("fs-6")
                }
            }
            Li({ classes("nav-item") }) {
                NavLink("Library Folders", "bi-folder", "/settings/library-folders", true) {
                    classes("fs-6")
                }
            }
        }
    }
}

@Composable
private fun ActiveStreamsList() {
    val client = get<AnyStreamClient>()
    val sessionsResponse by client.playbackSessions.collectAsState(PlaybackSessions())

    Div({ classes("d-flex", "flex-column", "pt-2", "ps-2") }) {
        Div { H3 { Text("Activity") } }
        Div({ classes("d-flex", "flex-row", "gap-1") }) {
            sessionsResponse.playbackStates.forEach { playbackState ->
                val user = sessionsResponse.users.getValue(playbackState.userId)
                val mediaLookup = sessionsResponse.mediaLookups.getValue(playbackState.metadataGid)
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
