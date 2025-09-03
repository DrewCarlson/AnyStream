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

import anystream.torrent.search.TorrentDescription2
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import qbittorrent.models.GlobalTransferInfo
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentFile

class TorrentsApiClient(
    private val core: AnyStreamApiCore,
) {

    suspend fun getGlobalTransferInfo(): GlobalTransferInfo =
        core.http.get("/api/torrents/global").bodyOrThrow()

    suspend fun getTorrents(): List<Torrent> =
        core.http.get("/api/torrents").body()

    suspend fun getTorrentFiles(hash: String): List<TorrentFile> =
        core.http.get("/api/torrents/$hash/files").bodyOrThrow()

    suspend fun resumeTorrent(hash: String) {
        core.http.get("/api/torrents/$hash/resume").orThrow()
    }

    suspend fun pauseTorrent(hash: String) {
        core.http.get("/api/torrents/$hash/pause").orThrow()
    }

    suspend fun deleteTorrent(hash: String, deleteFiles: Boolean = false) {
        core.http.delete("/api/torrents/$hash") {
            parameter("deleteFiles", deleteFiles)
        }.orThrow()
    }

    suspend fun downloadTorrent(description: TorrentDescription2, movieId: String?) {
        core.http.post("/api/torrents") {
            contentType(ContentType.Application.Json)
            setBody(description)

            movieId?.let { parameter("movieId", it) }
        }.orThrow()
    }

    fun torrentListChanges(): Flow<List<String>> = callbackFlow {
        core.http.wss(path = "/api/ws/torrents/observe") {
            send(core.sessionManager.fetchToken()!!)
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    if (message.isNotBlank()) {
                        trySend(message.split(","))
                    }
                }
            }
            awaitClose()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun globalInfoChanges(): Flow<GlobalTransferInfo> = callbackFlow {
        core.http.wss(path = "/api/ws/torrents/global") {
            send(core.sessionManager.fetchToken()!!)
            while (!incoming.isClosedForReceive) {
                try {
                    trySend(receiveDeserialized())
                } catch (e: WebsocketDeserializeException) {
                    // ignored
                }
            }
            awaitClose()
        }
    }

}