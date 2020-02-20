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
package anystream.android.router

import android.os.Bundle
import androidx.compose.runtime.*

private val rootSavedInstanceState = Bundle()

val LocalSavedInstanceState: ProvidableCompositionLocal<Bundle> = compositionLocalOf { rootSavedInstanceState }

internal const val BUNDLE_KEY = "LocalSavedInstanceState"

fun Bundle.saveLocal() {
    putBundle(BUNDLE_KEY, rootSavedInstanceState)
}

@Composable
fun persistentInt(key: String, defaultValue: Int = 0): MutableState<Int> {
    val bundle = LocalSavedInstanceState.current

    val state: MutableState<Int> = remember {
        mutableStateOf(
            bundle.getInt(key, defaultValue)
        )
    }

    saveInt(key, state.value)

    return state
}

@Composable
private fun saveInt(key: String, value: Int) {
    val bundle = LocalSavedInstanceState.current
    bundle.putInt(key, value)
}


@Composable
fun BundleScope(
    savedInstanceState: Bundle?,
    children: @Composable (bundle: Bundle) -> Unit
) {
    BundleScope(BUNDLE_KEY, savedInstanceState ?: Bundle(), true, children)
}

@Composable
fun BundleScope(
    key: String,
    children: @Composable (bundle: Bundle) -> Unit
) {
    BundleScope(key, Bundle(), true, children)
}

@Composable
fun BundleScope(
    key: String,
    defaultBundle: Bundle = Bundle(),
    autoDispose: Boolean = true,
    children: @Composable (Bundle) -> Unit
) {
    val upstream = LocalSavedInstanceState.current
    val downstream = upstream.getBundle(key) ?: defaultBundle

    SideEffect {
        upstream.putBundle(key, downstream)
    }
    if (autoDispose) {
        DisposableEffect(Unit) { onDispose { upstream.remove(key) } }
    }

    CompositionLocalProvider(LocalSavedInstanceState provides downstream) {
        children(downstream)
    }
}