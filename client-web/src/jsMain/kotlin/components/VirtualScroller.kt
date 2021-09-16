/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.frontend.components

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.get

private const val ITEM_BUFFER_COUNT = 1

@Composable
fun <T> VirtualScroller(
    items: List<T>,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    buildItem: @Composable (T) -> Unit,
) {
    val itemCount = derivedStateOf { items.size }
    val scope = rememberCoroutineScope()
    val itemSizeWH = remember { mutableStateOf(0 to 0) }
    val containerViewportWH = remember { mutableStateOf(0 to 0) }
    val scrollOffsetXY = remember { mutableStateOf(0 to 0) }

    val containerSizeWH = remember {
        derivedStateOf {
            val (containerVpWidth, _) = containerViewportWH.value
            val (itemWidth, itemHeight) = itemSizeWH.value

            val visibleXCount = (containerVpWidth / itemWidth)
            val totalYCount = (itemCount.value / visibleXCount)

            (visibleXCount * itemWidth to totalYCount * itemHeight)
        }
    }
    val renderItemStartIndex by remember {
        derivedStateOf {
            val (containerVpWidth, _) = containerViewportWH.value
            val (_, scrollOffsetY) = scrollOffsetXY.value
            val (itemWidth, itemHeight) = itemSizeWH.value
            val verticalI = scrollOffsetY / itemHeight
            verticalI * (containerVpWidth / itemWidth)
        }
    }
    val itemSlice by derivedStateOf {
        val (containerVpWidth, containerVpHeight) = containerViewportWH.value
        val (itemWidth, itemHeight) = itemSizeWH.value

        val itemsPerRow = (containerVpWidth / itemWidth)
        val totalYCount = containerVpHeight / itemHeight.toFloat()
        val remainder = if (totalYCount % 1 > 0) 1 else 0
        val count = itemsPerRow * (totalYCount.toInt() + remainder + ITEM_BUFFER_COUNT)
        val startIndex = renderItemStartIndex.coerceAtMost(items.lastIndex)
        val finalIndex = (renderItemStartIndex + count).coerceAtMost(items.lastIndex)

        items
            .subList(startIndex, finalIndex)
            .takeIf { count > 0 }
            ?.chunked(itemsPerRow)
            .orEmpty()
    }
    Div({
        attrs?.invoke(this)
        classes("h-100", "w-100")
        style {
            overflow("scroll")
            position(Position.Relative)
        }
        onScroll { event ->
            val newOffset = (event.target as HTMLDivElement).run {
                scrollLeft.toInt() to scrollTop.toInt()
            }
            if (scrollOffsetXY.value != newOffset) {
                scrollOffsetXY.value = newOffset
            }
        }
    }) {
        DomSideEffect { ref ->
            val job = scope.launch {
                while (true) {
                    val newSize = ref.clientWidth to ref.offsetHeight
                    if (containerViewportWH.value != newSize) {
                        containerViewportWH.value = newSize
                    }
                    delay(60)
                }
            }
            onDispose {
                job.cancel()
                containerViewportWH.value = 0 to 0
                scrollOffsetXY.value = 0 to 0
            }
        }
        // Determine item size
        items.firstOrNull()?.also { item ->
            Div({
                style {
                    position(Position.Absolute)
                    opacity(0)
                    property("z-index", -100)
                    property("pointer-events", "none")
                }
            }) {
                buildItem(item)
                DomSideEffect { ref ->
                    val job = scope.launch {
                        while (true) {
                            val newSize = ref.children[0]
                                ?.run { clientWidth to clientHeight }
                                ?: 0 to 0
                            if (itemSizeWH.value != newSize) {
                                itemSizeWH.value = newSize
                            }
                            delay(100)
                        }
                    }
                    onDispose {
                        job.cancel()
                        itemSizeWH.value = 0 to 0
                    }
                }
            }
        }
        Div({
            style {
                val (w, h) = containerSizeWH.value
                width(w.px)
                height(h.px)
            }
        }) {
            itemSlice.forEachIndexed { rowI, row ->
                val (_, scrollOffsetY) = scrollOffsetXY.value
                val (itemWidth, itemHeight) = itemSizeWH.value
                row.forEachIndexed { columnI, item ->
                    val itemTop = (rowI * itemHeight) + ((scrollOffsetY / itemHeight) * itemHeight)
                    val itemLeft = (columnI * itemWidth)
                    Div({
                        style {
                            top(itemTop.px)
                            left(itemLeft.px)
                            position(Position.Absolute)
                        }
                    }) {
                        buildItem(item)
                    }
                }
            }
        }
    }
}