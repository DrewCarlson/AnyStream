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

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.ktor.http.Url

@Composable
fun PosterCard(
    title: String,
    imagePath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preferredWidth: Dp = 130.dp,
) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(size = 4.dp)) {
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
                        onFailure = { exception ->
                        },
                        animationSpec = tween(),
                    )
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
