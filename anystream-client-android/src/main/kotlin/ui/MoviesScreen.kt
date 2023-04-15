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
package anystream.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import anystream.android.AppTopBar
import anystream.android.router.BackStack
import anystream.client.AnyStreamClient
import anystream.models.MediaLink
import anystream.models.Movie
import anystream.models.api.MoviesResponse
import anystream.routing.Routes

@Composable
fun MoviesScreen(
    client: AnyStreamClient,
    onMediaClick: (mediaLinkId: String?) -> Unit,
    backStack: BackStack<Routes>,
) {
    Scaffold(
        topBar = { AppTopBar(client = client, backStack = backStack) },
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
            )
        }
    }
}

@Composable
private fun LoadingScreen(paddingValues: PaddingValues) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MovieGrid(
    movies: List<Movie>,
    mediaLinks: List<MediaLink>,
    onMediaClick: (mediaLinkId: String?) -> Unit,
    paddingValues: PaddingValues,
) {
    val cardWidth = (LocalConfiguration.current.screenWidthDp / 3).coerceAtMost(130).dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth),
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        contentPadding = PaddingValues(all = 8.dp),
    ) {
        items(movies) { movie ->
            val mediaLink by produceState<MediaLink?>(null, movie) {
                value = mediaLinks.find { it.metadataGid == movie.gid }
            }
            PosterCard(
                title = movie.title,
                imagePath = movie.posterPath,
                onClick = { onMediaClick(mediaLink?.gid) },
                preferredWidth = cardWidth,
                modifier = Modifier
                    .padding(all = 8.dp),
            )
        }
    }
}
