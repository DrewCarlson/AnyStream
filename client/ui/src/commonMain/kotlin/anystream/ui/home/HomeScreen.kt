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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import anystream.client.AnyStreamClient
import anystream.models.*
import anystream.models.api.CurrentlyWatching
import anystream.models.api.Popular
import anystream.models.api.RecentlyAdded
import anystream.presentation.home.*
import anystream.ui.components.*
import anystream.ui.components.CarouselAutoPlayHandler
import anystream.ui.components.PagerIndicator
import anystream.ui.components.PosterCard
import anystream.ui.util.LocalImageProvider
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kt.mobius.SimpleLogger
import kt.mobius.compose.rememberMobiusLoop
import kt.mobius.flow.FlowMobius

private val CARD_SPACING = 8.dp

@Composable
fun HomeScreen(
    client: AnyStreamClient,
    onMetadataClick: (metadataId: String) -> Unit,
    onPlayClick: (mediaLinkId: String) -> Unit,
    onViewMoviesClicked: (libraryId: String) -> Unit,
    onViewTvShowsClicked: (libraryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (modelState, eventConsumer) = rememberMobiusLoop(HomeScreenModel.Loading, HomeScreenInit) {
        FlowMobius.loop(
            HomeScreenUpdate,
            HomeScreenHandler(client),
        ).logger(SimpleLogger("HomeScreen"))
    }
    val model by modelState
    AnimatedContent(model) { currentModel ->
        when (currentModel) {
            is HomeScreenModel.Loading -> LoadingScreen(modifier = modifier)
            is HomeScreenModel.Loaded ->
                HomeScreenContent(
                    modifier = modifier,
                    libraries = currentModel.libraries,
                    currentlyWatching = currentModel.currentlyWatching,
                    recentlyAdded = currentModel.recentlyAdded,
                    popular = currentModel.popular,
                    populars = currentModel.popular.movies.toList().take(7),
                    onMetadataClick = onMetadataClick,
                    onViewMoviesClicked = onViewMoviesClicked,
                    onViewTvShowsClicked = onViewTvShowsClicked,
                    onContinueWatchingClick = onPlayClick,
                )

            is HomeScreenModel.LoadingFailed -> Unit // TODO: add error view

            HomeScreenModel.Empty -> Unit // TODO: add empty view
        }
    }
}

@Composable
private fun HomeScreenContent(
    libraries: List<Library>,
    currentlyWatching: CurrentlyWatching,
    recentlyAdded: RecentlyAdded,
    popular: Popular,
    populars: List<Pair<Movie, MediaLink?>>,
    onMetadataClick: (metadataId: String) -> Unit,
    onViewMoviesClicked: (String) -> Unit,
    onViewTvShowsClicked: (String) -> Unit,
    onContinueWatchingClick: (mediaLinkId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .then(modifier), // NOTE: Applied after scroll for haze effect
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val pagerState = rememberPagerState { populars.count() }

        Box(
            modifier = Modifier
                .height(375.dp)
                .fillMaxWidth()
        ) {
            MediaCarousel(pagerState = pagerState, media = populars)
            PagerIndicator(count = populars.count(), currentPage = pagerState.currentPage)
        }

        CarouselAutoPlayHandler(pagerState, populars.count())

        if (currentlyWatching.playbackStates.isNotEmpty()) {
            SectionHeader(title = "Continue Watching")
            ContinueWatchingRow(
                currentlyWatching = currentlyWatching,
                onPlayClick = onContinueWatchingClick,
            )
        }

        if (recentlyAdded.movies.isNotEmpty()) {
            SectionHeader(
                title = "Recently Added Movies",
                ctaText = "All Movies",
                onCtaClicked = {
                    // todo: get id from home data response
                    onViewMoviesClicked(libraries.first { it.mediaKind == MediaKind.MOVIE }.id)
                }
            )
            MovieRow(
                movies = recentlyAdded.movies.toList(),
                onMetadataClick = onMetadataClick,
                onPlayClick = onContinueWatchingClick,
            )
        }

        if (recentlyAdded.tvShows.isNotEmpty()) {
            SectionHeader(
                title = "Recently Added TV",
                ctaText = "All Shows",
                onCtaClicked = {
                    // todo: get id from home data response
                    onViewTvShowsClicked(libraries.first { it.mediaKind == MediaKind.TV }.id)
                },
            )
            TvRow(
                shows = recentlyAdded.tvShows,
                onMetadataClick = onMetadataClick,
                onPlayClick = onContinueWatchingClick,
            )
        }

        if (popular.movies.isNotEmpty()) {
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
            item { Spacer(Modifier.width(CARD_SPACING)) }

            items(playbackStates) { playbackState ->
                currentlyWatchingMovies[playbackState.id]?.also { movie ->
                    PosterCard(
                        title = movie.title,
                        mediaId = movie.id,
                        onClick = { onPlayClick(playbackState.mediaLinkId) },
                        onPlayClick = { onPlayClick(playbackState.mediaLinkId) },
                    )
                }
                currentlyWatchingTv[playbackState.id]?.also { (episode, show) ->
                    /*val mediaItem = MediaItem(
                        mediaId = episode.id,
                        contentTitle = show.name,
                        mediaLinks = emptyList(),
                        releaseDate = episode.airDate,
                        subtitle1 = episode.name,
                        subtitle2 = "S${episode.seasonNumber} · E${episode.number}",
                        overview = "",
                        mediaType = MediaType.TV_EPISODE,
                    )
                    WatchingCard(mediaItem, playbackState, onPlayClick)*/
                    PosterCard(
                        title = show.name,
                        mediaId = show.id,//TODO: episode id
                        onClick = { onPlayClick(playbackState.mediaLinkId) },
                        onPlayClick = { onPlayClick(playbackState.mediaLinkId) },
                    )
                }
            }

            item { Spacer(Modifier.width(CARD_SPACING)) }
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
                val imageUrlBuilder = LocalImageProvider.current
                val painter = rememberAsyncImagePainter(
                    model = imageUrlBuilder.url("poster", mediaItem.mediaId, 300),
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
            item { Spacer(Modifier.width(CARD_SPACING)) }

            items(movies) { (movie, mediaLink) ->
                PosterCard(
                    title = movie.title,
                    mediaId = movie.id,
                    onClick = { onMetadataClick(movie.id) },
                    onPlayClick = { mediaLink?.run { onPlayClick(id) } },
                )
            }

            item { Spacer(Modifier.width(CARD_SPACING)) }
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
            item { Spacer(Modifier.width(CARD_SPACING)) }

            items(shows) { show ->
                PosterCard(
                    title = show.name,
                    mediaId = show.id,
                    onClick = { onMetadataClick(show.id) },
                    onPlayClick = { onPlayClick(show.id) },
                )
            }

            item { Spacer(Modifier.width(CARD_SPACING)) }
        },
    )
}
