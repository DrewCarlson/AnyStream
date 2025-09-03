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

import anystream.client.createPlatformClientCapabilities
import anystream.client.json
import anystream.models.ClientCapabilities
import anystream.models.PlaybackState
import anystream.models.api.PlaybackSessions
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.URLBuilder
import io.ktor.http.path
import io.ktor.utils.io.CancellationException
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

class StreamApiClient(
    private val core: AnyStreamApiCore,
) {

    suspend fun getStreams(): PlaybackSessions {
        return core.http.get("/api/stream").bodyOrThrow()
    }

    fun playbackSession(
        scope: CoroutineScope,
        mediaLinkId: String,
        onClosed: () -> Unit = {},
        init: (state: PlaybackState) -> Unit,
    ): PlaybackSessionHandle {
        val currentState = MutableStateFlow<PlaybackState?>(null)
        val progressFlow = MutableSharedFlow<Duration>(0, 1, BufferOverflow.DROP_OLDEST)
        val clientCapabilities = createPlatformClientCapabilities()
        val job = scope.launch {
            try {
                core.http.wss(path = "/api/ws/stream/$mediaLinkId/state") {
                    send( core.sessionManager.fetchToken()!!)

                    sendSerialized(clientCapabilities)

                    val playbackState = receiveDeserialized<PlaybackState>()
                    currentState.value = playbackState
                    init(playbackState)
                    progressFlow
                        .sample(5000)
                        .distinctUntilChanged()
                        .collect { progress ->
                            currentState.update { it?.copy(position = progress) }
                            sendSerialized(currentState.value!!)
                        }
                }
            } catch (e: Throwable) {
                // TODO: this will need to signal the consumer so it can:
                //   - display error message to user
                //   - enter idle state and prepare for new playback
                if (e !is CancellationException) {
                    println("Playback session failed: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        job.invokeOnCompletion { onClosed() }
        return PlaybackSessionHandle(
            update = progressFlow,
            cancel = { job.cancel() },
            initialPlaybackState = scope.async { currentState.filterNotNull().first() },
            playbackUrl = scope.async {
                currentState
                    .filterNotNull()
                    .map { createHlsStreamUrl(it.mediaLinkId, it.id, clientCapabilities) }
                    .first()
            },
        )
    }


    fun createHlsStreamUrl(
        mediaLinkId: String,
        token: String,
        clientCapabilities: ClientCapabilities? = createPlatformClientCapabilities()
    ): String {
        return URLBuilder( core.serverUrl.orEmpty()).apply {
            path("api", "stream", mediaLinkId, "hls", "playlist.m3u8")
            parameters["token"] = token

            if (clientCapabilities != null) {
                parameters["capabilities"] = json.encodeToString(clientCapabilities)
            }
        }.buildString()
    }

    suspend fun stopStreamSession(id: String): Boolean {
        val result =  core.http.delete("/api/stream/stop/$id")
        return result.status == OK
    }
}


class PlaybackSessionHandle(
    val initialPlaybackState: Deferred<PlaybackState>,
    val playbackUrl: Deferred<String>,
    val update: MutableSharedFlow<Duration>,
    val cancel: () -> Unit,
)
