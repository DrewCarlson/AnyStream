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
package anystream.presentation.app

import androidx.compose.runtime.mutableStateListOf
import anystream.routing.CommonRouter
import anystream.routing.Routes

internal class ComposeRouter(
    initialRoute: Routes,
) : CommonRouter {
    val stack = mutableStateListOf(initialRoute)

    override fun replaceTop(route: Routes) {
        stack[stack.lastIndex] = route
    }

    override fun pushRoute(route: Routes) {
        stack.add(route)
    }

    override fun replaceStack(routes: List<Routes>) {
        stack.clear()
        stack.addAll(routes)
    }

    override fun popCurrentRoute(): Boolean {
        return if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            true
        } else {
            false
        }
    }
}
