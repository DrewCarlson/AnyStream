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
package anystream.ui.movies

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import anystream.client.AnyStreamClient
import anystream.models.Descriptor
import anystream.models.MediaLink
import anystream.models.Movie
import anystream.models.api.MoviesResponse
import anystream.ui.components.LoadingScreen
import anystream.ui.components.PosterCard
import anystream.ui.components.PosterCardWidth

@Composable
fun MoviesScreen(
    client: AnyStreamClient,
    onMediaClick: (metadataId: String) -> Unit,
    onPlayMediaClick: (mediaLinkId: String) -> Unit,
    modifier: Modifier
) {
    val response by produceState<MoviesResponse?>(null) {
        value = client.getMovies()
    }

    AnimatedContent(targetState = response) { targetState ->
        when (targetState) {
            null -> LoadingScreen()
            else -> MovieGrid(
                modifier = modifier,
                movies = targetState.movies,
                mediaLinks = targetState.mediaLinks,
                onMediaClick = onMediaClick,
                onPlayMediaClick = onPlayMediaClick,
            )
        }
    }
}

@Composable
private fun MovieGrid(
    movies: List<Movie>,
    mediaLinks: Map<String, MediaLink>,
    onMediaClick: (metadataId: String) -> Unit,
    onPlayMediaClick: (mediaLinkId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PosterCardWidth),
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(movies) { movie ->
            val mediaLink by produceState<MediaLink?>(null, movie) {
                value = mediaLinks[movie.id]?.takeIf { it.descriptor == Descriptor.VIDEO }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                PosterCard(
                    title = movie.title,
                    mediaId = movie.id,
                    onClick = { onMediaClick(movie.id) },
                    modifier = Modifier,
                    onPlayClick = { mediaLink?.run { onPlayMediaClick(id) } },
                )
            }
        }
    }
}
