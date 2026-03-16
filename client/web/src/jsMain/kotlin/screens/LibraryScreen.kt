/*
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.screens

import androidx.compose.runtime.*
import anystream.components.*
import anystream.playerMediaLinkId
import anystream.presentation.library.LibraryScreenModel
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun LibraryScreen(screenModel: LibraryScreenModel) {
    when (screenModel) {
        is LibraryScreenModel.Loaded -> {
            if (screenModel.mediaItems.isEmpty()) {
                Div({ classes("d-flex", "justify-content-center", "align-items-center", "h-100") }) {
                    Text("${screenModel.library.mediaKind.libraryName} will appear here.")
                }
            } else {
                val router = Router.current
                VerticalGridScroller(screenModel.mediaItems) { mediaItem ->
                    PosterCard(
                        title = {
                            LinkedText("/media/${mediaItem.mediaId}") {
                                Text(mediaItem.contentTitle)
                            }
                        },
                        metadataId = mediaItem.mediaId,
                        isAdded = true,
                        onPlayClicked = {
                            playerMediaLinkId.value = mediaItem.playableMediaLink?.id
                        }.takeIf { mediaItem.playableMediaLink != null },
                        onBodyClicked = {
                            router.navigate("/media/${mediaItem.mediaId}")
                        },
                    )
                }
            }
        }

        LibraryScreenModel.Loading -> {
            FullSizeCenteredLoader()
        }

        LibraryScreenModel.LoadingFailed -> {
            Text("Failed to load library")
        }

        LibraryScreenModel.NotFound -> {
            Text("Library not found")
        }
    }
}
