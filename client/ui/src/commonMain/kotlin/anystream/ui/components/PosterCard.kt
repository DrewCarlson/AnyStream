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
package anystream.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import anystream.ui.LocalAnyStreamClient
import anystream.ui.theme.AppTheme
import anystream.ui.util.pointerMover
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

internal val PosterCardWidth = 120.dp

@Composable
internal fun PosterCard(
    title: String,
    mediaId: String,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = .664f,
    width: Dp = PosterCardWidth,
) {
    var showPlayButtonOverlay by remember { mutableStateOf(false) }

    val cornerShape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .width(width),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .shadow(elevation = 8.dp, cornerShape)
                .background(Color(0xFF35383F), cornerShape)
                .clip(cornerShape)
                .clickable(onClick = onClick)
                .pointerMover { showPlayButtonOverlay = it },
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val client = LocalAnyStreamClient.current
                val painter = rememberAsyncImagePainter(
                    model = client.buildImageUrl("poster", mediaId, 300),
                    contentScale = ContentScale.FillBounds,
                )
                val state by painter.state.collectAsState()
                val loaded by produceState(state is AsyncImagePainter.State.Success, state) {
                    value = state is AsyncImagePainter.State.Success
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = loaded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                }

                Column(
                    modifier = Modifier.matchParentSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedVisibility(
                        visible = showPlayButtonOverlay,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(400)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background, CircleShape)
                                .clickable(showPlayButtonOverlay, onClick = onPlayClick)
                                .padding(2.dp),
                            tint = Color.Red,
                        )
                    }
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(width)
        )
    }
}

@Preview
@Composable
private fun PosterCardPreview() = AppTheme {
    PosterCard(
        title = "Gremlins",
        mediaId = "",
        onClick = {},
        onPlayClick = {},
    )
}
