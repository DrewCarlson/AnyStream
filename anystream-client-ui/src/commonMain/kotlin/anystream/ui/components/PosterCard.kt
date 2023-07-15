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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.ui.util.cardWidth
import anystream.ui.util.pointerMover
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.ktor.http.Url

@Composable
fun PosterCard(
    title: String,
    imagePath: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    preferredWidth: Dp = 130.dp,
) {
    var showPlayButtonOverlay by remember { mutableStateOf(false) }

    Card(
        modifier
            .clickable(onClick = onClick)
            .pointerMover { showPlayButtonOverlay = it },
        shape = RoundedCornerShape(size = 4.dp),
    ) {
        Column(Modifier.width(preferredWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                modifier = Modifier.aspectRatio(ratio = 0.69f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    KamelImage(
                        resource = asyncPainterResource(data = Url("https://image.tmdb.org/t/p/w200$imagePath")),
                        contentDescription = "Profile",
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
                        Modifier.matchParentSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedVisibility(
                            showPlayButtonOverlay,
                            enter = fadeIn(tween(600)),
                            exit = fadeOut(tween(1500)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(cardWidth / 4)
                                    .background(MaterialTheme.colors.background, CircleShape)
                                    .clickable(showPlayButtonOverlay, onClick = onPlayClick)
                                    .padding(2.dp),
                                tint = Color.Red,
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    text = title,
                    maxLines = 2,
                    minLines = 2,
                    style = TextStyle(fontSize = 14.sp),
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
