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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.ImagePainter
import coil.compose.rememberImagePainter

@Composable
fun PosterCard(
    title: String,
    imagePath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preferredWidth: Dp = 130.dp
) {
    Card(
        shape = RoundedCornerShape(size = 2.dp),
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .width(preferredWidth)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    bottomStart = 2.dp,
                    bottomEnd = 2.dp
                ),
                modifier = Modifier
                    .aspectRatio(ratio = 0.69f)
            ) {
                val painter = rememberImagePainter(
                    data = "https://image.tmdb.org/t/p/w200$imagePath",
                    builder = {
                        crossfade(true)
                    }
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                    )

                    when (painter.state) {
                        is ImagePainter.State.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.DarkGray)
                            )
                        }
                        is ImagePainter.State.Empty -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.DarkGray)
                            ) {
                                Text("No Backdrop")
                            }
                        }
                        is ImagePainter.State.Success -> Unit
                        is ImagePainter.State.Error -> Unit
                    }
                }
            }

            Box(modifier = Modifier.padding(all = 4.dp)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Preview(name = "Movie Card")
@Composable
private fun MovieCardPreview() {
    PosterCard(
        title = "Turbo: A Power Rangers Movie",
        imagePath = "/sHgjdRPYduUCaw3Te2CXaWLpBkm.jpg",
        onClick = { }
    )
}
