/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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
package anystream.di

import anystream.client.AnyStreamClient
import anystream.client.CoreBindings
import anystream.presentation.app.AppPresenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph

@DependencyGraph(
    AppScope::class,
    bindingContainers = [CoreBindings::class],
)
interface JsAppGraph {
    val client: AnyStreamClient
    val appPresenter: AppPresenter

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): JsAppGraph
    }
}
