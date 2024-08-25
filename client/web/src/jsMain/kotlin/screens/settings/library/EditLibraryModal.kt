package anystream.screens.settings.library

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.components.Modal
import anystream.components.ModalSize
import anystream.models.Library
import anystream.models.api.LibraryFolderList
import anystream.models.backend.MediaScannerState
import anystream.util.get
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.web.attributes.Scope
import org.jetbrains.compose.web.attributes.scope
import org.jetbrains.compose.web.dom.*
import kotlin.time.Duration.Companion.seconds

@Composable
fun EditLibraryModal(
    library: Library,
    onClosed: () -> Unit,
) {
    val client = get<AnyStreamClient>()
    var folderListUpdate by remember { mutableStateOf(0) }
    val folderList by produceState<LibraryFolderList?>(null, folderListUpdate) {
        value = client.getLibraryFolderList()
        client.libraryActivity
            .map { it.scannerState }
            .filterIsInstance<MediaScannerState.Active>()
            .debounce(1.seconds)
            .collect { value = client.getLibraryFolderList() }
    }

    Modal(
        id = "edit-library-modal",
        title = "Edit \"${library.name}\"",
        size = ModalSize.Large,
        onHidden = onClosed
    ) {
        Div({ classes("table-responsive") }) {
            Table({ classes("table", "table-hover") }) {
                Thead { FolderHeaderRow() }
                Tbody {
                    folderList?.run {
                        folders.forEach { folder ->
                            FolderRow(
                                folder,
                                onDeleteClicked = { /*deleteTarget = it.libraryGid*/ },
                                onScanClicked = { /*scope.launch { client.scanMediaLink(it.libraryGid) }*/ },
                                onEditClicked = { /*router.navigate("/settings/libraries/${it.libraryGid}")*/ },
                                onAnalyzeClicked = { /*scope.launch { client.analyzeMediaLinksAsync(it.libraryGid) }*/ },
                            )
                        }
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