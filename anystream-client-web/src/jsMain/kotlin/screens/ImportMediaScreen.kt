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
package anystream.frontend.screens

import androidx.compose.runtime.*
import anystream.frontend.LocalAnyStreamClient
import anystream.frontend.components.LoadingIndicator
import anystream.frontend.util.tooltip
import anystream.models.MediaKind
import anystream.models.api.FoldersResponse
import anystream.models.api.ImportMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement

@Composable
fun ImportMediaScreen(scope: CoroutineScope) {
    val client = LocalAnyStreamClient.current
    val importAll = remember { mutableStateOf(false) }
    val selectedPath = remember { mutableStateOf<String?>(null) }
    val selectedMediaKind = remember { mutableStateOf(MediaKind.MOVIE) }
    var showFiles by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val subfolders by produceState<FoldersResponse?>(null, selectedPath.value, showFiles) {
        isLoading = true
        value = FoldersResponse()
        value = try {
            client.folders(selectedPath.value, showFiles)
        } catch (e: Throwable) {
            null
        }
        isLoading = false
    }
    Div {
        Div { H4 { Text("Import Media") } }
        Select({
            tooltip("The type of media to be imported.", "top")
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
        var inputRef by remember { mutableStateOf<HTMLInputElement?>(null) }
        Input(InputType.Text) {
            placeholder("(content path)")
            onInput { event ->
                selectedPath.value = event.value
            }
            ref {
                inputRef = it
                onDispose { inputRef = null }
            }
        }
        Div({
            classes("form-check")
            tooltip("If enabled, assume each file/folder are different pieces of media.", "top")
        }) {
            CheckboxInput(importAll.value) {
                id("import-all-check")
                classes("form-check-input")
                onInput { event ->
                    importAll.value = event.value
                }
            }
            Label("import-all-check", {
                classes("form-check-label")
            }) {
                Text("Import All")
            }
        }
        Div({
            classes("form-check")
            tooltip("If enabled, display directories and individual files.", "top")
        }) {
            CheckboxInput(showFiles) {
                id("show-files-check")
                classes("form-check-input")
                onInput { event ->
                    showFiles = event.value
                }
            }
            Label("show-files-check", {
                classes("form-check-label")
            }) {
                Text("Show Files")
            }
        }
        Button({
            classes("btn", "btn-primary")
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

        Div({
            classes("w-100", "vstack", "gap-1", "overflow-scroll")
            style {
                height(200.px)
            }
        }) {
            if (isLoading) {
                Div { LoadingIndicator() }
            } else if (subfolders == null) {
                Div { Text("Invalid directory") }
            } else {
                Div {
                    FolderListItem("Up", "bi-folder2-open") {
                        selectedPath.value = selectedPath.value?.let { path ->
                            if (path.endsWith(":\\")) return@let null
                            val delimiter = if (path.contains("/")) "/" else "\\"
                            val newPath = path.split(delimiter).dropLast(1).joinToString(delimiter)
                            if (newPath.endsWith(":")) newPath + "\\" else newPath
                        }
                        inputRef?.value = selectedPath.value.orEmpty()
                    }
                }
                subfolders?.folders.orEmpty().forEach { subfolder ->
                    FolderListItem(subfolder, "bi-folder") {
                        inputRef?.value = subfolder
                        selectedPath.value = subfolder
                    }
                }
                subfolders?.files.orEmpty().forEach { subfolder ->
                    FolderListItem(subfolder, "bi-file-earmark") {
                        inputRef?.value = subfolder
                        selectedPath.value = subfolder
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderListItem(
    text: String,
    icon: String,
    onClick: () -> Unit,
) {
    Div {
        A(attrs = {
            style { cursor("pointer") }
            onClick { onClick() }
        }) {
            I({ classes("bi", icon, "me-1") })
            Text(text)
        }
    }
}
