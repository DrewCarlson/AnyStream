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
package anystream.presentation.home

import anystream.client.AnyStreamClient
import kt.mobius.flow.FlowTransformer
import kt.mobius.flow.subtypeEffectHandler
import anystream.presentation.home.HomeScreenEffect as Effect
import anystream.presentation.home.HomeScreenEvent as Event

class HomeScreenHandler(
    client: AnyStreamClient,
) : FlowTransformer<Effect, Event> by subtypeEffectHandler({

    addFunction<Effect.LoadHomeData> {
        try {
            Event.OnHomeDataLoaded(
                homeData = client.library.getHomeData(),
                libraries = client.library.getLibraries(),
            )
        } catch (e: Throwable) {
            Event.OnHomeDataError
        }
    }
})
