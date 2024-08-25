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
package anystream.screens.settings.library

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.components.LoadingIndicator
import anystream.models.Library
import anystream.models.MediaKind
import anystream.models.api.AddLibraryFolderResponse
import anystream.models.api.ListFilesResponse
import anystream.util.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement

@Composable
fun AddLibraryFolderScreen(
    library: Library,
    onFolderAdded: () -> Unit,
    onLoadingStatChanged: (isLoading: Boolean) -> Unit,
    closeScreen: () -> Unit,
) {
    val client = get<AnyStreamClient>()
    val scope = rememberCoroutineScope()
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var selectedMediaKind by remember { mutableStateOf<MediaKind?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val subfolders by produceState<ListFilesResponse?>(null, selectedPath) {
        isLoading = true
        value = ListFilesResponse()
        value = try {
            client.listFiles(selectedPath)
        } catch (e: Throwable) {
            null
        }
        isLoading = false
    }
    var isInputLocked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var inputRef by remember { mutableStateOf<HTMLInputElement?>(null) }
    Div({ classes("vstack", "h-100", "gap-2") }) {
        /*Div { Text("Select the kind of media to search for:") }
        Div({ classes("hstack", "gap-4") }) {
            MediaKindButton(
                name = "Movies",
                isSelected = selectedMediaKind == MediaKind.MOVIE,
                icon = "film",
                onClick = {
                    if (!isInputLocked) {
                        selectedMediaKind = MediaKind.MOVIE
                    }
                },
            )
            MediaKindButton(
                name = "TV Shows",
                isSelected = selectedMediaKind == MediaKind.TV,
                icon = "tv",
                onClick = {
                    if (!isInputLocked) {
                        selectedMediaKind = MediaKind.TV
                    }
                },
            )
            MediaKindButton(
                name = "Music",
                isSelected = selectedMediaKind == MediaKind.MUSIC,
                icon = "music-note-beamed",
                onClick = { /*selectedMediaKind.value = MediaKind.MUSIC*/ },
            )
        }*/
        Div { Text("Select a folder for the ${library.name} library:") }
        Div({ classes("input-group") }) {
            Span({ classes("input-group-text") }) {
                Text("Media Folder")
            }
            Input(InputType.Text) {
                classes("form-control")
                placeholder("(select a folder below or paste it here)")
                value(selectedPath.orEmpty())
                onInput { event ->
                    if (!isInputLocked) {
                        selectedPath = event.value
                    }
                }
                ref {
                    inputRef = it
                    onDispose { inputRef = null }
                }
            }
        }

        Div({
            classes("vstack", "gap-1", "justify-content-center", "align-items-center")
            style { overflow("hidden scroll") }
        }) {
            if (isLoading) {
                Div { LoadingIndicator() }
            } else if (subfolders == null) {
                Div { H4 { Text("Invalid directory") } }
            } else {
                Div({ classes("w-100", "h-100") }) {
                    Table({ classes("w-100") }) {
                        Tbody {
                            if (!selectedPath.isNullOrBlank()) {
                                FolderListItem("Up", "bi-folder2-open") {
                                    if (!isInputLocked) {
                                        val newPath = selectedPath?.dropLastPathSegment()
                                        selectedPath = newPath
                                        inputRef?.value = newPath.orEmpty()
                                    }
                                }
                            }
                            subfolders?.folders.orEmpty().forEach { subfolder ->
                                FolderListItem(subfolder, "bi-folder") {
                                    if (!isInputLocked) {
                                        inputRef?.value = subfolder
                                        selectedPath = subfolder
                                    }
                                }
                            }
                            subfolders?.files.orEmpty().forEach { subfolder ->
                                FolderListItem(subfolder, "bi-file-earmark") {
                                    if (!isInputLocked) {
                                        inputRef?.value = subfolder
                                        selectedPath = subfolder
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Div({ classes("hstack", "gap-2", "m2", "justify-content-end") }) {
            Div({ classes("flex-grow-1") }) {
                Text(message.orEmpty())
            }
            Button({
                classes("btn", "btn-primary")
                if (isInputLocked || selectedMediaKind == null || selectedPath.isNullOrBlank()) {
                    disabled()
                }
                onClick {
                    scope.launch {
                        message = "Loading"
                        isInputLocked = true
                        onLoadingStatChanged(true)
                        val loadingJob = launch {
                            while (isActive) {
                                message += "."
                                delay(800)
                                message = message?.substringBefore("....")
                            }
                        }
                        val response = client.addLibraryFolder(library.id, selectedPath.orEmpty())
                        onLoadingStatChanged(false)
                        loadingJob.cancel()
                        isInputLocked = false
                        when (response) {
                            is AddLibraryFolderResponse.Success -> {
                                message = null
                                selectedPath = null
                                selectedMediaKind = null
                                onFolderAdded()
                                closeScreen()
                            }
                            is AddLibraryFolderResponse.LibraryFolderExists -> {
                                message = "This folder already belongs to a library."
                            }
                            is AddLibraryFolderResponse.FileError -> {
                                message = if (!response.exists) {
                                    "The selected folder does not exist."
                                } else {
                                    "The selected file is not a directory."
                                }
                            }
                            is AddLibraryFolderResponse.RequestError -> {
                                message = "Unknown request error"
                            }
                            is AddLibraryFolderResponse.DatabaseError -> {
                                message = "Database error"
                            }
                        }
                    }
                }
            }) { Text("Add") }
            Button({
                classes("btn", "btn-secondary")
                if (isInputLocked) disabled()
                // modalDismiss()
                onClick { closeScreen() }
            }) { Text("Cancel") }
        }
    }
}

@Composable
private fun FolderListItem(
    text: String,
    icon: String,
    onClick: () -> Unit,
) {
    Tr {
        Td {
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
    }
}

@Composable
private fun MediaKindButton(
    name: String,
    isSelected: Boolean,
    icon: String,
    onClick: () -> Unit,
) {
    A(null, {
        onClick { onClick() }
        style {
            cursor("pointer")
            height(100.px)
            width(100.px)
        }
        classes("media-kind-link", "rounded")
        if (isSelected) {
            classes("active")
        }
    }) {
        Div({ classes("vstack", "m-2", "fs-6", "text-center") }) {
            I({ classes("bi", "bi-$icon", "fs-2") })
            Text(name)
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
