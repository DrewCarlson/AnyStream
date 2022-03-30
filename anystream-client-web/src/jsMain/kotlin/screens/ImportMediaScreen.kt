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
import anystream.models.api.ImportMedia
import anystream.models.api.ListFilesResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement

@Composable
fun ImportMediaScreen(scope: CoroutineScope) {
    val client = LocalAnyStreamClient.current
    val importAll = remember { mutableStateOf(false) }
    val selectedPath = remember { mutableStateOf<String?>(null) }
    val selectedMediaKind = remember { mutableStateOf(MediaKind.MOVIE) }
    var showFiles by remember { mutableStateOf(false) }
    var interactiveImport by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val subfolders by produceState<ListFilesResponse?>(null, selectedPath.value, showFiles) {
        isLoading = true
        value = ListFilesResponse()
        value = try {
            client.listFiles(selectedPath.value, showFiles)
        } catch (e: Throwable) {
            null
        }
        isLoading = false
    }
    var inputRef by remember { mutableStateOf<HTMLInputElement?>(null) }
    Div({ classes("d-flex", "flex-column", "w-100", "gap-1") }) {
        Div { H3 { Text("Import Media") } }
        Div({ classes("d-flex", "gap-1") }) {
            Button({
                classes("btn", "btn-primary")
                attr("data-bs-toggle", "modal")
                attr("data-bs-target", "#importModal")
            }) {
                Text("modal")
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
            Input(InputType.Text) {
                classes("flex-grow-1")
                placeholder("(content path)")
                onInput { event ->
                    selectedPath.value = event.value
                }
                ref {
                    inputRef = it
                    onDispose { inputRef = null }
                }
            }
        }
        Div({ classes("d-flex", "gap-1") }) {
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
            Div({
                classes("form-check")
                tooltip("If enabled, you must confirm each file/folders media before importing it.", "top")
            }) {
                CheckboxInput(interactiveImport) {
                    disabled()
                    id("interactive-check")
                    classes("form-check-input")
                    onInput { event ->
                        interactiveImport = event.value
                    }
                }
                Label("interactive-check", {
                    classes("form-check-label")
                }) {
                    Text("Interactive")
                }
            }
        }

        Div({
            classes("w-100", "vstack", "gap-1", "overflow-scroll")
        }) {
            if (isLoading) {
                Div { LoadingIndicator() }
            } else if (subfolders == null) {
                Div { Text("Invalid directory") }
            } else {
                if (!selectedPath.value.isNullOrBlank()) {
                    Div {
                        FolderListItem("Up", "bi-folder2-open") {
                            selectedPath.value = selectedPath.value?.dropLastPathSegment()
                            inputRef?.value = selectedPath.value.orEmpty()
                        }
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

private fun String?.dropLastPathSegment(): String? {
    return this?.let { path ->
        if (path.endsWith(":\\")) return@let null
        val delimiter = if (path.contains("/")) "/" else "\\"
        val newPath = path.split(delimiter).dropLast(1).joinToString(delimiter)
        if (newPath.endsWith(":")) newPath + "\\" else newPath
    }
}
