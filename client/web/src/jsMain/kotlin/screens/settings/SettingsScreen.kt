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
import anystream.models.*
import anystream.models.api.EpisodeResponse
import anystream.models.api.MovieResponse
import anystream.models.api.MediaLookupResponse
import anystream.models.api.PlaybackSessions
import anystream.screens.settings.library.LibrariesScreen
import anystream.screens.settings.library.MediaLinkListScreen
import anystream.util.formatProgressAndRuntime
import anystream.util.koinGet
import app.softwork.routingcompose.RouteBuilder
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun RouteBuilder.SettingsScreen(subscreen: String) {
    Div({ classes("d-flex", "p-2", "h-100") }) {
        when (subscreen) {
            "activity" -> ActiveStreamsList()
            "users" -> UserManagerScreen()
            "libraries" -> {
                string { id -> MediaLinkListScreen(libraryId = id) }
                noMatch { LibrariesScreen() }
            }

            else -> Text("Not found")
        }
    }
}

@Composable
fun SettingsSideMenu() {
    Div(
        {
            classes("d-inline-block", "mx-2", "py-2")
            style {
                property("transition", "width .2s ease-in-out 0s")
                width(250.px)
                minWidth(250.px)
            }
        },
    ) {
        Ul(
            {
                classes(
                    "nav", "nav-pills", "bg-dark-translucent", "flex-column",
                    "h-100", "py-2", "mb-auto", "rounded", "shadow",
                )
                style {
                    overflow("hidden")
                }
            },
        ) {
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
                NavLink("Libraries", "bi-folder", "/settings/libraries", true) {
                    classes("fs-6")
                }
            }
        }
    }
}

@Composable
private fun ActiveStreamsList() {
    val client = koinGet<AnyStreamClient>()
    val sessionsResponse by client.admin.playbackSessions.collectAsState(PlaybackSessions())
    val transcodeSessions = sessionsResponse.transcodeSessions
    val mediaLookups = sessionsResponse.mediaLookups
    val playbackStates = sessionsResponse.playbackStates
    val users = sessionsResponse.users
    val scope = rememberCoroutineScope()

    Div({ classes("d-flex", "flex-column", "pt-2", "ps-2") }) {
        Div { H3 { Text("Activity") } }
        Div({ classes("d-flex", "flex-row", "gap-1") }) {
            playbackStates.forEach { playbackState ->
                PlaybackSessionCard(
                    playbackState = playbackState,
                    user = users.getValue(playbackState.userId),
                    mediaLookup = mediaLookups.getValue(playbackState.metadataId),
                    transcodeSession = transcodeSessions.getValue(playbackState.id),
                    onStopClicked = {
                        scope.launch {
                            client.stream.stopStreamSession(playbackState.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaybackSessionCard(
    user: UserPublic,
    transcodeSession: TranscodeSession,
    mediaLookup: MediaLookupResponse,
    playbackState: PlaybackState,
    onStopClicked: () -> Unit,
) {
    val mediaItem = remember {
        checkNotNull(
            (mediaLookup as? MovieResponse)?.toMediaItem()
                ?: (mediaLookup as? EpisodeResponse)?.toMediaItem(concise = true)
        )
    }
    Div(
        {
            classes("d-flex", "flex-column", "p-3", "rounded")
            style {
                backgroundColor(rgba(0, 0, 0, 0.2))
                width(300.px)
            }
        },
    ) {
        Div(
            {
                classes("d-flex", "flex-row", "align-items-center", "justify-content-between")
            },
        ) {
            Div { Text(mediaItem.contentTitle) }
            Div({
                classes("d-flex", "flex-row", "gap-2")
            }) {
                I(
                    {
                        classes("bi", "bi-filetype-json")
                        onClick {
                            console.log("playbackState", playbackState.toString())
                            console.log(
                                "transcoding",
                                transcodeSession.toString(),
                            )
                        }
                    },
                )
                I(
                    {
                        classes("bi", "bi-x-lg")
                        onClick { onStopClicked() }
                    },
                )
            }
        }
        Div(
            {
                classes("overflow-hidden", "text-nowrap")
                style {
                    property("text-overflow", "ellipsis")
                }
            },
        ) {
            mediaItem.subtitle1?.let { subtitle1 ->
                Text(subtitle1.replace("Season ", "S"))
            }
            mediaItem.subtitle2?.let { subtitle2 ->
                Text(subtitle2.replace("Episode ", "E"))
            }
        }
        Div { Text("User: ${user.displayName}") }
        Div {
            Text("Transcode (${transcodeSession.transcodeDecision.name.substringBefore('_')}): ")
            Text(transcodeSession.state.name)
        }
        Div {
            val progress = playbackState.position
            val runtime = playbackState.runtime
            Text(formatProgressAndRuntime(progress, runtime))
        }
    }
}
