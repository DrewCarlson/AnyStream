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
package anystream.android.router

import anystream.routing.CommonRouter
import anystream.routing.Routes

class AndroidRouter : CommonRouter {

    private var backStack: BackStack<Routes>? = null

    fun setBackStack(backStack: BackStack<Routes>?) {
        this.backStack = backStack
    }

    override fun popCurrentRoute() {
        backStack?.pop()
    }

    override fun pushRoute(route: Routes) {
        backStack?.push(route)
    }

    override fun replaceStack(routes: List<Routes>) {
        backStack?.apply {
            newRoot(routes.first())
            if (routes.size > 1) {
                routes.drop(1).forEach(::push)
            }
        }
    }

    override fun replaceTop(route: Routes) {
        backStack?.replace(route)
    }
}
