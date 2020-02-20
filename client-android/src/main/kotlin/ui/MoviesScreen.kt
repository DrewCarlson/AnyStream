/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import anystream.android.AppTopBar
import anystream.android.Routes
import anystream.android.router.BackStack
import anystream.client.AnyStreamClient
import anystream.models.MediaReference
import anystream.models.Movie
import anystream.models.api.MoviesResponse

@Composable
fun MoviesScreen(
    client: AnyStreamClient,
    onMediaClick: (mediaRefId: String?) -> Unit,
    backStack: BackStack<Routes>
) {
    Scaffold(
        topBar = { AppTopBar(client = client, backStack = backStack) }
    ) { padding ->
        val response = produceState<MoviesResponse?>(null) {
            value = client.getMovies()
        }
        if (response.value == null) {
            LoadingScreen(padding)
        } else {
            MovieGrid(
                movies = response.value!!.movies,
                mediaReferences = response.value!!.mediaReferences,
                onMediaClick = onMediaClick,
                paddingValues = padding
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
            .fillMaxSize()
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieGrid(
    movies: List<Movie>,
    mediaReferences: List<MediaReference>,
    onMediaClick: (mediaRefId: String?) -> Unit,
    paddingValues: PaddingValues,
) {
    val cardWidth = (LocalConfiguration.current.screenWidthDp / 3).coerceAtMost(130).dp
    LazyVerticalGrid(
        cells = GridCells.Adaptive(cardWidth),
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        contentPadding = PaddingValues(all = 8.dp)
    ) {
        items(movies) { movie ->
            val mediaRef = remember {
                mediaReferences.find { it.contentId == movie.id }
            }
            PosterCard(
                title = movie.title,
                imagePath = movie.posterPath,
                onClick = { onMediaClick(mediaRef?.id) },
                preferredWidth = cardWidth,
                modifier = Modifier
                    .padding(all = 8.dp)
            )
        }
    }
}