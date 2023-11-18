package anystream.screens.settings.library

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.components.*
import anystream.models.LocalMediaLink
import anystream.models.MediaLink
import anystream.util.get
import anystream.util.tooltip
import org.jetbrains.compose.web.attributes.Scope
import org.jetbrains.compose.web.attributes.scope
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.dom.*

@Composable
fun LibraryMediaLinkListScreen(libraryGid: String) {
    val client = get<AnyStreamClient>()
    val libraryLink by produceState<LocalMediaLink?>(null) {
        value = client.findMediaLink(libraryGid, includeMetadata = false).mediaLink as LocalMediaLink
    }
    val mediaLinks by produceState(emptyList<MediaLink>()) {
        value = client.getMediaLinks(libraryGid)
    }
    var matchMediaLinkGid by remember { mutableStateOf<String?>(null) }

    Div({ classes("vstack", "h-100", "w-100", "gap-1", "p-2") }) {
        Div {
            H3 {
                if (mediaLinks.isEmpty()) {
                    Text("Media Links")
                } else {
                    Text("Media Links (${mediaLinks.size})")
                }
            }
        }
        if (mediaLinks.isNotEmpty()) {
            MediaLinkHeaderRow()
            VerticalScroller(mediaLinks) { mediaLink ->
                MediaLinkRow(
                    mediaLink = mediaLink as LocalMediaLink,
                    onScanClicked = { },
                    onMatchMetadata = { matchMediaLinkGid = it.gid },
                )
            }
        }
        /*Div({ classes("d-flex", "gap-1") }) {
            Button({
                classes("btn", "btn-primary")
                onClick { }
            }) {
                I({ classes("bi", "bi-folder-plus", "pe-1") })
                Text("Add Folder")
            }
        }*/
    }
    Modal(
        "matchMetadata",
        title = "Match Metadata",
        size = "lg",
        contentAttrs = {
            classes("bg-dark")
            style { height(70.vh) }
        },
        bodyAttrs = { classes("overflow-hidden") },
        onHide = { matchMediaLinkGid = null }
    ) { modalRef ->
        LaunchedEffect(matchMediaLinkGid) {
            if (matchMediaLinkGid == null) {
                modalRef.hide()
            } else {
                modalRef.show()
            }
        }
        MetadataMatchScreen(
            mediaLinkGid = matchMediaLinkGid,
            onLoadingStatChanged = {
                modalRef._config.backdrop = if (it) "static" else "true"
                modalRef._config.keyboard = !it
            },
            closeScreen = { modalRef.hide() }
        )
    }
}

@Composable
private fun MediaLinkHeaderRow() {
    Tr {
        Th { }
        Th({ scope(Scope.Col) }) { Text("Matched") }
        Th({ scope(Scope.Col) }) { Text("Kind") }
        Th({ scope(Scope.Col) }) { Text("Path") }
    }
}


@Composable
private fun MediaLinkRow(
    mediaLink: LocalMediaLink,
    onScanClicked: (LocalMediaLink) -> Unit,
    onMatchMetadata: (LocalMediaLink) -> Unit,
) {
    Div({ classes("hstack", "m-2", "gap-2") }) {
        Div {
            Div({ classes("hstack", "gap-1") }) {
                MediaLinkAction("Scan Files", "arrow-clockwise") { onScanClicked(mediaLink) }
                MediaLinkAction("Match Metadata", "binoculars") { onMatchMetadata(mediaLink) }
            }
        }
        Div({ classes("text-center") }) {
            if (mediaLink.metadataId == null) {
                I({ classes("bi", "bi-exclamation-triangle") })
            } else {
                I({ classes("bi", "bi-check-circle-fill") })
            }
        }
        Div({ classes("text-center") }) {
            Text(mediaLink.mediaKind.name)
        }
        Div({
            classes("w-100")
        }) {
            if (mediaLink.metadataId == null) {
                Text(mediaLink.filename)
            } else {
                LinkedText("/media/${mediaLink.metadataGid}") {
                    Text(mediaLink.filename)
                }
            }
        }
    }
}

@Composable
private fun MediaLinkAction(
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
