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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import anystream.client.AnyStreamClient
import anystream.models.MediaLink
import anystream.models.Movie
import anystream.models.api.MoviesResponse
import anystream.router.BackStack
import anystream.routing.Routes
import anystream.ui.components.AppTopBar
import anystream.ui.components.LoadingScreen
import anystream.ui.components.PosterCard
import anystream.ui.util.cardWidth

@Composable
fun MoviesScreen(
    client: AnyStreamClient,
    onMediaClick: (mediaLinkId: String?) -> Unit,
    onPlayMediaClick: (mediaLinkId: String?) -> Unit,
    backStack: BackStack<Routes>,
) {
    Scaffold(
        topBar = { AppTopBar(client = client, backStack = backStack, showBackButton = true) },
    ) { padding ->
        val response = produceState<MoviesResponse?>(null) {
            value = client.getMovies()
        }
        if (response.value == null) {
            LoadingScreen(padding)
        } else {
            MovieGrid(
                movies = response.value!!.movies,
                mediaLinks = response.value!!.mediaLinks,
                onMediaClick = onMediaClick,
                paddingValues = padding,
                onPlayMediaClick = onPlayMediaClick,
            )
        }
    }
}

@Composable
private fun MovieGrid(
    movies: List<Movie>,
    mediaLinks: List<MediaLink>,
    onMediaClick: (mediaLinkId: String?) -> Unit,
    onPlayMediaClick: (mediaLinkId: String?) -> Unit,
    paddingValues: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth),
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        contentPadding = PaddingValues(all = 8.dp),
    ) {
        items(movies) { movie ->
            val mediaLink by produceState<MediaLink?>(null, movie) {
                value = mediaLinks.find {
                    it.metadataGid == movie.gid &&
                            it.descriptor == MediaLink.Descriptor.VIDEO
                }
            }
            PosterCard(
                title = movie.title,
                imagePath = movie.posterPath,
                onClick = { onMediaClick(mediaLink?.metadataGid) },
                preferredWidth = cardWidth,
                modifier = Modifier.padding(all = 8.dp),
                onPlayClick = { onPlayMediaClick(mediaLink?.gid) },
            )
        }
    }
}
