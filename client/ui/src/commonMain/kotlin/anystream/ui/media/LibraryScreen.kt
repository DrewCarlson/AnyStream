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
package anystream.ui.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import anystream.client.AnyStreamClient
import anystream.models.*
import anystream.ui.components.LoadingScreen

@Composable
fun LibraryScreen(
    client: AnyStreamClient,
    library: Library,
    onMediaClick: (metadataId: String) -> Unit,
    onPlayMediaClick: (mediaLinkId: String) -> Unit,
    modifier: Modifier
) {
    val response by produceState<List<MediaItem>?>(null, library) {
        value = when (library.mediaKind) {
            MediaKind.MOVIE -> client.library.getMovies(library.id).toMediaItems()
            MediaKind.TV -> client.library.getTvShows(library.id).toMediaItems()
            else -> null
        }
    }

    AnimatedContent(targetState = response) { targetState ->
        when (targetState) {
            null -> LoadingScreen()
            else -> {
                MediaItemGrid(
                    modifier = modifier,
                    mediaItems = targetState,
                    onMediaClick = onMediaClick,
                    onPlayMediaClick = onPlayMediaClick,
                )
            }
        }
    }
}
