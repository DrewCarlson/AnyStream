/**
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
package anystream.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import anystream.client.AnyStreamClient
import anystream.presentation.core.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException

data object HomeScreenProps

@SingleIn(AppScope::class)
@Inject
class HomeScreenPresenter(
    private val client: AnyStreamClient,
) : Presenter<HomeScreenProps, HomeScreenModel> {
    @Composable
    override fun model(props: HomeScreenProps): HomeScreenModel {
        val state by produceState<HomeScreenModel>(HomeScreenModel.Loading) {
            value = try {
                val homeData = client.library.getHomeData()
                val libraries = client.library.getLibraries()
                HomeScreenModel.Loaded(
                    libraries = libraries,
                    currentlyWatching = homeData.currentlyWatching,
                    recentlyAdded = homeData.recentlyAdded,
                    popular = homeData.popular,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                HomeScreenModel.LoadingFailed
            }
        }
        return state
    }
}
