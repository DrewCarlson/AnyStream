/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import anystream.client.AnyStreamClient
import anystream.models.api.TvShowsResponse
import anystream.models.toMediaItems
import anystream.ui.components.LoadingScreen


@Composable
fun TvShowsScreen(
    client: AnyStreamClient,
    onMediaClick: (metadataId: String) -> Unit,
    onPlayMediaClick: (mediaLinkId: String) -> Unit,
    modifier: Modifier
) {
    val response by produceState<TvShowsResponse?>(null) {
        value = client.library.getTvShows()
    }

    AnimatedContent(targetState = response) { targetState ->
        when (targetState) {
            null -> LoadingScreen()
            else -> {
                val mediaItems = remember { targetState.toMediaItems() }
                MediaItemGrid(
                    modifier = modifier,
                    mediaItems = mediaItems,
                    onMediaClick = onMediaClick,
                    onPlayMediaClick = onPlayMediaClick,
                )
            }
        }
    }
}
