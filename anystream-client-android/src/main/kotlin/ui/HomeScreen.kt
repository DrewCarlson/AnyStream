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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.android.AppTopBar
import anystream.android.AppTypography
import anystream.android.router.BackStack
import anystream.client.AnyStreamClient
import anystream.models.*
import anystream.models.api.HomeResponse
import anystream.routing.Routes
import coil.compose.ImagePainter
import coil.compose.rememberImagePainter

private val CARD_SPACING = 12.dp

@Composable
private fun RowSpace() = Spacer(modifier = Modifier.size(8.dp))

@Composable
fun HomeScreen(
    client: AnyStreamClient,
    backStack: BackStack<Routes>,
    onMediaClick: (mediaRefId: String?) -> Unit,
    onViewMoviesClicked: () -> Unit
) {
    Scaffold(
        topBar = { AppTopBar(client = client, backStack = backStack) }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 8.dp)
        ) {
            item {
                val homeData = produceState<HomeResponse?>(null) {
                    value = client.getHomeData()
                }

                homeData.value?.run {
                    Spacer(modifier = Modifier.size(4.dp))
                    /*if (currentlyWatching.isNotEmpty()) {
                        RowTitle(text = "Continue Watching")
                        ContinueWatchingRow(currentlyWatching, onClick = onMediaClick)
                        RowSpace()
                    }*/

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RowTitle(text = "Recently Added Movies")
                        TextButton(onClick = onViewMoviesClicked) {
                            Text(text = "All Movies")
                        }
                    }
                    MovieRow(movies = recentlyAdded, onClick = onMediaClick)
                    RowSpace()

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RowTitle(text = "Recently Added TV")
                        TextButton(onClick = onViewMoviesClicked) {
                            Text(text = "All Shows")
                        }
                    }
                    TvRow(shows = recentlyAddedTv, onClick = onMediaClick)
                    RowSpace()

                    RowTitle(text = "Popular Movies")
                    MovieRow(movies = popularMovies, onClick = onMediaClick)
                    RowSpace()
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    currentlyWatching: Map<Movie, PlaybackState>,
    onClick: (mediaRefId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        modifier = modifier,
        content = {
            items(currentlyWatching.toList()) { (movie, playbackState) ->
                WatchingCard(movie, playbackState, onClick)
            }
        }
    )
}

@Composable
private fun WatchingCard(
    movie: Movie,
    playbackState: PlaybackState,
    onClick: (mediaRefId: String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier
            .width(256.dp)
            .clickable(onClick = { onClick(playbackState.mediaReferenceId) }),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val painter = rememberImagePainter(
                data = "https://image.tmdb.org/t/p/w300${movie.backdropPath}",
                builder = {
                    crossfade(true)
                }
            )
            Box(
                modifier = Modifier
                    .height(144.dp)
                    .fillMaxWidth()
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                when (painter.state) {
                    is ImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray)
                        )
                    }
                    is ImagePainter.State.Error -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray)
                        ) {
                            Text("No Backdrop")
                        }
                    }
                    ImagePainter.State.Empty,
                    is ImagePainter.State.Success -> Unit
                }
            }

            LinearProgressIndicator(
                progress = (playbackState.position / (movie.runtime * 60f)).toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .padding(all = 4.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = movie.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MovieRow(
    movies: Map<Movie, MediaReference?>,
    onClick: (mediaRefId: String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        content = {
            items(movies.toList()) { (movie, mediaRef) ->
                PosterCard(
                    title = movie.title,
                    imagePath = movie.posterPath,
                    onClick = { mediaRef?.run { onClick(id) } },
                )
            }
        }
    )
}

@Composable
private fun TvRow(
    shows: List<TvShow>,
    onClick: (mediaRefId: String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        content = {
            items(shows) { show ->
                PosterCard(
                    title = show.name,
                    imagePath = show.posterPath,
                    onClick = { },
                )
            }
        }
    )
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text = text,
        fontSize = 24.sp,
        style = AppTypography.h3,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
