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
package anystream.ui.preview

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import anystream.ui.components.PosterCard
import anystream.ui.home.MediaCarousel
import anystream.ui.theme.AppTheme

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

@Preview
@Composable
private fun MediaCarousel_Preview() = AppTheme {
    MediaCarousel(rememberPagerState(0) { 5 }, media = listOf())
}
