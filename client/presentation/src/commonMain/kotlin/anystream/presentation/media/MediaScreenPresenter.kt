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
package anystream.presentation.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import anystream.client.AnyStreamClient
import anystream.presentation.core.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException

data class MediaScreenProps(
    val mediaId: String,
)

@SingleIn(AppScope::class)
@Inject
class MediaScreenPresenter(
    private val client: AnyStreamClient,
) : Presenter<MediaScreenProps, MediaScreenModel> {
    @Composable
    override fun model(props: MediaScreenProps): MediaScreenModel {
        val state by produceState<MediaScreenModel>(MediaScreenModel.Loading, props.mediaId) {
            value = try {
                val response = client.library.lookupMedia(props.mediaId)
                MediaScreenModel.Loaded(response)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                MediaScreenModel.LoadingFailed
            }
        }
        return state
    }
}
