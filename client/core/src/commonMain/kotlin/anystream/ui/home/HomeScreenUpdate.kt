/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.ui.home

import kt.mobius.Next
import kt.mobius.Next.Companion.next
import kt.mobius.Update
import kt.mobius.gen.GenerateUpdate
import anystream.ui.home.HomeScreenEffect as Effect
import anystream.ui.home.HomeScreenEvent as Event
import anystream.ui.home.HomeScreenModel as Model

@GenerateUpdate
object HomeScreenUpdate : Update<Model, Event, Effect>, HomeScreenGeneratedUpdate {

    override fun onHomeDataError(model: Model): Next<Model, Effect> {
        return next(Model.LoadingFailed)
    }

    override fun onHomeDataLoaded(
        model: Model,
        event: Event.OnHomeDataLoaded
    ): Next<Model, Effect> {
        return next(
            Model.Loaded(
                currentlyWatching = event.homeDate.currentlyWatching,
                recentlyAdded = event.homeDate.recentlyAdded,
                popular = event.homeDate.popular,
            ),
        )
    }
}
