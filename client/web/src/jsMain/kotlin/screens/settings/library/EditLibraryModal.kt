/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
import anystream.components.Modal
import anystream.components.ModalSize
import anystream.models.Directory
import anystream.models.Library
import anystream.models.api.LibraryFolderList
import anystream.util.get
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.Scope
import org.jetbrains.compose.web.attributes.scope
import org.jetbrains.compose.web.dom.*

private enum class LibraryModalScreen {
    DIRECTORY_LIST,
    ADD_FOLDER
}

@Composable
fun EditLibraryModal(
    library: Library,
    onClosed: () -> Unit,
) {
    val client = get<AnyStreamClient>()
    var folderListUpdate by remember { mutableStateOf(0) }
    val folderList by produceState<List<Directory>>(emptyList(), folderListUpdate) {
        value = client.getDirectories(library.id)
    }
    var screen by remember { mutableStateOf(LibraryModalScreen.DIRECTORY_LIST) }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }

    Modal(
        id = "edit-library-modal",
        title = "Edit ${library.name}",
        size = ModalSize.Large,
        onHidden = onClosed,
        onHide = {}.takeIf { isLoading }
    ) {
        when (screen) {
            LibraryModalScreen.DIRECTORY_LIST -> {
                LibraryDirectories(
                    directories = folderList,
                    onAddDirectoryClicked = {
                        screen = LibraryModalScreen.ADD_FOLDER
                    },
                    onRemoveClicked = { directory ->
                        scope.launch {
                            isLoading = true
                            client.removeDirectory(directory.id)
                            isLoading = false
                        }
                    },
                    onScanClicked = { directory ->
                        scope.launch {
                            isLoading = true
                            client.scanDirectory(directory.id, refreshMetadata = true)
                            isLoading = false
                        }
                    },
                    onAnalyzeClicked = {}
                )
            }

            LibraryModalScreen.ADD_FOLDER -> {
                AddLibraryFolderScreen(
                    library = library,
                    onFolderAdded = { folderListUpdate += 1 },
                    onLoadingStatChanged = {},
                    closeScreen = {
                        screen = LibraryModalScreen.DIRECTORY_LIST
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryDirectories(
    directories: List<Directory>,
    onAddDirectoryClicked: () -> Unit,
    onRemoveClicked: (Directory) -> Unit,
    onScanClicked: (Directory) -> Unit,
    onAnalyzeClicked: (Directory) -> Unit,
) {
    Div {
        Button({
            classes("btn", "btn-primary")
            onClick { onAddDirectoryClicked() }
        }) {
            I({ classes("bi", "bi-folder-plus", "pe-1") })
            Text("Add Folder")
        }
    }
    Div({ classes("table-responsive") }) {
        Table({ classes("table", "table-hover") }) {
            Thead {
                Tr {
                    Th({ scope(Scope.Col) }) { /* actions */ }
                    Th({ scope(Scope.Col) }) { Text("Folder") }
                }
            }
            Tbody {
                directories.forEach { directory ->
                    Tr {
                        Td {
                            Div({ classes("hstack", "gap-3") }) {
                                FolderAction("Remove", "trash") { onRemoveClicked(directory) }
                                FolderAction("Scan", "arrow-clockwise") { onScanClicked(directory) }
                                //FolderAction("Edit", "gear-wide") { onEditClicked(folder) }
                                FolderAction("Analyze", "file-earmark-play") { onAnalyzeClicked(directory) }
                            }
                        }
                        Td({ scope(Scope.Row) }) { Text(directory.filePath) }
                    }
                }
            }
        }
    }
}

/*Modal(
    addFolderModalId,
    title = "Add Folder",
    size = "lg",
    contentAttrs = {
        classes("bg-dark")
        style { height(70.vh) }
    },
    bodyAttrs = { classes("overflow-hidden") },
) { modalRef ->
    DisposableEffect(modalRef) {
        modal = modalRef
        onDispose { modal = null }
    }
    AddLibraryFolderScreen(
        onFolderAdded = { },
        onLoadingStatChanged = {
            modalRef._config.backdrop = if (it) "static" else "true"
            modalRef._config.keyboard = !it
        },
        closeScreen = { modalRef.hide() },
    )
}*/

/*Modal(
    "deleteFolder",
    title = "Delete Folder",
    contentAttrs = { classes("bg-dark") },
) { modalRef ->
    DisposableEffect(deleteTarget) {
        if (deleteTarget == null) modalRef.hide() else modalRef.show()
        onDispose { }
    }
    DeleteFolderDialog(
        onDeleteClicked = {
            scope.launch {
                client.removeMediaLink(checkNotNull(deleteTarget))
                folderListUpdate++
                deleteTarget = null
                modalRef.hide()
            }
        },
        onCancelClicked = {
            modalRef.hide()
            deleteTarget = null
        },
    )
}*/

@Composable
private fun DeleteFolderDialog(
    onDeleteClicked: () -> Unit,
    onCancelClicked: () -> Unit,
) {
    Div({ classes("vstack", "gap-1") }) {
        Div { Text("Are you sure you want to delete this folder?") }
        Div({ classes("hstack", "justify-content-end", "py-1", "gap-2") }) {
            Button({
                classes("btn", "btn-primary")
                onClick { onDeleteClicked() }
            }) {
                Text("Delete")
            }
            Button({
                classes("btn", "btn-secondary")
                onClick { onCancelClicked() }
            }) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun FolderHeaderRow() {
    Tr {
        Th { }
        Th({ scope(Scope.Col) }) { Text("Path") }
        Th({ scope(Scope.Col) }) { Text("Type") }
        Th({ scope(Scope.Col) }) { Text("Size") }
        Th({ scope(Scope.Col) }) { Text("Free Space") }
        Th({ scope(Scope.Col) }) { Text("Matched") }
        Th({ scope(Scope.Col) }) { Text("Unmatched") }
    }
}

@Composable
private fun FolderRow(
    folder: LibraryFolderList.RootFolder,
    onDeleteClicked: (LibraryFolderList.RootFolder) -> Unit,
    onScanClicked: (LibraryFolderList.RootFolder) -> Unit,
    onEditClicked: (LibraryFolderList.RootFolder) -> Unit,
    onAnalyzeClicked: (LibraryFolderList.RootFolder) -> Unit,
) {
    Tr {
        Td {
            Div({ classes("hstack", "gap-3") }) {
                FolderAction("Delete Library", "trash") { onDeleteClicked(folder) }
                FolderAction("Scan Files", "arrow-clockwise") { onScanClicked(folder) }
                FolderAction("Edit Library", "gear-wide") { onEditClicked(folder) }
                FolderAction("Analyze Files", "file-earmark-play") { onAnalyzeClicked(folder) }
            }
        }
        Th({ scope(Scope.Row) }) { Text(folder.path) }
        Td { Text(folder.mediaKind.name) }
        Td { Text(folder.sizeOnDisk.orEmpty()) }
        Td { Text(folder.freeSpace.orEmpty()) }
        Td { Text(folder.mediaMatchCount.toString()) }
        Td { Text(folder.unmatchedCount.toString()) }
    }
}