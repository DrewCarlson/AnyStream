/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.client.api

import anystream.models.api.LibraryActivity
import anystream.models.api.PlaybackSessions
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.wss
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AdminApiClient(
    private val core: AnyStreamApiCore,
) {

    private val playbackSessionsFlow by lazy {
        createWsStateFlow("/api/ws/admin/sessions", PlaybackSessions())
    }
    private val libraryActivityFlow by lazy {
        createWsStateFlow("/api/ws/admin/activity", LibraryActivity())
    }

    val playbackSessions: StateFlow<PlaybackSessions>
        get() = playbackSessionsFlow
    val libraryActivity: StateFlow<LibraryActivity>
        get() = libraryActivityFlow

    fun observeLogs(): Flow<String> = callbackFlow {
        core.http.wss(path = "/api/ws/admin/logs") {
            send(core.sessionManager.fetchToken()!!)
            for (frame in incoming) {
                if (frame is Frame.Text) trySend(frame.readText())
            }
            awaitClose()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private inline fun <reified T> createWsStateFlow(path: String, default: T): StateFlow<T> {
        return callbackFlow<T> {
            launch {
                try {
                    core.http.wss(path = path) {
                        send(core.sessionManager.fetchToken()!!)
                        while (!incoming.isClosedForReceive && isActive) {
                            try {
                                trySend(receiveDeserialized())
                            } catch (e: WebsocketDeserializeException) {
                                // ignored
                            } catch (e: ClosedReceiveChannelException) {
                                // ignored
                            }
                        }
                    }
                } catch (e: WebSocketException) {
                    // ignored
                }
            }
            awaitClose()
        }.stateIn(core.scope, SharingStarted.WhileSubscribed(), default)
    }
}