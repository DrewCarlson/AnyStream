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
package anystream.components

import androidx.compose.runtime.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import web.dom.observers.ResizeObserver
import web.dom.observers.ResizeObserverEntry
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.random.Random

private const val ITEM_BUFFER = 1
private const val MAX_CACHED_ITEMS = 60

private data class CompositionStateHolder<T>(
    val composition: Composition,
    val itemTop: MutableState<Int>,
    val itemLeft: MutableState<Int>,
    val itemState: MutableState<T?>,
)

@Composable
fun <T> VirtualScroller(
    items: List<T>,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    buildItem: @Composable (T) -> Unit,
) {
    val instanceId = remember { Random.nextLong().absoluteValue }
    val parentId = remember { "vs-parent-$instanceId" }
    val containerId = remember { "vs-container-$instanceId" }
    val placeHolderId = remember { "vs-placeholder-$instanceId" }
    val itemCount = derivedStateOf { items.size }
    val itemSizeWH = remember { mutableStateOf(0 to 0) }
    val containerViewportWH = remember { mutableStateOf(0 to 0) }
    val scrollOffsetXY = remember { mutableStateOf(0 to 0) }

    val containerSizeWH = remember {
        derivedStateOf {
            val (containerVpWidth, _) = containerViewportWH.value
            val (itemWidth, itemHeight) = itemSizeWH.value

            val visibleXCount = (containerVpWidth / itemWidth)
            val totalYCount = ceil((itemCount.value / visibleXCount.toFloat())).toInt()

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
    val itemSlice by remember {
        derivedStateOf {
            val (containerVpWidth, containerVpHeight) = containerViewportWH.value
            val (itemWidth, itemHeight) = itemSizeWH.value

            val itemsPerRow = (containerVpWidth / itemWidth)
            val totalYCount = containerVpHeight / itemHeight.toFloat()
            val remainder = if (totalYCount % 1 > 0) 1 else 0
            val count = itemsPerRow * (totalYCount.toInt() + remainder)
            val buffer = ITEM_BUFFER * itemsPerRow
            val startIndex = renderItemStartIndex.coerceIn(0..items.lastIndex)
            val finalIndex = (renderItemStartIndex + count + buffer).coerceAtMost(items.size)

            items
                .subList(startIndex, finalIndex)
                .takeIf { count > 0 }
                ?.chunked(itemsPerRow)
                .orEmpty()
        }
    }
    Div(
        {
            id(parentId)
            attrs?.invoke(this)
            classes("h-100", "w-100")
            style {
                position(Position.Relative)
                overflowY("scroll")
                overflowX("hidden")
            }
            onScroll { event ->
                val newOffset = (event.target as HTMLDivElement).run {
                    scrollLeft.toInt() to scrollTop.toInt()
                }
                if (scrollOffsetXY.value != newOffset) {
                    scrollOffsetXY.value = newOffset
                }
            }
        },
    ) {
        ObserverResize(
            parentId,
            onDisposed = {
                containerViewportWH.value = 0 to 0
                scrollOffsetXY.value = 0 to 0
            },
        ) { entry ->
            val newWidth = entry.contentRect.width.toInt()
            val newHeight = entry.contentRect.height.toInt()

            if (newWidth != containerViewportWH.value.first || newHeight != containerViewportWH.value.second) {
                containerViewportWH.value = newWidth to newHeight
            }
        }
        // Create an invisible dummy item to determine it's size
        items.firstOrNull()?.also { item ->
            Div(
                {
                    id(placeHolderId)
                    style {
                        position(Position.Absolute)
                        opacity(0)
                        property("z-index", -100)
                        property("pointer-events", "none")
                    }
                },
            ) {
                buildItem(item)
                ObserverResize(placeHolderId) { entry ->
                    val newWidth = entry.contentRect.width.toInt()
                    val newHeight = entry.contentRect.height.toInt()

                    if (itemSizeWH.value.first != newWidth || itemSizeWH.value.second != newHeight) {
                        itemSizeWH.value = newWidth to newHeight
                    }
                }
            }
        }
        Div(
            {
                id(containerId)
                style {
                    val (w, h) = containerSizeWH.value
                    width(w.px)
                    height(h.px)
                }
            },
        ) {
            // Holders with an item are associated by the item here
            val activeHolders = remember { mutableStateMapOf<T, CompositionStateHolder<T>>() }
            // Any unused holders are kept here, waiting for a new item
            val cachedHolders = remember { mutableStateListOf<CompositionStateHolder<T>>() }

            LaunchedEffect(itemSlice) {
                // When the list of items to display changes, unbind any unused holders,
                // returning them to the cache up to the max cache size.
                (activeHolders.keys - itemSlice.flatten().toSet())
                    .mapNotNull(activeHolders::remove)
                    .take(MAX_CACHED_ITEMS - cachedHolders.size)
                    .onEach { holder ->
                        holder.itemLeft.value = -500
                        holder.itemTop.value = -500
                    }
                    .onEach(cachedHolders::add)
            }

            val compositionLocalContext = currentCompositionLocalContext
            fun bindHolder(item: T, itemTop: Int, itemLeft: Int) {
                // Find the active holder unless the content is unchanged,
                // or pull one from the cache, otherwise create a new one.
                val holder = activeHolders[item]
                    ?: cachedHolders.removeFirstOrNull()
                    ?: createHolder(containerId, compositionLocalContext, buildItem)

                activeHolders[item] = holder
                holder.itemTop.value = itemTop
                holder.itemLeft.value = itemLeft
                holder.itemState.value = item
            }

            // Determine where each item should be and bind a holder
            // to that item and position based on scroll offset.
            val (_, scrollOffsetY) = scrollOffsetXY.value
            val (itemWidth, itemHeight) = itemSizeWH.value
            val sliceYOffset = ((scrollOffsetY / itemHeight) * itemHeight)
            itemSlice.forEachIndexed { rowI, row ->
                val itemTop = (rowI * itemHeight) + sliceYOffset
                row.forEachIndexed { columnI, item ->
                    val itemLeft = (columnI * itemWidth)
                    bindHolder(item, itemTop, itemLeft)
                }
            }
        }
    }
}

private fun <T> createHolder(
    containerId: String,
    compositionLocalContext: CompositionLocalContext,
    buildItem: @Composable (T) -> Unit,
): CompositionStateHolder<T> {
    val itemTop = mutableStateOf(0)
    val itemLeft = mutableStateOf(0)
    val itemState = mutableStateOf<T?>(null)
    val composition = renderComposable(containerId) {
        CompositionLocalProvider(compositionLocalContext) {
            Div(
                {
                    style {
                        position(Position.Absolute)
                        property(
                            "transform",
                            "translate(${itemLeft.value.px}, ${itemTop.value.px})",
                        )
                        property("will-change", "transform")
                        // Hide cached item
                        if (itemState.value == null) {
                            opacity(0)
                            property("z-index", -100)
                            property("pointer-events", "none")
                        }
                    }
                },
            ) {
                itemState.value?.also { item -> buildItem(item) }
            }
        }
    }
    return CompositionStateHolder(composition, itemTop, itemLeft, itemState)
}

@Composable
private fun ObserverResize(
    id: String,
    onDisposed: () -> Unit = {},
    callback: (ResizeObserverEntry) -> Unit,
) {
    DisposableEffect(Unit) {
        val observer = ResizeObserver { entries, _ ->
            entries.forEach(callback)
        }
        val target = requireNotNull(web.dom.document.getElementById(id))
        observer.observe(target)
        onDispose {
            observer.unobserve(target)
            onDisposed()
        }
    }
}
