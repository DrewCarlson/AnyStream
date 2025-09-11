/**
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
import anystream.client.AnyStreamClient
import anystream.components.*
import anystream.models.MediaItem
import anystream.models.MediaKind
import anystream.models.toMediaItems
import anystream.playerMediaLinkId
import anystream.util.get
import app.softwork.routingcompose.Router
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun LibraryScreen(
    libraryId: String,
) {
    val router = Router.current
    val client = get<AnyStreamClient>()
    val library by remember(libraryId) {
        client.library.libraries
            .mapNotNull { libraries ->
                libraries.firstOrNull { it.id == libraryId }
            }
    }.collectAsState(client.library.libraries.value.firstOrNull { it.id == libraryId })
    val mediaItems by produceState<List<MediaItem>?>(null, libraryId) {
        value = null
        value = when (library?.mediaKind) {
            MediaKind.MOVIE -> client.library.getMovies(libraryId).toMediaItems()
            MediaKind.TV -> client.library.getTvShows(libraryId).toMediaItems()
            else -> {
                router.navigate("/")
                null
            }
        }
    }

    when (val items = mediaItems) {
        null -> FullSizeCenteredLoader()
        else -> {
            if (items.isEmpty()) {
                Div({ classes("flex", "justify-content-center", "align-items-center", "h-full") }) {
                    Text("${library?.mediaKind?.libraryName} will appear here.")
                }
            } else {
                val router = Router.current
                VerticalGridScroller(items) { mediaItem ->
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
    }
}

