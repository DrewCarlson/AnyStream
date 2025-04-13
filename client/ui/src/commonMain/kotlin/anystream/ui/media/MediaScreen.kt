/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.models.*
import anystream.models.api.*
import anystream.ui.LocalAnyStreamClient
import anystream.ui.components.PosterCard
import anystream.ui.components.PosterCardWidth
import anystream.ui.generated.resources.Res
import anystream.ui.generated.resources.ic_play
import anystream.ui.generated.resources.tmdb_small
import anystream.ui.util.LocalImageProvider
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import org.jetbrains.compose.resources.painterResource
import kotlin.time.DurationUnit.MINUTES
import kotlin.time.toDuration


@Composable
fun MediaScreen(
    mediaId: String,
    onPlayClick: (mediaLinkId: String) -> Unit,
    onMetadataClick: (metadataId: String) -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val client = LocalAnyStreamClient.current
    val mediaResponse by produceState<MediaLookupResponse?>(null, mediaId) {
        value = try {
            client.lookupMedia(mediaId)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    MediaScreen(
        response = mediaResponse,
        onPlayClick = onPlayClick,
        onMetadataClick = onMetadataClick,
        onBackClicked = onBackClicked,
        modifier = modifier,
    )
}

@Composable
fun MediaScreen(
    response: MediaLookupResponse?,
    onPlayClick: (mediaRefId: String) -> Unit,
    onMetadataClick: (metadataId: String) -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.statusBars)
            .navigationBarsPadding()
    ) {
        when (response) {
            is EpisodeResponse -> {
                val mediaItem = remember(response) { response.toMediaItem() }
                BaseDetailsView(
                    mediaItem = mediaItem,
                    onBackClicked = onBackClicked,
                    onPlayClick = onPlayClick,
                )
            }

            is MovieResponse -> {
                BaseDetailsView(
                    mediaItem = response.toMediaItem(),
                    onBackClicked = onBackClicked,
                    onPlayClick = onPlayClick,
                )
            }

            is SeasonResponse -> {
                BaseDetailsView(
                    mediaItem = response.toMediaItem(),
                    onBackClicked = onBackClicked,
                    onPlayClick = onPlayClick,
                ) {
                    if (response.episodes.isNotEmpty()) {
                        EpisodeGrid(
                            episodes = response.episodes,
                            mediaLinks = response.mediaLinkMap,
                            onEpisodeClick = { episode ->
                                onMetadataClick(episode.id)
                            }
                        )
                    }
                }
            }

            is TvShowResponse -> {
                BaseDetailsView(
                    mediaItem = response.toMediaItem(),
                    onBackClicked = onBackClicked,
                    onPlayClick = onPlayClick,
                ) {
                    if (response.seasons.isNotEmpty()) {
                        SeasonRow(
                            seasons = response.seasons,
                            onMetadataClick = onMetadataClick,
                        )
                    }
                }
            }

            null -> Unit
        }
    }
}

@Composable
private fun BaseDetailsView(
    mediaItem: MediaItem,
    onBackClicked: () -> Unit,
    onPlayClick: (mediaLinkId: String) -> Unit,
    subcontainer: @Composable () -> Unit = {},
) {
    val imageUrlBuilder = LocalImageProvider.current
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.37f),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                //.fillMaxHeight(.7f)
            ) {
                val painter = rememberAsyncImagePainter(
                    model = imageUrlBuilder.url("backdrop", mediaItem.mediaId),
                    contentScale = ContentScale.Crop,
                )
                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Success ->
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray),
                        )
                    }

                    AsyncImagePainter.State.Empty -> Unit
                    is AsyncImagePainter.State.Error -> Unit
                }

                val bgColor = MaterialTheme.colorScheme.background
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    bgColor.copy(alpha = 0f),
                                    bgColor
                                ),
                            ),
                        ),
                )
            }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.statusBars.asPaddingValues()),
                ) {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    PosterCard(
                        title = null,
                        mediaId = mediaItem.mediaId,
                        onClick = null,
                        onPlayClick = null,
                        modifier = Modifier
                            .align(Alignment.Bottom)
                    )

                    MediaMetadata(mediaItem)
                }
            }
        }

        Button(
            onClick = {
                mediaItem.playableMediaLink?.run { onPlayClick(id) }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red,
                contentColor = Color.White,
            ),
            modifier = Modifier
                .padding(top = 24.dp, start = 16.dp)
                .width(PosterCardWidth),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                painterResource(Res.drawable.ic_play),
                contentDescription = "",
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (mediaItem.playbackState != null) "Resume" else "Play",
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        ExpandableText(mediaItem.overview)

        Spacer(Modifier.height(12.dp))

        Box {
            subcontainer()
        }
    }
}

@Composable
private fun MediaMetadata(mediaItem: MediaItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = mediaItem.contentTitle,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val items = remember(mediaItem) {
                listOfNotNull(
                    mediaItem.releaseYear,
                    mediaItem.runtime?.toDuration(MINUTES)?.asFriendlyString(),
                    mediaItem.contentRating,
                )
            }
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Text(
                        text = "•",
                        color = Color(0x80FFFFFF),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = item,
                    color = Color(0x80FFFFFF),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        val tmdbRating = mediaItem.tmdbRating?.toString()
        if (tmdbRating != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$tmdbRating%",
                    color = Color(0x80FFFFFF),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Image(
                    painter = painterResource(Res.drawable.tmdb_small),
                    contentDescription = null,
                    modifier = Modifier.height(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SeasonRow(
    seasons: List<TvSeason>,
    onMetadataClick: (metadataId: String) -> Unit,
) {
    BaseRow(
        title = "${seasons.size} Seasons",
        items = seasons,
    ) { season ->
        PosterCard(
            title = season.name,
            mediaId = season.id,
            onClick = { onMetadataClick(season.id) },
            onPlayClick = {},
        )
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    mediaLinks: Map<String, MediaLink>,
    onEpisodeClick: (episode: Episode) -> Unit,
) {
    BaseRow(
        title = "${episodes.size} Episodes",
        items = episodes,
    ) { episode ->
        val link = mediaLinks[episode.id]
        PosterCard(
            title = episode.name,
            /*subtitle1 = {
                LinkedText("/media/${episode.id}") {
                    Text("Episode ${episode.number}")
                }
            },*/
            mediaId = episode.id,
            // heightAndWidth = 178.px to 318.px,
            onPlayClick = null,
            onClick = { onEpisodeClick(episode) },
        )
    }
}

@Composable
private fun <T> BaseRow(
    title: String,
    items: List<T>,
    buildItem: @Composable (T) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.width(4.dp)) }
            items(items) {
                buildItem(it)
            }
            item { Spacer(Modifier.width(4.dp)) }
        }
    }
}

@Composable
internal fun ExpandableText(overview: String) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val isExpandable by remember { derivedStateOf { textLayoutResult?.didOverflowHeight ?: false } }
    var isExpanded by remember { mutableStateOf(false) }
    val isButtonShown by remember { derivedStateOf { isExpandable || isExpanded } }

    Column(Modifier.animateContentSize(animationSpec = tween(100))) {
        Text(
            text = overview,
            modifier = Modifier.padding(16.dp).animateContentSize(),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult = it },
        )

        if (isButtonShown) {
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = if (isExpanded) "LESS" else "MORE",
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}
