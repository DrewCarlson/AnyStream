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
package anystream.presentation.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import anystream.client.AnyStreamClient
import anystream.models.MediaKind
import anystream.models.toMediaItems
import anystream.presentation.core.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException

data class LibraryScreenProps(
    val libraryId: String,
)

@SingleIn(AppScope::class)
@Inject
class LibraryScreenPresenter(
    private val client: AnyStreamClient,
) : Presenter<LibraryScreenProps, LibraryScreenModel> {
    @Composable
    override fun model(props: LibraryScreenProps): LibraryScreenModel {
        val libraries by client.library.libraries.collectAsState()
        val library = libraries.firstOrNull { it.id == props.libraryId }
            ?: return LibraryScreenModel.NotFound

        val state by produceState<LibraryScreenModel>(LibraryScreenModel.Loading, library) {
            value = try {
                val items = when (library.mediaKind) {
                    MediaKind.MOVIE -> client.library.getMovies(library.id).toMediaItems()
                    MediaKind.TV -> client.library.getTvShows(library.id).toMediaItems()
                    else -> null
                }
                if (items != null) {
                    LibraryScreenModel.Loaded(library, items)
                } else {
                    LibraryScreenModel.LoadingFailed
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                LibraryScreenModel.LoadingFailed
            }
        }
        return state
    }
}
