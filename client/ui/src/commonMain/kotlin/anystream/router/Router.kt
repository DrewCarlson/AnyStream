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
package anystream.router

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

private fun key(backStackIndex: Int) =
    "K$backStackIndex"

// TODO: Bind root stores to app provided scope
private val routeViewModelStores = mutableMapOf<String, ViewModelStore>()
private val backStackMap: MutableMap<Any, BackStack<*>> =
    mutableMapOf()

val LocalRouting: ProvidableCompositionLocal<List<Any>> = compositionLocalOf {
    listOf<Any>()
}

private class RouteViewModelStoreOwner(private val store: ViewModelStore) : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore get() = store
}

@Composable
internal fun <T> Router(
    contextId: String,
    defaultRouting: T,
    children: @Composable (BackStack<T>) -> Unit,
) {
    val route = LocalRouting.current

    @Suppress("UNCHECKED_CAST")
    val routingFromAmbient = route.firstOrNull() as? T
    val downStreamRoute = if (route.size > 1) route.takeLast(route.size - 1) else emptyList()

    val upstreamHandler = LocalBackPressHandler.current
    val localHandler = remember { BackPressHandler("${upstreamHandler.id}.$contextId") }
    val backStack = fetchBackStack(localHandler.id, defaultRouting, routingFromAmbient)
    val handleBackPressHere: () -> Boolean = { localHandler.handle() || backStack.pop() }

    SideEffect {
        upstreamHandler.children.add(handleBackPressHere)
    }
    DisposableEffect(Unit) {
        onDispose {
            upstreamHandler.children.remove(handleBackPressHere)
        }
    }

    @Composable
    fun Observe(body: @Composable () -> Unit) = body()
    val store = routeViewModelStores.getOrPut(key(backStack.lastIndex)) { ViewModelStore() }
    val owner = remember(store) { RouteViewModelStoreOwner(store) }

    Observe {
        // Not recomposing router on backstack operation
        BundleScope(key(backStack.lastIndex), autoDispose = false) {
            CompositionLocalProvider(
                LocalBackPressHandler provides localHandler,
                LocalRouting provides downStreamRoute,
                LocalViewModelStoreOwner provides owner,
            ) {
                children(backStack)
            }
        }
    }
}

@Composable
private fun <T> fetchBackStack(key: String, defaultElement: T, override: T?): BackStack<T> {
    val upstreamBundle = LocalSavedInstanceState.current
    val onElementRemoved: (Int) -> Unit = {
        routeViewModelStores.remove(key(it))?.clear()
        upstreamBundle.remove(key(it))
    }

    @Suppress("UNCHECKED_CAST")
    val existing = backStackMap[key] as BackStack<T>?
    @Suppress("UNCHECKED_CAST")
    return when {
        override != null -> BackStack(override as T, onElementRemoved)
        existing != null -> existing
        else -> BackStack(defaultElement, onElementRemoved)
    }.also {
        backStackMap[key] = it
    }
}
