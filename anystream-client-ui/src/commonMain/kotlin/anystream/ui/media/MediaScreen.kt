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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.client.AnyStreamClient
import anystream.models.*
import anystream.models.api.MediaLookupResponse
import anystream.router.BackStack
import anystream.routing.Routes
import anystream.ui.components.PosterCard
import anystream.ui.util.cardWidth
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlin.time.DurationUnit.MINUTES
import kotlin.time.toDuration

@Composable
fun MediaScreen(
    client: AnyStreamClient,
    mediaId: String,
    onPlayClick: (mediaRefId: String?) -> Unit,
    backStack: BackStack<Routes>,
) {
    val lookupIdFlow = remember(mediaId) { MutableStateFlow<Int?>(null) }
    val refreshMetadata: () -> Unit = remember {
        {
            lookupIdFlow.update { (it ?: 0) + 1 }
        }
    }
    val mediaResponse by produceState<MediaLookupResponse?>(null, mediaId) {
        value = try {
            client.lookupMedia(mediaId)
        } catch (e: Throwable) {
            null
        }
        lookupIdFlow
            .filterNotNull()
            .debounce(1_000L)
            .collect {
                try {
                    value?.mediaLinks
                        ?.filter { it.descriptor.isMediaFileLink() }
                        ?.forEach { mediaLink ->
                            client.analyzeMediaLink(mediaLink.gid)
                        }
                    value = client.refreshMetadata(mediaId)
                } catch (_: Throwable) {
                }
            }
    }
    Scaffold {
        Column(modifier = Modifier.fillMaxSize()) {
            mediaResponse?.movie?.let { response ->
                BaseDetailsView(
                    mediaItem = response.toMediaItem(),
                    refreshMetadata = refreshMetadata,
                    client = client,
                    backStack = backStack,
                    onPlayClick = onPlayClick,
                )
            }

            mediaResponse?.tvShow?.let { response ->
                BaseDetailsView(
                    mediaItem = response.toMediaItem(),
                    refreshMetadata = refreshMetadata,
                    client = client,
                    backStack = backStack,
                    onPlayClick = onPlayClick,
                ) {
                    if (response.seasons.isNotEmpty()) {
                        SeasonRow(
                            seasons = response.seasons,
                            backStack = backStack,
                        )
                    }
                }
            }

            mediaResponse?.season?.let { response ->
                BaseDetailsView(
                    mediaItem = response.toMediaItem(),
                    refreshMetadata = refreshMetadata,
                    client = client,
                    backStack = backStack,
                    onPlayClick = onPlayClick,
                ) {
                    if (response.episodes.isNotEmpty()) {
                        EpisodeGrid(
                            episodes = response.episodes,
                            mediaLinks = response.mediaLinks,
                            backStack = backStack,
                        )
                    }
                }
            }

            mediaResponse?.episode?.let { response ->
                val mediaItem = remember(response) { response.toMediaItem() }
                BaseDetailsView(
                    mediaItem = mediaItem,
                    refreshMetadata = refreshMetadata,
                    client = client,
                    backStack = backStack,
                    onPlayClick = onPlayClick,
                )
            }
        }
    }
}

@Composable
private fun BaseDetailsView(
    mediaItem: MediaItem,
    refreshMetadata: () -> Unit,
    client: AnyStreamClient,
    backStack: BackStack<Routes>,
    onPlayClick: (mediaRefId: String?) -> Unit,
    subcontainer: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.37f),
        ) {
            KamelImage(
                resource = asyncPainterResource(data = mediaItem.tmdbBackdropUrl),
                contentDescription = "",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(.7f),
                onLoading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray),
                    )
                },
                onFailure = { },
                animationSpec = tween(),
            )

            Column(
                modifier = Modifier
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x99000000), Color(0x10000000)),
                        ),
                    )
                    .fillMaxWidth()
                    .fillMaxHeight(.8f),
            ) {
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = { backStack.pop() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "",
                            tint = MaterialTheme.colors.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "",
                            tint = MaterialTheme.colors.onSurface,
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
                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .align(Alignment.Bottom),
                    ) {
                        Card(elevation = 2.dp, shape = RectangleShape) {
                            KamelImage(
                                resource = asyncPainterResource(data = Url(mediaItem.tmdbPosterUrl)),
                                contentDescription = "",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth(),
                                onLoading = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.DarkGray),
                                    )
                                },
                                onFailure = { },
                                animationSpec = tween(),
                                contentAlignment = Alignment.BottomCenter,
                            )
                        }
                    }

                    MediaMetadata(mediaItem)
                }
            }
        }

        Button(
            onClick = {
                onPlayClick(mediaItem.playableMediaLink?.gid)
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red),
            modifier = Modifier
                .padding(top = 24.dp, start = 16.dp)
                .width(cardWidth),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "",
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = if (mediaItem.playbackState != null) "Resume" else "Play",
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        ExpandableText(mediaItem.overview)

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            subcontainer()
        }
    }
}

@Composable
private fun MediaMetadata(mediaItem: MediaItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(text = mediaItem.contentTitle, style = MaterialTheme.typography.body1)
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
                        style = MaterialTheme.typography.caption,
                    )
                }
                Text(
                    text = item,
                    color = Color(0x80FFFFFF),
                    style = MaterialTheme.typography.caption,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        val tmdbRating = mediaItem.tmdbRating?.toString()
        if (tmdbRating != null) {
            Text(
                text = "$tmdbRating%",
                color = Color(0x80FFFFFF),
                style = MaterialTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun SeasonRow(
    seasons: List<TvSeason>,
    backStack: BackStack<Routes>,
) {
    BaseRow(
        title = "${seasons.size} Seasons",
    ) {
        seasons.forEach { season ->
            PosterCard(
                title = season.name,
                imagePath = season.posterPath,
                onClick = {
                    backStack.push(Routes.Details(season.gid))
                },
                onPlayClick = {},
            )
        }
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    mediaLinks: Map<String, MediaLink>,
    backStack: BackStack<Routes>,
) {
    BaseRow(
        title = "${episodes.size} Episodes",
    ) {
        episodes.forEach { episode ->
            val link = mediaLinks[episode.gid]
            PosterCard(
                title = episode.name,
                /*subtitle1 = {
                    LinkedText("/media/${episode.gid}") {
                        Text("Episode ${episode.number}")
                    }
                },*/
                imagePath = episode.stillPath,
                // heightAndWidth = 178.px to 318.px,
                onPlayClick = {
                },
                onClick = {
                    backStack.push(Routes.Details(episode.gid))
                },
            )
        }
    }
}

@Composable
private fun BaseRow(
    title: String,
    buildItems: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.h5,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            buildItems()
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
            style = MaterialTheme.typography.body1,
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
                    color = MaterialTheme.colors.primary,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}
