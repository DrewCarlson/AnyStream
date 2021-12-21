/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.client

import anystream.models.*
import anystream.models.api.*
import anystream.torrent.search.TorrentDescription2
import drewcarlson.qbittorrent.models.GlobalTransferInfo
import drewcarlson.qbittorrent.models.Torrent
import drewcarlson.qbittorrent.models.TorrentFile
import io.ktor.client.HttpClient
import io.ktor.client.features.*
import io.ktor.client.features.cookies.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PAGE = "page"
private const val QUERY = "query"

private val json = Json {
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "__type"
    allowStructuredMapKeys = true
    useAlternativeNames = false
}

private const val SESSION_HEADER = "as_user_session"

class AnyStreamClient(
    /** The AnyStream server URL, ex. `http://localhost:3000`. */
    private val serverUrl: String,
    http: HttpClient = HttpClient(),
    private val sessionManager: SessionManager = SessionManager(SessionDataStore)
) {
    val authenticated: Flow<Boolean> = sessionManager.tokenFlow.map { it != null }
    val permissions: Flow<Set<String>?> = sessionManager.permissionsFlow
    val user: Flow<User?> = sessionManager.userFlow

    private val http = http.config {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        Json {
            serializer = KotlinxSerializer(json)
        }
        defaultRequest {
            val configuredProtocol = URLProtocol.createOrDefault(serverUrl.substringBefore("://"))
            val configuredWssProtocol = if (configuredProtocol.isSecure()) URLProtocol.WSS else URLProtocol.WS
            url {
                host = serverUrl.substringAfter("://").substringBefore(':')
                port = serverUrl.substringAfterLast(':').takeWhile { it != '/' }.toIntOrNull() ?: 0
                protocol = if (protocol.isWebsocket()) configuredWssProtocol else configuredProtocol
            }
        }
        WebSockets { }
        install("TokenHandler") {
            requestPipeline.intercept(HttpRequestPipeline.Before) {
                sessionManager.fetchToken()?.let { token ->
                    context.header(SESSION_HEADER, token)
                }
            }
            responsePipeline.intercept(HttpResponsePipeline.Receive) {
                context.response.headers[SESSION_HEADER]?.let { token ->
                    if (token != sessionManager.fetchToken()) {
                        sessionManager.writeToken(token)
                    }
                }
                val sentToken = context.request.headers[SESSION_HEADER]
                if (context.response.status == Unauthorized && sentToken != null) {
                    sessionManager.clear()
                }
            }
        }
    }

    fun isAuthenticated(): Boolean {
        return sessionManager.fetchToken() != null
    }

    fun userPermissions(): Set<String> {
        return sessionManager.fetchPermissions() ?: emptySet()
    }

    fun hasPermission(permission: String): Boolean {
        return userPermissions().run { contains(Permissions.GLOBAL) || contains(permission) }
    }

    fun authedUser(): User? {
        return sessionManager.fetchUser()
    }

    fun torrentListChanges(): Flow<List<String>> = callbackFlow {
        http.wss("/api/ws/torrents/observe") {
            incoming.consumeAsFlow()
                .onEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val message = frame.readText()
                            if (message.isNotBlank()) {
                                trySend(message.split(","))
                            }
                        }
                        is Frame.Close -> close()
                        else -> Unit
                    }
                }
                .onStart { send(Frame.Text(sessionManager.fetchToken()!!)) }
                .collect()
        }
        awaitClose()
    }

    fun globalInfoChanges(): Flow<GlobalTransferInfo> = callbackFlow {
        http.wss("/api/ws/torrents/global") {
            incoming.consumeAsFlow()
                .onEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val message = frame.readText()
                            if (message.isNotBlank()) {
                                trySend(json.decodeFromString(message))
                            }
                        }
                        is Frame.Close -> close()
                        else -> Unit
                    }
                }
                .onStart { send(Frame.Text(sessionManager.fetchToken()!!)) }
                .collect()
        }
        awaitClose()
    }

    suspend fun getHomeData() = http.get<HomeResponse>("/api/home")

    suspend fun getMovies(page: Int = 1) =
        http.get<MoviesResponse>("/api/movies") {
            pageParam(page)
        }

    suspend fun getMovieSources(id: String) =
        http.get<List<TorrentDescription2>>("/api/media/movies/$id/sources")

    suspend fun getTvShowSources(id: String) =
        http.get<List<TorrentDescription2>>("/api/media/tv/$id/sources")

    suspend fun getTvShows(page: Int = 1) =
        http.get<TvShowsResponse>("/api/tv") {
            pageParam(page)
        }

    suspend fun getMovie(id: String) =
        http.get<MovieResponse>("/api/movies/$id")

    suspend fun deleteMovie(id: String) =
        http.delete<Unit>("/api/movies/$id")

    suspend fun getMediaRefs() =
        http.get<List<MediaReference>>("/api/media/refs")

    suspend fun getMediaRef(refId: String) =
        http.get<MediaReference>("/api/media/refs/$refId")

    suspend fun getMediaRefsForMovie(movieId: String) =
        http.get<List<MediaReference>>("/api/movies/$movieId/refs")

    suspend fun getMediaRefsForShow(showId: String) =
        http.get<List<MediaReference>>("/api/tv/$showId/refs")

    suspend fun importMedia(importMedia: ImportMedia, importAll: Boolean) {
        http.post<Unit>("/api/media/import") {
            contentType(ContentType.Application.Json)
            parameter("importAll", importAll)
            body = importMedia
        }
    }

    suspend fun unmappedMedia(importMedia: ImportMedia): List<String> {
        return http.post("/api/media/unmapped") {
            contentType(ContentType.Application.Json)
            body = importMedia
        }
    }

    suspend fun refreshMetadata(mediaId: String): MediaLookupResponse {
        return http.get("/api/media/$mediaId/refresh-metadata")
    }

    suspend fun refreshStreamDetails(mediaId: String): List<ImportStreamDetailsResult> {
        return http.get("/api/media/$mediaId/refresh-stream-details")
    }

    suspend fun getTvShow(id: String) =
        http.get<TvShowResponse>("/api/tv/$id")

    suspend fun getEpisodes(showId: String) =
        http.get<EpisodesResponse>("/api/tv/$showId/episodes")

    suspend fun getEpisode(id: String) =
        http.get<EpisodeResponse>("/api/tv/episode/$id")

    suspend fun getSeason(seasonId: String) =
        http.get<SeasonResponse>("/api/tv/season/$seasonId")

    suspend fun lookupMedia(mediaId: String): MediaLookupResponse =
        http.get("/api/media/$mediaId")

    suspend fun lookupMediaByRefId(refId: String): MediaLookupResponse =
        http.get("/api/media/by-ref/$refId")

    suspend fun getTmdbSources(tmdbId: Int) =
        http.get<List<TorrentDescription2>>("/api/media/tmdb/$tmdbId/sources")

    suspend fun getGlobalTransferInfo() =
        http.get<GlobalTransferInfo>("/api/torrents/global")

    suspend fun getTorrents() =
        http.get<List<Torrent>>("/api/torrents")

    suspend fun getTorrentFiles(hash: String) =
        http.get<List<TorrentFile>>("/api/torrents/$hash/files")

    suspend fun resumeTorrent(hash: String) =
        http.get<Unit>("/api/torrents/$hash/resume")

    suspend fun pauseTorrent(hash: String) =
        http.get<Unit>("/api/torrents/$hash/pause")

    suspend fun deleteTorrent(hash: String, deleteFiles: Boolean = false) =
        http.delete<Unit>("/api/torrents/$hash") {
            parameter("deleteFiles", deleteFiles)
        }

    suspend fun downloadTorrent(description: TorrentDescription2, movieId: String?) {
        http.post<Unit>("/api/torrents") {
            contentType(ContentType.Application.Json)
            body = description

            movieId?.let { parameter("movieId", it) }
        }
    }

    suspend fun playbackSession(
        mediaRefId: String,
        init: (state: PlaybackState) -> Unit
    ): PlaybackSessionHandle {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val currentState = MutableStateFlow<PlaybackState?>(null)
        val progressFlow = MutableSharedFlow<Double>(0, 1, BufferOverflow.DROP_OLDEST)
        scope.launch {
            http.wss("/api/ws/stream/$mediaRefId/state") {
                progressFlow
                    .sample(5000)
                    .distinctUntilChanged()
                    .onEach { progress ->
                        if (currentState.value != null) {
                            currentState.update { currentState ->
                                currentState?.copy(position = progress)
                            }
                            send(Frame.Text(json.encodeToString(currentState.value)))
                        }
                    }
                    .launchIn(this)
                incoming.consumeAsFlow()
                    .onEach { frame ->
                        when (frame) {
                            is Frame.Text -> {
                                val message = frame.readText()
                                currentState.value = json.decodeFromString<PlaybackState>(message).also(init)
                            }
                            else -> Unit
                        }
                    }
                    .onStart { send(Frame.Text(sessionManager.fetchToken()!!)) }
                    .collect()
            }
        }
        return PlaybackSessionHandle(
            update = progressFlow,
            cancel = { scope.cancel() }
        )
    }

    suspend fun createUser(
        username: String,
        password: String,
        inviteCode: String?,
        rememberUser: Boolean = true,
    ) = http.post<CreateUserResponse>("/api/users") {
        contentType(ContentType.Application.Json)
        parameter("createSession", rememberUser)
        body = CreateUserBody(username, password, inviteCode)
    }.also { (success, _) ->
        if (rememberUser && success != null) {
            sessionManager.writeUser(success.user)
            sessionManager.writePermissions(success.permissions)
        }
    }

    suspend fun getUser(id: String) =
        http.get<User>("/api/users/$id")

    suspend fun getUsers(): List<User> =
        http.get("/api/users")

    suspend fun updateUser(
        userId: String,
        displayName: String,
        password: String?,
        currentPassword: String?
    ) = http.put<Unit>("/api/users/${userId}") {
        contentType(ContentType.Application.Json)
        body = UpdateUserBody(
            displayName = displayName,
            password = password,
            currentPassword = currentPassword
        )
    }

    suspend fun deleteUser(id: String) =
        http.delete<HttpResponse>("/api/users/$id")

    suspend fun login(username: String, password: String, pairing: Boolean = false): CreateSessionResponse {
        return http.post<CreateSessionResponse>("/api/users/session") {
            contentType(ContentType.Application.Json)
            body = CreateSessionBody(username, password)
        }.also { (success, _) ->
            if (!pairing && success != null) {
                sessionManager.writeUser(success.user)
                sessionManager.writePermissions(success.permissions)
            }
        }
    }

    suspend fun logout() {
        val token = sessionManager.fetchToken() ?: return
        sessionManager.clear()
        http.delete<Unit>("/api/users/session") {
            header(SESSION_HEADER, token)
        }
    }

    suspend fun getInvites(): List<InviteCode> =
        http.get("/api/users/invite")

    suspend fun createInvite(permissions: Set<String>): InviteCode {
        return http.post("/api/users/invite") {
            contentType(ContentType.Application.Json)
            body = permissions
        }
    }

    suspend fun deleteInvite(id: String): Boolean {
        return try {
            http.delete<Unit>("/api/users/invite/$id")
            true
        } catch (e: ClientRequestException) {
            if (e.response.status == NotFound) false else throw e
        }
    }

    suspend fun createPairedSession(pairingCode: String, secret: String): CreateSessionResponse {
        val response = http.post<CreateSessionResponse>("/api/users/session/paired") {
            parameter("pairingCode", pairingCode)
            parameter("secret", secret)
        }

        response.success?.run {
            sessionManager.writeUser(user)
            sessionManager.writePermissions(permissions)
        }

        return response
    }

    suspend fun createPairingSession(): Flow<PairingMessage> = callbackFlow {
        http.wss("/api/ws/users/pair") {
            incoming.consumeAsFlow()
                .onEach { frame ->
                    when (frame) {
                        is Frame.Text -> trySend(json.decodeFromString(frame.readText()))
                        is Frame.Close -> close()
                        else -> Unit
                    }
                }
                .collect()
        }
        awaitClose()
    }

    suspend fun search(query: String, limit: Int? = null): SearchResponse =
        http.get("/api/search") {
            parameter("query", query)
            parameter("limit", limit)
        }

    suspend fun closeStream(token: String) {

    }

    fun observeLogs(): Flow<String> = callbackFlow {
        http.wss("/api/ws/admin/logs") {
            incoming.consumeAsFlow()
                .onEach { frame ->
                    when (frame) {
                        is Frame.Text -> trySend(frame.readText())
                        is Frame.Close -> close()
                        else -> Unit
                    }
                }
                .onStart { outgoing.trySend(Frame.Text(sessionManager.fetchToken()!!)) }
                .collect()
        }
        awaitClose()
    }

    suspend fun getStreams(): PlaybackSessionsResponse =
        http.get("/api/stream")

    fun createHlsStreamUrl(mediaRefId: String, token: String): String {
        return "${serverUrl}/api/stream/${mediaRefId}/hls/playlist.m3u8?token=$token"
    }

    data class PlaybackSessionHandle(
        val update: MutableSharedFlow<Double>,
        val cancel: () -> Unit,
    )
}

fun HttpRequestBuilder.pageParam(page: Int) {
    parameter(PAGE, page)
}
