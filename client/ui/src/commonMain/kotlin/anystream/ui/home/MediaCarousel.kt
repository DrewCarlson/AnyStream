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
package anystream.ui.home

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.models.MediaLink
import anystream.models.Movie
import anystream.ui.LocalAnyStreamClient
import anystream.ui.generated.resources.Res
import anystream.ui.generated.resources.ic_play
import anystream.ui.theme.AppTheme
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun MediaCarousel(pagerState: PagerState, media: List<Pair<Movie, MediaLink?>>) {
    HorizontalPager(pagerState, modifier = Modifier.height(375.dp)) {
        Box(Modifier.fillMaxWidth()) {
            val client = LocalAnyStreamClient.current
            val painter = rememberAsyncImagePainter(
                // todo: restore size selection w1920_and_h800_multi_faces
                model = client.buildImageUrl("backdrop", media[it].first.id),
            )
            val state by painter.state.collectAsState()
            Box(
                modifier = Modifier.height(375.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                when (state) {
                    is AsyncImagePainter.State.Success ->
                        Image(
                            painter = painter,
                            contentDescription = "Movie Poster",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                    is AsyncImagePainter.State.Loading -> {
                        Box(Modifier.size(375.dp).background(color = Color(0xFFBDBDBD)))
                    }
                    AsyncImagePainter.State.Empty -> Unit
                    is AsyncImagePainter.State.Error -> Unit
                }
            }

            Row(
                Modifier
                    .height(230.dp)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x00181A20),
                                Color(0xFF181A20),
                            ),
                        ),
                    ),
            ) { }

            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
            ) {
                Text(
                    media[pagerState.currentPage].first.title,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    media[pagerState.currentPage].first.genres.map { it.name }.take(3)
                        .joinToString(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                CarouselMediaButtonRow()
            }
        }
    }
}

@Composable
private fun CarouselMediaButtonRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Button(
                onClick = {},
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    letterSpacing = 0.2.sp,
                )
            }

            Button(
                onClick = {},
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "My List",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

@Preview
@Composable
private fun CarouselMediaButtonRow_Preview() = AppTheme {
    MediaCarousel(rememberPagerState(0) { 5 }, media = listOf())
}
