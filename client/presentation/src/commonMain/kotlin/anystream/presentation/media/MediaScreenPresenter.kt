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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import anystream.client.AnyStreamClient
import anystream.models.Descriptor
import anystream.models.Permission
import anystream.models.api.MediaLookupResponse
import anystream.presentation.core.Presenter
import anystream.presentation.model.MenuOptionModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

data class MediaScreenProps(
    val mediaId: String,
)

@SingleIn(AppScope::class)
@Inject
class MediaScreenPresenter(
    private val client: AnyStreamClient,
) : Presenter<MediaScreenProps, MediaScreenModel> {
    private sealed class DataState {
        data object Loading : DataState()

        data class Loaded(
            val response: MediaLookupResponse,
        ) : DataState()

        data object LoadingFailed : DataState()
    }

    @Composable
    override fun model(props: MediaScreenProps): MediaScreenModel {
        val state by produceState<DataState>(DataState.Loading, props.mediaId) {
            value = try {
                val response = client.library.lookupMedia(props.mediaId)
                DataState.Loaded(response)
            } catch (_: Throwable) {
                DataState.LoadingFailed
            }
        }

        return when (val state = state) {
            is DataState.Loaded -> produceLoadedState(state.response)
            DataState.Loading -> MediaScreenModel.Loading
            DataState.LoadingFailed -> MediaScreenModel.LoadingFailed
        }
    }

    @Composable
    fun produceLoadedState(response: MediaLookupResponse): MediaScreenModel.Loaded {
        val scope = rememberCoroutineScope()
        val permissions by remember { client.user.permissions.filterNotNull() }
            .collectAsState(emptySet())
        val hasManagePermission = remember(permissions) {
            client.user.hasPermission(Permission.ManageCollection)
        }
        val menuOptions = if (hasManagePermission) {
            produceViewMenuOptions() + produceManageMenuOptions(response, client, scope)
        } else {
            produceViewMenuOptions()
        }
        println(permissions)
        return MediaScreenModel.Loaded(
            response = response,
            menuOptions = menuOptions,
        )
    }

    @Composable
    fun produceViewMenuOptions(): List<MenuOptionModel> {
        return emptyList()
    }

    @Composable
    fun produceManageMenuOptions(
        response: MediaLookupResponse,
        client: AnyStreamClient,
        scope: CoroutineScope,
    ): List<MenuOptionModel> {
        return listOfNotNull(
            MenuOptionModel(
                label = "Analyze Files",
                onClick = {
                    scope.launch {
                        response.mediaLinks.forEach { link ->
                            client.library.analyzeMediaLinksAsync(link.id)
                        }
                    }
                },
            ),
            MenuOptionModel(
                label = "Fix Match",
                onClick = {},
            ),
            MenuOptionModel(
                label = "Generate Preview",
                onClick = {
                    scope.launch {
                        response.mediaLinks
                            .filter { it.descriptor == Descriptor.VIDEO }
                            .forEach { link ->
                                client.library.generatePreview(link.id)
                            }
                    }
                },
            ),
        )
    }
}
