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
package anystream.frontend.screens

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.frontend.models.toMediaItem
import anystream.models.MediaKind
import anystream.models.api.ImportMedia
import anystream.models.api.PlaybackSessionsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.dom.*
import kotlin.time.Duration

@Composable
fun SettingsScreen(
    client: AnyStreamClient,
) {
    val scope = rememberCoroutineScope()
    Div({
        classes("container-fluid", "p-4")
    }) {
        Div({
        }) {
            H3 {
                Text("Settings")
            }
        }
        Div({
            classes("row")
        }) {
            ImportMediaArea(client, scope)
        }
        Div({
        }) {
            H3 {
                Text("Active Streams")
            }
        }
        Div({
            classes("row")
        }) {
            ActiveStreamsList(client)
        }
    }
}

@Composable
private fun ImportMediaArea(
    client: AnyStreamClient,
    scope: CoroutineScope,
) {
    Div({
        classes("col-4")
    }) {
        val importAll = remember { mutableStateOf(false) }
        val selectedPath = remember { mutableStateOf<String?>(null) }
        val selectedMediaKind = remember { mutableStateOf(MediaKind.MOVIE) }

        Div { H4 { Text("Import Media") } }
        Select({
            onChange { event ->
                selectedMediaKind.value = event.value?.run(MediaKind::valueOf)
                    ?: selectedMediaKind.value
            }
        }) {
            MediaKind.values().forEach { mediaKind ->
                Option(mediaKind.name) {
                    Text(mediaKind.name.lowercase())
                }
            }
        }
        Input(InputType.Text) {
            placeholder("(content path)")
            onChange { event ->
                selectedPath.value = event.value
            }
        }
        Div({
            classes("form-check")
        }) {
            CheckboxInput(importAll.value) {
                id("import-all-check")
                classes("form-check-input")
                onChange { event ->
                    importAll.value = event.value
                }
            }
            Label("import-all-check", {
                classes("form-check-label")
            }) {
                Text("Import All")
            }
        }
        Button({
            onClick {
                scope.launch {
                    val contentPath = selectedPath.value ?: return@launch
                    client.importMedia(
                        importMedia = ImportMedia(
                            mediaKind = selectedMediaKind.value,
                            contentPath = contentPath
                        ),
                        importAll = importAll.value
                    )
                }
            }
        }) {
            Text("Import")
        }
    }
}

@Composable
private fun ActiveStreamsList(
    client: AnyStreamClient,
) {
    val sessionsResponse by produceState<PlaybackSessionsResponse?>(null) {
        while (true) {
            value = try {
                client.getStreams()
            } catch (e: Throwable) {
                null
            }
            delay(5_000L)
        }
    }

    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
        }
    }) {
        sessionsResponse?.apply {
            playbackStates.forEach { playbackState ->
                val user = users.getValue(playbackState.userId)
                val mediaLookup = mediaLookups.getValue(playbackState.mediaId)
                //val transcodeSession = transcodeSessions.getValue(playbackState.id)
                val mediaItem = checkNotNull(
                    mediaLookup.run { movie?.toMediaItem() ?: episode?.toMediaItem() }
                )
                Div({
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                    }
                }) {
                    Div { Text(mediaItem.contentTitle) }
                    Div { Text("User: ${user.displayName}") }
                    Div {
                        val progress = Duration.Companion.seconds(playbackState.position)
                        val runtime = Duration.Companion.seconds(playbackState.runtime)
                        Text(formatProgressAndRuntime(progress, runtime))
                    }
                }
            }
        }
    }
}