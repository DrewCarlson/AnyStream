package anystream.screens.settings.library

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.components.Modal
import anystream.components.ModalSize
import anystream.models.Directory
import anystream.models.Library
import anystream.models.api.LibraryFolderList
import anystream.util.get
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

    Modal(
        id = "edit-library-modal",
        title = "Edit \"${library.name}\"",
        size = ModalSize.Large,
        onHidden = onClosed
    ) {
        when (screen) {
            LibraryModalScreen.DIRECTORY_LIST -> {
                LibraryDirectories(
                    directories = folderList,
                    onAddDirectoryClicked = {
                        screen = LibraryModalScreen.ADD_FOLDER
                    }
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
                    Th({ scope(Scope.Col) }) {
                        Text("Folder")
                    }
                }
            }
            Tbody {
                directories.forEach { directory ->
                    Tr {
                        /*Td {
                            Div({ classes("hstack", "gap-3") }) {
                                FolderAction("Delete Library", "trash") { onDeleteClicked(folder) }
                                FolderAction("Scan Files", "arrow-clockwise") { onScanClicked(folder) }
                                FolderAction("Edit Library", "gear-wide") { onEditClicked(folder) }
                                FolderAction("Analyze Files", "file-earmark-play") { onAnalyzeClicked(folder) }
                            }
                        }*/
                        Th({ scope(Scope.Row) }) { Text(directory.filePath) }
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