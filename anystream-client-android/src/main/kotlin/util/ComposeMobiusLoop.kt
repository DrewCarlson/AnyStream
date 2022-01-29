/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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
package anystream.android.util

import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kt.mobius.Init
import kt.mobius.MobiusLoop
import kt.mobius.android.MobiusLoopViewModel
import kt.mobius.functions.Consumer

@Composable
fun <M, E, F> createLoopController(
    initialModel: M,
    init: Init<M, F>,
    loopBuilder: () -> MobiusLoop.Factory<M, E, F>,
): Pair<State<M>, Consumer<E>> {
    val factory = remember {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MobiusLoopViewModel.create<M, E, F, Nothing>({ _, _ -> loopBuilder() }, initialModel, init) as T
            }
        }
    }
    val loopVm = viewModel<MobiusLoopViewModel<M, E, F, Nothing>>(factory = factory)

    return remember(loopVm) {
        Pair(
            loopVm.models.observeAsState(loopVm.model),
            Consumer(loopVm::dispatchEvent)
        )
    }
}
