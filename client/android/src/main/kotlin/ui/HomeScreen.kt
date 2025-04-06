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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.android.AppTopBar
import anystream.android.AppTypography
import anystream.android.router.BackStack
import anystream.client.AnyStreamClient
import anystream.models.*
import anystream.models.api.CurrentlyWatching
import anystream.models.api.HomeResponse
import anystream.routing.Routes
import anystream.ui.LocalAnyStreamClient
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

private val CARD_SPACING = 12.dp

@Composable
private fun RowSpace() = Spacer(modifier = Modifier.size(8.dp))

@Composable
fun HomeScreen(
    client: AnyStreamClient,
    backStack: BackStack<Routes>,
    onMediaClick: (mediaLinkId: String?) -> Unit,
    onViewMoviesClicked: () -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(client = client, backStack = backStack) },
    ) { paddigValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddigValues)
                .padding(horizontal = 8.dp),
        ) {
            item {
                val homeData by produceState<HomeResponse?>(null) {
                    value = client.getHomeData()
                }

                homeData?.run {
                    Spacer(modifier = Modifier.size(4.dp))

                    if (currentlyWatching.playbackStates.isNotEmpty()) {
                        RowTitle(text = "Continue Watching")
                        ContinueWatchingRow(
                            currentlyWatching = currentlyWatching,
                            onClick = onMediaClick,
                        )
                        RowSpace()
                    }

                    if (recentlyAdded.movies.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RowTitle(text = "Recently Added Movies")
                            TextButton(onClick = onViewMoviesClicked) {
                                Text(text = "All Movies")
                            }
                        }
                        MovieRow(movies = recentlyAdded.movies, onClick = onMediaClick)
                        RowSpace()
                    }

                    if (recentlyAdded.tvShows.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RowTitle(text = "Recently Added TV")
                            TextButton(onClick = onViewMoviesClicked) {
                                Text(text = "All Shows")
                            }
                        }
                        TvRow(shows = recentlyAdded.tvShows, onClick = onMediaClick)
                        RowSpace()
                    }

                    RowTitle(text = "Popular Movies")
                    MovieRow(movies = popular.movies, onClick = onMediaClick)
                    RowSpace()

                    RowTitle(text = "Popular TV")
                    TvRow(shows = popular.tvShows, onClick = { })
                    RowSpace()
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    currentlyWatching: CurrentlyWatching,
    onClick: (mediaLinkId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackStates = currentlyWatching.playbackStates
    val currentlyWatchingMovies = currentlyWatching.movies
    val currentlyWatchingTv = currentlyWatching.tvShows
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        modifier = modifier,
        content = {
            items(playbackStates) { playbackState ->
                currentlyWatchingMovies[playbackState.id]?.also { movie ->
                    val mediaItem = MediaItem(
                        mediaId = movie.id,
                        contentTitle = movie.title,
                        mediaLinks = emptyList(),
                        releaseDate = movie.releaseDate,
                        subtitle1 = movie.releaseDate?.split("-")?.first(),
                        overview = "",
                        mediaType = MediaType.TV_EPISODE
                    )
                    WatchingCard(mediaItem, playbackState, onClick)
                }
                currentlyWatchingTv[playbackState.id]?.also { (episode, show) ->
                    val mediaItem = MediaItem(
                        mediaId = episode.id,
                        contentTitle = show.name,
                        mediaLinks = emptyList(),
                        releaseDate = episode.airDate,
                        subtitle1 = episode.name,
                        subtitle2 = "S${episode.seasonNumber} · E${episode.number}",
                        overview = "",
                        mediaType = MediaType.TV_EPISODE
                    )
                    WatchingCard(mediaItem, playbackState, onClick)
                }
            }
        },
    )
}

@Composable
private fun WatchingCard(
    mediaItem: MediaItem,
    playbackState: PlaybackState,
    onClick: (mediaLinkId: String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier
            .width(256.dp)
            .clickable(onClick = { onClick(playbackState.mediaLinkId) }),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val client = LocalAnyStreamClient.current
            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(client.buildImageUrl("backdrop", mediaItem.mediaId, 300))
                    .crossfade(true)
                    .build(),
            )
            Box(
                modifier = Modifier
                    .height(144.dp)
                    .fillMaxWidth(),
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )

                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray),
                        )
                    }

                    is AsyncImagePainter.State.Error -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray),
                        ) {
                            Text("No Backdrop")
                        }
                    }

                    AsyncImagePainter.State.Empty,
                    is AsyncImagePainter.State.Success,
                    -> Unit
                }
            }

            LinearProgressIndicator(
                progress = (playbackState.position / playbackState.runtime).toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .padding(all = 4.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = mediaItem.contentTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mediaItem.subtitle1 ?: " ",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mediaItem.subtitle2 ?: " ",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MovieRow(
    movies: Map<Movie, MediaLink?>,
    onClick: (mediaLinkId: String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        content = {
            items(movies.toList()) { (movie, mediaLink) ->
                PosterCard(
                    title = movie.title,
                    metadataId = movie.id,
                    onClick = { mediaLink?.run { onClick(id) } },
                )
            }
        },
    )
}

@Composable
private fun TvRow(
    shows: List<TvShow>,
    onClick: (mediaLinkId: String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        content = {
            items(shows) { show ->
                PosterCard(
                    title = show.name,
                    metadataId = show.id,
                    onClick = { onClick(show.id) },
                )
            }
        },
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
