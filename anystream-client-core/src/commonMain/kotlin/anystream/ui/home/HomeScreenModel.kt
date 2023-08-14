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

import anystream.models.MediaLink
import anystream.models.Movie
import anystream.models.api.HomeResponse

data class HomeScreenModel(
    val homeResponse: LoadableDataState<HomeResponse> = LoadableDataState.Loading,
) {
    val popular: List<Pair<Movie, MediaLink?>>
        get() = homeResponse.dataOrNull?.popular?.movies?.toList()?.take(7).orEmpty()
}

sealed class LoadableDataState<out T> {
    object Loading : LoadableDataState<Nothing>()
    object Empty : LoadableDataState<Nothing>()
    object Error : LoadableDataState<Nothing>()

    data class Loaded<out T>(val data: T) : LoadableDataState<T>()

    val dataOrNull: T?
        get() = (this as? Loaded<T>)?.data

    val isLoading: Boolean get() = this is Loading
    val isError: Boolean get() = this is Error
    val isEmpty: Boolean get() = this is Empty
}
