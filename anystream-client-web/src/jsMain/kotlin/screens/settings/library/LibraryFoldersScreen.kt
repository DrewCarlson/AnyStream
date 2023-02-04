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
import anystream.LocalAnyStreamClient
import anystream.util.throttleLatest
import anystream.components.Modal
import anystream.models.LocalMediaLink
import anystream.models.api.LibraryFolderList
import anystream.models.backend.MediaScannerState
import anystream.util.Bootstrap
import anystream.util.throttleLatest
import anystream.util.tooltip
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.Scope
import org.jetbrains.compose.web.attributes.scope
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.dom.*
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryFoldersScreen() {
    val scope = rememberCoroutineScope()
    val addFolderModalId = remember { "addFolderModal" }
    var folderListUpdate by remember { mutableStateOf(0) }
    val client = LocalAnyStreamClient.current
    var modal by remember { mutableStateOf<Bootstrap.ModalInstance?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalMediaLink?>(null) }
    val folderList by produceState<LibraryFolderList?>(null, folderListUpdate) {
        value = client.getLibraryFolderList()
        client.libraryActivity
            .map { it.scannerState }
            .filterIsInstance<MediaScannerState.Active>()
            .debounce(1.seconds)
            .collect { value = client.getLibraryFolderList() }
    }
    Div({ classes("vstack", "h-100", "w-100", "gap-1", "p-2") }) {
        Div { H3 { Text("Library Folders") } }
        Div({ classes("table-responsive") }) {
            Table({ classes("table", "table-hover") }) {
                Thead { FolderHeaderRow() }
                Tbody {
                    folderList?.run {
                        folders.forEach { folder ->
                            FolderRow(
                                folder,
                                onDeleteClicked = { deleteTarget = it.mediaLink },
                                onScanClicked = {},
                                onEditClicked = {},
                            )
                        }
                    }
                }
            }
        }
        Div({ classes("d-flex", "gap-1") }) {
            Button({
                classes("btn", "btn-primary")
                onClick { modal?.show() }
            }) {
                I({ classes("bi", "bi-folder-plus", "pe-1") })
                Text("Add Folder")
            }
        }
    }

    Modal(
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
        AddLibraryScreen(
            onFolderAdded = { },
            onLoadingStatChanged = {
                modalRef._config.backdrop = if (it) "static" else "true"
                modalRef._config.keyboard = !it
            },
            closeScreen = { modalRef.hide() },
        )
    }

    Modal(
        "deleteFolder",
        title = "Delete Folder",
        contentAttrs = { classes("bg-dark") },
    ) { modalRef ->
        DisposableEffect(deleteTarget) {
            if (deleteTarget == null) modalRef.hide() else modalRef.show()
            onDispose { }
        }
        DeleteFolderDialog(
            deleteTarget,
            onDeleteClicked = {
                scope.launch {
                    client.deleteLibraryFolder(checkNotNull(deleteTarget).gid)
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
    }
}

@Composable
private fun DeleteFolderDialog(
    mediaLink: LocalMediaLink?,
    onDeleteClicked: () -> Unit,
    onCancelClicked: () -> Unit,
) {
    Div({ classes("vstack", "gap-1") }) {
        Div { Text("Are you sure you want to delete this folder?") }
        Div { Text(mediaLink?.filePath.orEmpty()) }
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
) {
    Tr {
        Td {
            Div({ classes("hstack", "gap-3") }) {
                FolderAction("Delete Library", "trash") { onDeleteClicked(folder) }
                FolderAction("Scan Files", "arrow-clockwise") { onScanClicked(folder) }
                FolderAction("Edit Library", "gear-wide") { onEditClicked(folder) }
            }
        }
        Th({ scope(Scope.Row) }) { Text(folder.mediaLink.filePath) }
        Td { Text(folder.mediaLink.mediaKind.name) }
        Td { Text(folder.sizeOnDisk.orEmpty()) }
        Td { Text(folder.mediaMatchCount.toString()) }
        Td { Text(folder.run { unmatchedFileCount + unmatchedFolderCount }.toString()) }
    }
}

@Composable
private fun FolderAction(
    title: String,
    icon: String,
    onClick: () -> Unit,
) {
    A(null, {
        style { cursor("pointer") }
        onClick { onClick() }
    }) {
        I({
            tooltip(title)
            classes("bi", "bi-$icon")
        })
    }
}
