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
package anystream.routing

import anystream.routing.CommonRouter
import anystream.routing.Routes
import app.softwork.routingcompose.Router

// TODO: BrowserRouter is not customizable and can only push routes,
//  a custom Router utilizing window.history.state data can emulate
//  a full BackStack.
class WebRouter(private val router: Router) : CommonRouter {
    override fun replaceTop(route: Routes) {
        router.navigate("/${route.path}")
    }

    override fun pushRoute(route: Routes) {
        router.navigate("/${route.path}")
    }

    override fun replaceStack(routes: List<Routes>) {
        TODO("Not yet implemented")
    }

    override fun popCurrentRoute() {
        TODO("Not yet implemented")
    }
}
