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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import anystream.client.AnyStreamClient
import anystream.models.MediaKind
import anystream.models.api.ImportMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.*

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