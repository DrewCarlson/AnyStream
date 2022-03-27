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
import anystream.models.api.PlaybackSessionsResponse
import anystream.util.formatProgressAndRuntime
import kotlinx.coroutines.flow.retry
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.time.Duration.Companion.seconds

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    Div({
        classes("d-flex", "flex-column", "p-2")
        style {
            gap(1.cssRem)
        }
    }) {
        Div {
            H3 {
                Text("Settings")
            }
        }
        Div {
            ImportMediaScreen(scope)
        }
        Div {
            H3 {
                Text("Active Streams")
            }
        }
        Div {
            ActiveStreamsList()
        }
    }
}

@Composable
private fun ActiveStreamsList() {
    val client = LocalAnyStreamClient.current
    val sessionsResponse by remember { client.observeStreams().retry() }.collectAsState(PlaybackSessionsResponse())

    Div({
        classes("d-flex", "flex-row")
        style {
            gap(10.px)
        }
    }) {
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
