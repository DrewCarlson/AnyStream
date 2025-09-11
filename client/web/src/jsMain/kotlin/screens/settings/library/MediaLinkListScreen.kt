/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
import anystream.components.*
import anystream.models.LocalMediaLink
import anystream.models.MediaLink
import anystream.models.typed
import anystream.util.get
import anystream.util.tooltip
import org.jetbrains.compose.web.attributes.Scope
import org.jetbrains.compose.web.attributes.scope
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.dom.*

@Composable
fun MediaLinkListScreen(libraryId: String) {
    val client = get<AnyStreamClient>()
    var updateIndex by remember { mutableStateOf(0) }
    val mediaLinks by produceState(emptyList<MediaLink>(), updateIndex) {
        value = client.library.getMediaLinks(libraryId)
    }
    var matchMediaLinkId by remember { mutableStateOf<String?>(null) }
    val selectedMediaLinks = remember { mutableStateListOf<String>() }

    Div({ classes("flex", "flex-col", "size-full", "gap-1", "p-2") }) {
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
                    mediaLink = mediaLink.typed() as LocalMediaLink,
                    isSelected = selectedMediaLinks.contains(mediaLink.id),
                    onSelect = {
                        if (!selectedMediaLinks.remove(it.id)) {
                            selectedMediaLinks.add(it.id)
                        }
                    },
                    onScanClicked = { },
                    onMatchMetadata = { matchMediaLinkId = it.id },
                )
            }
        }
        /*Div({ classes("flex", "gap-1") }) {
            Button({
                classes("btn", "btn-primary")
                onClick { }
            }) {
                I({ classes("bi", "bi-folder-plus", "pe-1") })
                Text("Add Folder")
            }
        }*/
    }

    matchMediaLinkId?.let {
        Modal(
            "matchMetadata",
            title = "Match Metadata",
            size = ModalSize.Large,
            contentAttrs = {
                classes("bg-dark")
                style { height(70.vh) }
            },
            bodyAttrs = { classes("overflow-hidden") },
            onHidden = { matchMediaLinkId = null }
        ) { modalRef ->
            MetadataMatchScreen(
                mediaLinkId = matchMediaLinkId,
                onLoadingStatChanged = { loading ->
                    modalRef._config.backdrop = if (loading) "static" else "true"
                    modalRef._config.keyboard = !loading
                },
                closeScreen = {
                    matchMediaLinkId = null
                    updateIndex += 1
                    modalRef.hide()
                }
            )
        }
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
    isSelected: Boolean,
    onSelect: (LocalMediaLink) -> Unit,
    onScanClicked: (LocalMediaLink) -> Unit,
    onMatchMetadata: (LocalMediaLink) -> Unit,
) {
    Div({ classes("flex", "flex-row", "m-2", "gap-3") }) {
        Div({ classes("me-2") }) {
            CheckboxInput(checked = isSelected) {
                classes("form-check-input")
                onChange {
                    onSelect(mediaLink)
                }
            }
        }
        Div({ classes("flex", "flex-row", "gap-2") }) {
            MediaLinkAction("Scan Files", "arrow-clockwise") { onScanClicked(mediaLink) }
            MediaLinkAction("Match Metadata", "binoculars") { onMatchMetadata(mediaLink) }
        }
        Div { Text(mediaLink.mediaKind.name) }
        Div {
            if (mediaLink.metadataId == null) {
                I({ classes("bi", "bi-exclamation-triangle") })
            } else {
                I({ classes("bi", "bi-check-circle-fill") })
            }
        }
        Div({ classes("w-full") }) {
            //Text(mediaLink.filename)
            /*if (mediaLink.metadataId == null) {
                Text(mediaLink.filename)
            } else {
                LinkedText("/media/${mediaLink.metadataId}") {
                    Text(mediaLink.filename)
                }
            }*/
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
