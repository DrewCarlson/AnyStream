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
import org.jetbrains.compose.web.ExperimentalComposeWebApi
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLDivElement
import web.animations.awaitAnimationFrame
import web.resize.ResizeObserver
import web.resize.ResizeObserverEntry
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.random.Random

private const val DEFAULT_BUFFER_PAGES = 1

// TODO: Use a stable dynamic pool size selection
private const val HOLDER_POOL_SIZE = 50

val LocalInVirtualScrollCache = compositionLocalOf { false }

private data class ItemStateHolder<T>(
    val state: MutableState<T?>,
    val left: MutableState<Int>,
    val top: MutableState<Int>,
) {
    constructor(item: T, top: Int, left: Int) : this(
        state = mutableStateOf(item),
        left = mutableStateOf(left),
        top = mutableStateOf(top)
    )
}

private enum class ScrollerDirection {
    Vertical, Horizontal;
}

private enum class ScrollerLayout {
    LINEAR, GRID;
}

@Composable
fun <T> VerticalGridScroller(
    items: List<T>,
    scrollbars: Boolean = true,
    bufferPages: Int = DEFAULT_BUFFER_PAGES,
    placeholderBody: (@Composable () -> Unit)? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    itemBody: @Composable (T) -> Unit,
) {
    VirtualScroller(
        items = items,
        attrs = attrs,
        layout = ScrollerLayout.GRID,
        direction = ScrollerDirection.Vertical,
        scrollbars = scrollbars,
        bufferPages = bufferPages,
        placeholderBody = placeholderBody,
        itemBody = itemBody
    )
}

@Composable
fun <T> HorizontalGridScroller(
    items: List<T>,
    scrollbars: Boolean = true,
    bufferPages: Int = DEFAULT_BUFFER_PAGES,
    placeholderBody: (@Composable () -> Unit)? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    itemBody: @Composable (T) -> Unit,
) {
    VirtualScroller(
        items = items,
        attrs = attrs,
        layout = ScrollerLayout.GRID,
        direction = ScrollerDirection.Horizontal,
        scrollbars = scrollbars,
        bufferPages = bufferPages,
        placeholderBody = placeholderBody,
        itemBody = itemBody
    )
}

@Composable
fun <T> VerticalScroller(
    items: List<T>,
    scrollbars: Boolean = true,
    bufferPages: Int = DEFAULT_BUFFER_PAGES,
    placeholderBody: (@Composable () -> Unit)? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    itemBody: @Composable (T) -> Unit,
) {
    VirtualScroller(
        items = items,
        attrs = attrs,
        layout = ScrollerLayout.LINEAR,
        direction = ScrollerDirection.Vertical,
        scrollbars = scrollbars,
        bufferPages = bufferPages,
        placeholderBody = placeholderBody,
        itemBody = itemBody
    )
}

@Composable
fun <T> HorizontalScroller(
    items: List<T>,
    scrollbars: Boolean = true,
    bufferPages: Int = DEFAULT_BUFFER_PAGES,
    placeholderBody: (@Composable () -> Unit)? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    itemBody: @Composable (T) -> Unit,
) {
    VirtualScroller(
        items = items,
        attrs = attrs,
        layout = ScrollerLayout.LINEAR,
        direction = ScrollerDirection.Horizontal,
        scrollbars = scrollbars,
        bufferPages = bufferPages,
        placeholderBody = placeholderBody,
        itemBody = itemBody
    )
}

@Immutable
private class VirtualScrollerImpl<T>(
    private val layout: ScrollerLayout,
    private val direction: ScrollerDirection,
    private val bufferPages: Int,
) {

    val instanceId = Random.nextLong().absoluteValue
    val parentId = "vs-parent-$instanceId"
    val containerId = "vs-container-$instanceId"
    val placeHolderId = "vs-placeholder-$instanceId"
    val itemSizeWH = mutableStateOf(0 to 0)
    val viewportWH = mutableStateOf(0 to 0)
    val scrollOffsetXY = mutableStateOf(0 to 0)
    val bufferedItemStartIndex = mutableStateOf(0)
    val renderItemStartIndex = derivedStateOf {
        val (_, _) = scrollOffsetXY.value
        val (_, _) = itemSizeWH.value
        val (_, _) = viewportWH.value
        getRenderStartIndex()
    }

    // All State holders, inactive or cached, are stored here
    val stateHolders = mutableStateListOf<ItemStateHolder<T>>()

    // Holders with an item are associated by the item here
    private val boundHolders = mutableMapOf<T, ItemStateHolder<T>>()

    // Any unused holders are kept here, waiting for a new item
    private val cachedHolders = ArrayDeque<ItemStateHolder<T>>()

    fun bindHolder(item: T, top: Int, left: Int) {
        // Find the active holder unless the content is unchanged,
        // or pull one from the cache, otherwise create a new one.
        val currentBinding = boundHolders[item]
        val binding = if (currentBinding == null) {
            val cachedHolder = cachedHolders.removeFirstOrNull()
            if (cachedHolder == null) {
                val newHolder = ItemStateHolder(item, top, left)
                boundHolders[item] = newHolder
                stateHolders.add(newHolder)
                return
            } else {
                boundHolders[item] = cachedHolder
                cachedHolder
            }
        } else {
            currentBinding
        }
        binding.state.value = item
        binding.top.value = top
        binding.left.value = left
    }

    fun cacheUnusedStateHolders(itemSlice: List<List<T>>) {
        // When the list of items to display changes, unbind any unused holders,
        // returning them to the cache up to the max cache size.
        (boundHolders.keys - itemSlice.flatten().toSet())
            .mapNotNull(boundHolders::remove)
            .forEach { holder ->
                val remainingCacheSize = HOLDER_POOL_SIZE - cachedHolders.size
                if (remainingCacheSize > 0) {
                    // return to cache
                    holder.state.value = null
                    cachedHolders.add(holder)
                } else {
                    // no room in cache, dispose
                    holder.state.value = null
                    stateHolders.remove(holder)
                }
            }
    }

    fun calculateContainerSize(itemCount: Int): Pair<Int, Int> {
        val (itemWidth, itemHeight) = itemSizeWH.value
        val (viewportWidth, viewportHeight) = viewportWH.value
        return when (layout) {
            ScrollerLayout.GRID -> {
                when (direction) {
                    ScrollerDirection.Vertical -> {
                        val visibleXCount = (viewportWidth / itemWidth)
                        val visibleYCount = ceil(itemCount / visibleXCount.toFloat()).toInt()
                        (visibleXCount * itemWidth to visibleYCount * itemHeight)
                    }

                    ScrollerDirection.Horizontal -> {
                        val visibleYCount = (viewportHeight / itemHeight)
                        val visibleXCount = ceil(itemCount / visibleYCount.toFloat()).toInt()
                        (visibleXCount * itemWidth to visibleYCount * itemHeight)
                    }
                }
            }

            ScrollerLayout.LINEAR -> {
                when (direction) {
                    ScrollerDirection.Vertical -> itemWidth to (itemCount * itemHeight)
                    ScrollerDirection.Horizontal -> (itemCount * itemWidth) to itemHeight
                }
            }
        }
    }

    /**
     * Produce the first item index to display given the current container
     * size, item size, and scroll offset.
     */
    fun getRenderStartIndex(): Int {
        val (scrollOffsetX, scrollOffsetY) = scrollOffsetXY.value
        val (itemWidth, itemHeight) = itemSizeWH.value
        val (viewportWidth, viewportHeight) = viewportWH.value
        return when (layout) {
            ScrollerLayout.GRID -> {
                when (direction) {
                    ScrollerDirection.Vertical -> {
                        val verticalI = scrollOffsetY / itemHeight
                        verticalI * (viewportWidth / itemWidth)
                    }

                    ScrollerDirection.Horizontal -> {
                        val horizontalI = scrollOffsetX / itemWidth
                        horizontalI * (viewportHeight / itemHeight)
                    }
                }
            }

            ScrollerLayout.LINEAR -> {
                when (direction) {
                    ScrollerDirection.Vertical -> scrollOffsetY / itemHeight
                    ScrollerDirection.Horizontal -> scrollOffsetX / itemWidth
                }
            }
        }
    }

    fun getItemSlice(items: List<T>): List<List<T>> {
        val (viewportWidth, viewportHeight) = viewportWH.value
        val (itemWidth, itemHeight) = itemSizeWH.value
        return when (layout) {
            ScrollerLayout.LINEAR -> {
                when (direction) {
                    ScrollerDirection.Vertical -> getLinearSlice(viewportHeight, itemHeight, items)
                    ScrollerDirection.Horizontal -> getLinearSlice(viewportWidth, itemWidth, items)
                }
            }

            ScrollerLayout.GRID -> {
                when (direction) {
                    ScrollerDirection.Vertical -> {
                        getGridSlice(viewportWidth, itemWidth, viewportHeight, itemHeight, items)
                    }

                    ScrollerDirection.Horizontal -> {
                        getGridSlice(viewportHeight, itemHeight, viewportWidth, itemWidth, items)
                    }
                }
            }
        }
    }

    private fun getGridSlice(
        viewportWidth: Int,
        itemWidth: Int,
        viewportHeight: Int,
        itemHeight: Int,
        items: List<T>
    ): List<List<T>> {
        val itemsPerRow = viewportWidth / itemWidth
        val totalYCount = viewportHeight / itemHeight.toFloat()
        val remainder = if (totalYCount % 1 > 0) 1 else 0
        val count = itemsPerRow * (totalYCount.toInt() + remainder)
        val buffer = bufferPages * itemsPerRow
        val startIndex = (renderItemStartIndex.value - buffer).coerceIn(0..items.lastIndex)
        val finalIndex = (renderItemStartIndex.value + count + buffer).coerceAtMost(items.size)
        bufferedItemStartIndex.value = startIndex / itemsPerRow
        return items
            .subList(startIndex, finalIndex)
            .takeIf { count > 0 }
            ?.chunked(itemsPerRow)
            .orEmpty()
    }

    private fun getLinearSlice(
        viewportSize: Int,
        itemSize: Int,
        items: List<T>
    ): List<List<T>> {
        val total = viewportSize / itemSize.toFloat()
        val remainder = if (total % 1 > 0) 1 else 0
        val count = total.toInt() + remainder
        val buffer = (bufferPages * total).toInt()
        bufferedItemStartIndex.value = (renderItemStartIndex.value - buffer).coerceIn(0..items.lastIndex)
        val startIndex = bufferedItemStartIndex.value
        val finalIndex = (renderItemStartIndex.value + count + buffer).coerceIn(0, items.size)
        return listOf(items.subList(startIndex, finalIndex))
    }
}

@Composable
private fun <T> VirtualScroller(
    items: List<T>,
    scrollbars: Boolean = true,
    bufferPages: Int = DEFAULT_BUFFER_PAGES,
    direction: ScrollerDirection = ScrollerDirection.Vertical,
    layout: ScrollerLayout = ScrollerLayout.GRID,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    placeholderBody: (@Composable () -> Unit)? = null,
    itemBody: @Composable (T) -> Unit,
) {
    val scroller = remember { VirtualScrollerImpl<T>(layout, direction, bufferPages) }

    Div(
        {
            id(scroller.parentId)
            attrs?.invoke(this)
            style {
                position(Position.Relative)
                when (direction) {
                    ScrollerDirection.Vertical -> {
                        overflowY("scroll")
                        overflowX("hidden")
                    }

                    ScrollerDirection.Horizontal -> {
                        overflowX("scroll")
                        overflowY("hidden")
                    }
                }
                if (!scrollbars) {
                    property("scrollbar-width", "none")
                }
            }
            onScroll { event ->
                val element = (event.target as HTMLDivElement)
                val top = element.scrollTop.toInt()
                val left = element.scrollLeft.toInt()
                val newOffset = left to top
                if (scroller.scrollOffsetXY.value != newOffset) {
                    scroller.scrollOffsetXY.value = newOffset
                }
            }
        },
    ) {
        ObserverResize(
            id = scroller.parentId,
            onDisposed = {
                scroller.viewportWH.value = 0 to 0
                scroller.scrollOffsetXY.value = 0 to 0
            },
        ) { entry ->
            val (viewportWidth, viewportHeight) = scroller.viewportWH.value
            val newWidth = entry.contentRect.width.toInt()
            val newHeight = entry.contentRect.height.toInt()

            if (newWidth != viewportWidth || newHeight != viewportHeight) {
                scroller.viewportWH.value = newWidth to newHeight
            }
        }
        Div(
            {
                id(scroller.placeHolderId)
                style {
                    position(Position.Absolute)
                    opacity(0)
                    property("z-index", -100)
                    property("pointer-events", "none")
                }
            },
        ) {
            if (placeholderBody == null) {
                // Create an invisible dummy item to determine it's size
                val firstItem = remember(items) { items.firstOrNull() }
                firstItem?.also { item -> itemBody(item) }
            } else {
                placeholderBody()
            }
            ObserverResize(scroller.placeHolderId) { entry ->
                val (itemWidth, itemHeight) = scroller.itemSizeWH.value
                val newWidth = entry.contentRect.width.toInt()
                val newHeight = entry.contentRect.height.toInt()

                if (itemWidth != newWidth || itemHeight != newHeight) {
                    scroller.itemSizeWH.value = newWidth to newHeight
                }
            }
        }
        Div(
            {
                id(scroller.containerId)
                style {
                    val (w, h) = scroller.calculateContainerSize(items.size)
                    width(w.px)
                    height(h.px)
                }
            },
        ) {
            scroller.stateHolders.forEach { holder ->
                DrawHolder(
                    holder = holder,
                    itemBody = itemBody,
                )
            }

            Div {
                val itemSlice by remember {
                    derivedStateOf {
                        scroller.renderItemStartIndex.value
                        scroller.viewportWH.value
                        scroller.itemSizeWH.value
                        scroller.getItemSlice(items)
                    }
                }

                // Determine where each item should be and bind a holder
                // to that item and position based on scroll offset.
                val bufferedItemStartIndex by scroller.bufferedItemStartIndex
                LaunchedEffect(itemSlice, scroller.itemSizeWH.value, bufferedItemStartIndex) {
                    scroller.cacheUnusedStateHolders(itemSlice)

                    val (itemWidth, itemHeight) = scroller.itemSizeWH.value
                    when (layout) {
                        ScrollerLayout.GRID -> {
                            when (direction) {
                                ScrollerDirection.Vertical -> {
                                    val base = bufferedItemStartIndex * itemHeight
                                    itemSlice.forEachIndexed { rowI, row ->
                                        val itemTop = base + (rowI * itemHeight)
                                        row.forEachIndexed { columnI, item ->
                                            val itemLeft = (columnI * itemWidth)
                                            scroller.bindHolder(item, itemTop, itemLeft)
                                        }
                                    }
                                }

                                ScrollerDirection.Horizontal -> {
                                    val base = bufferedItemStartIndex * itemWidth
                                    itemSlice.forEachIndexed { columnI, row ->
                                        val itemLeft = base + (columnI * itemWidth)
                                        row.forEachIndexed { rowI, item ->
                                            val itemTop = (rowI * itemHeight)
                                            scroller.bindHolder(item, itemTop, itemLeft)
                                        }
                                    }
                                }
                            }
                        }

                        ScrollerLayout.LINEAR -> {
                            val itemList = itemSlice.firstOrNull().orEmpty()
                            when (direction) {
                                ScrollerDirection.Vertical -> {
                                    val base = (bufferedItemStartIndex * itemHeight)
                                    itemList.forEachIndexed { index, item ->
                                        val itemTop = base + (index * itemHeight)
                                        scroller.bindHolder(item, itemTop, 0)
                                    }
                                }

                                ScrollerDirection.Horizontal -> {
                                    val base = (bufferedItemStartIndex * itemWidth)
                                    itemList.forEachIndexed { index, item ->
                                        val itemLeft = base + (index * itemWidth)
                                        scroller.bindHolder(item, 0, itemLeft)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeWebApi::class)
@Composable
private fun <T> DrawHolder(
    holder: ItemStateHolder<T>,
    itemBody: @Composable (T) -> Unit,
) {
    val initial = remember { holder.state.value!! }
    var isCached by remember { mutableStateOf(false) }
    Div(
        {
            style {
                position(Position.Absolute)
                transform {
                    translate(holder.left.value.px, holder.top.value.px)
                }
                property("will-change", "transform")
                // Hide cached item
                if (isCached) {
                    visibility(VisibilityStyle.Hidden)
                    property("z-index", -100)
                    property("pointer-events", "none")
                }
            }
        },
    ) {
        val currentItem by produceState(initial, holder.state.value) {
            val newHolderValue = holder.state.value
            if (newHolderValue != null && value != newHolderValue) {
                isCached = true
                awaitAnimationFrame()
                value = newHolderValue
                awaitAnimationFrame()
                isCached = false
            } else {
                value = newHolderValue ?: value
                awaitAnimationFrame()
                isCached = newHolderValue == null
            }
        }
        CompositionLocalProvider(
            LocalInVirtualScrollCache provides isCached
        ) {
            itemBody(currentItem)
        }
    }
}

@Composable
private fun ObserverResize(
    id: String,
    onDisposed: () -> Unit = {},
    callback: (ResizeObserverEntry) -> Unit,
) {
    DisposableEffect(id, onDisposed, callback) {
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
