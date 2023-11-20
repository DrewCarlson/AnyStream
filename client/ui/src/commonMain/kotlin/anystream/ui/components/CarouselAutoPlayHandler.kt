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
package anystream.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
internal fun CarouselAutoPlayHandler(pagerState: PagerState, carouselSize: Int) {
    var pageKey by remember { mutableStateOf(0) }

    val effectFlow =
        pagerState.interactionSource.interactions.collectAsState(DragInteraction.Start())

    LaunchedEffect(effectFlow.value) {
        if (effectFlow.value is DragInteraction.Stop) pageKey++
    }

    LaunchedEffect(pageKey) {
        delay(5000)
        val newPage = (pagerState.currentPage + 1) % carouselSize
        pagerState.animateScrollToPage(
            page = newPage,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
        pageKey++
    }
}
