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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import anystream.ui.theme.AppTheme
import anystream.ui.util.pointerMover
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

@Composable
internal fun PosterCard(
    title: String,
    imagePath: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    preferredWidth: Dp = 150.dp,
    aspectRatio: Float = .75f,
) {
    var showPlayButtonOverlay by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .widthIn(0.dp, preferredWidth)
            .clickable(onClick = onClick)
            .pointerMover { showPlayButtonOverlay = it },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val painter = rememberAsyncImagePainter(
                model = "https://image.tmdb.org/t/p/w200$imagePath",
                contentScale = ContentScale.FillBounds,
            )
            val state by painter.state.collectAsState()
            when (state) {
                is AsyncImagePainter.State.Success -> {
                    Image(
                        painter = painter,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 8.dp, RoundedCornerShape(6.dp))
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                    )
                }

                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier
                            .aspectRatio(aspectRatio)
                            .background(
                                color = Color(0xFFBDBDBD),
                                shape = RoundedCornerShape(size = 6.dp),
                            ),
                    )
                }

                AsyncImagePainter.State.Empty -> Unit
                is AsyncImagePainter.State.Error -> Unit
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
                        modifier = Modifier.size(preferredWidth / 4)
                            .background(MaterialTheme.colors.background, CircleShape)
                            .clickable(showPlayButtonOverlay, onClick = onPlayClick)
                            .padding(2.dp),
                        tint = Color.Red,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PosterCardPreview() = AppTheme {
    PosterCard(
        title = "Gremlins",
        imagePath = "",
        onClick = {},
        onPlayClick = {},
    )
}
