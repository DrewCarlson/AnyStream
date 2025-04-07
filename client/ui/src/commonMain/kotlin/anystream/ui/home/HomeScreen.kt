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
package anystream.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import anystream.client.AnyStreamClient
import anystream.models.*
import anystream.models.api.CurrentlyWatching
import anystream.models.api.HomeResponse
import anystream.router.BackStack
import anystream.routing.Routes
import anystream.ui.LocalAnyStreamClient
import anystream.ui.components.*
import anystream.ui.components.CarouselAutoPlayHandler
import anystream.ui.components.PagerIndicator
import anystream.ui.components.PosterCard
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kt.mobius.SimpleLogger
import kt.mobius.compose.rememberMobiusLoop
import kt.mobius.flow.FlowMobius

private val CARD_SPACING = 8.dp

@Composable
fun HomeScreen(
    client: AnyStreamClient,
    backStack: BackStack<Routes>,
    onMetadataClick: (metadataId: String) -> Unit,
    onPlayClick: (mediaLinkId: String) -> Unit,
    onViewMoviesClicked: () -> Unit,
) {
    val (modelState, eventConsumer) = rememberMobiusLoop(HomeScreenModel(), HomeScreenInit) {
        FlowMobius.loop(
            HomeScreenUpdate,
            HomeScreenHandler.create(client),
        ).logger(SimpleLogger("HomeScreen"))
    }
    val model by modelState
    Scaffold(
        modifier = Modifier
            .consumeWindowInsets(WindowInsets.statusBars)
    ) { paddingValues ->
        AnimatedContent(targetState = model.homeResponse) { targetState ->
            when (targetState) {
                is LoadableDataState.Loading -> LoadingScreen(paddingValues)
                is LoadableDataState.Loaded ->
                    HomeScreenContent(
                        paddingValues = paddingValues,
                        homeData = targetState.data,
                        populars = model.popular,
                        onMetadataClick = onMetadataClick,
                        onViewMoviesClicked = onViewMoviesClicked,
                        onContinueWatchingClick = onPlayClick,
                    )

                is LoadableDataState.Error -> Unit // TODO: add error view

                LoadableDataState.Empty -> Unit // TODO: add empty view
            }
        }
    }
}

@Composable
private fun HomeScreenContent(
    paddingValues: PaddingValues,
    homeData: HomeResponse,
    populars: List<Pair<Movie, MediaLink?>>,
    onMetadataClick: (metadataId: String) -> Unit,
    onViewMoviesClicked: () -> Unit,
    onContinueWatchingClick: (mediaLinkId: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val (currentlyWatching, recentlyAdded, popular) = homeData
        val pagerState = rememberPagerState { populars.count() }

        Box(Modifier.height(375.dp).fillMaxWidth()) {
            MediaCarousel(pagerState = pagerState, media = populars)
            PagerIndicator(count = populars.count(), currentPage = pagerState.currentPage)
        }

        CarouselAutoPlayHandler(pagerState, populars.count())

        if (currentlyWatching.playbackStates.isNotEmpty()) {
            Column(Modifier.padding(start = 20.dp)) {
                SectionHeader(title = "Continue Watching")
                ContinueWatchingRow(
                    currentlyWatching,
                    onPlayClick = onContinueWatchingClick,
                )
            }
        }

        if (recentlyAdded.movies.isNotEmpty()) {
            Column(Modifier.padding(start = 20.dp)) {
                SectionHeader(title = "Recently Added Movies", ctaText = "All Movies") {
                    onViewMoviesClicked()
                }
                MovieRow(
                    movies = recentlyAdded.movies.toList(),
                    onMetadataClick = onMetadataClick,
                    onPlayClick = onContinueWatchingClick,
                )
            }
        }

        if (recentlyAdded.tvShows.isNotEmpty()) {
            Column(Modifier.padding(start = 20.dp)) {
                SectionHeader(
                    title = "Recently Added TV",
                    ctaText = "All Shows",
                    onCtaClicked = onViewMoviesClicked,
                )
                TvRow(
                    shows = recentlyAdded.tvShows,
                    onMetadataClick = onMetadataClick,
                    onPlayClick = onContinueWatchingClick,
                )
            }
        }

        Column(Modifier.padding(start = 20.dp)) {
            SectionHeader(title = "Popular Movies")
            MovieRow(
                movies = popular.movies.toList(),
                onMetadataClick = onMetadataClick,
                onPlayClick = onContinueWatchingClick,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ContinueWatchingRow(
    currentlyWatching: CurrentlyWatching,
    onPlayClick: (mediaLinkId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (playbackStates, currentlyWatchingMovies, currentlyWatchingTv, _) = currentlyWatching
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        modifier = modifier,
        content = {
            itemsIndexed(playbackStates) { index, playbackState ->
                currentlyWatchingMovies[playbackState.id]?.also { movie ->
                    PosterCard(
                        title = movie.title,
                        mediaId = movie.id,
                        onClick = { onPlayClick(playbackState.mediaLinkId) },
                        onPlayClick = { onPlayClick(playbackState.mediaLinkId) },
                    )
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
                        mediaType = MediaType.TV_EPISODE,
                    )
                    WatchingCard(mediaItem, playbackState, onPlayClick)
                }

                if (index == playbackStates.lastIndex) {
                    Spacer(Modifier.width(24.dp))
                }
            }
        },
    )
}

@Composable
private fun WatchingCard(
    mediaItem: MediaItem,
    playbackState: PlaybackState,
    onPlayClick: (mediaLinkId: String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier
            .width(256.dp)
            .clickable(onClick = { onPlayClick(playbackState.mediaLinkId) }),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .height(144.dp)
                    .fillMaxWidth(),
            ) {
                val client = LocalAnyStreamClient.current
                val painter = rememberAsyncImagePainter(
                    model = client.buildImageUrl("poster", mediaItem.mediaId, 300),
                )
                val state by painter.state.collectAsState()

                when (state) {
                    is AsyncImagePainter.State.Success ->
                        Image(
                            painter = painter,
                            contentDescription = "Backdrop for ${mediaItem.contentTitle}",
                            modifier = Modifier.fillMaxSize()
                        )

                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray),
                        )
                    }

                    AsyncImagePainter.State.Empty,
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
                }
            }

            LinearProgressIndicator(
                progress = { (playbackState.position / playbackState.runtime).toFloat() },
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
    movies: List<Pair<Movie, MediaLink?>>,
    onMetadataClick: (metadataId: String) -> Unit,
    onPlayClick: (mediaLinkId: String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        content = {
            itemsIndexed(movies) { index, (movie, mediaLink) ->
                PosterCard(
                    title = movie.title,
                    mediaId = movie.id,
                    onClick = { onMetadataClick(movie.id) },
                    onPlayClick = { mediaLink?.run { onPlayClick(id) } },
                )
                if (index == movies.lastIndex) {
                    Spacer(Modifier.width(24.dp))
                }
            }
        },
    )
}

@Composable
private fun TvRow(
    shows: List<TvShow>,
    onMetadataClick: (metadataId: String) -> Unit,
    onPlayClick: (mediaLinkId: String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        content = {
            itemsIndexed(shows) { index, show ->
                PosterCard(
                    title = show.name,
                    mediaId = show.id,
                    onClick = { onMetadataClick(show.id) },
                    onPlayClick = { onPlayClick(show.id) },
                )
                if (index == shows.lastIndex) {
                    Spacer(Modifier.width(24.dp))
                }
            }
        },
    )
}
