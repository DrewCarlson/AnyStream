/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val PAGE = "page"
private const val QUERY = "query"

private val json = Json {
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    allowStructuredMapKeys = true
    useAlternativeNames = false
}

class AnyStreamClient(
    /** The AnyStream server URL, ex. `http://localhost:3000`. */
    serverUrl: String?,
    http: HttpClient = HttpClient(),
    private val sessionManager: SessionManager = SessionManager(SessionDataStore)
) {
    companion object {
        const val SESSION_KEY = "as_user_session"
    }

    val authenticated: Flow<Boolean> = sessionManager.tokenFlow.map { it != null }
    val permissions: Flow<Set<Permission>?> = sessionManager.permissionsFlow
    val user: Flow<User?> = sessionManager.userFlow
    val token: String?
        get() = sessionManager.fetchToken()

    private val _serverUrl = atomic("")
    private val _serverUrlWss = atomic("")
    var serverUrl: String
        get() = _serverUrl.value
        private set(value) {
            val trimmedUrl = value.trimEnd('/')
            _serverUrl.value = trimmedUrl
            _serverUrlWss.value = trimmedUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
        }
    private val serverUrlWs: String
        get() = _serverUrlWss.value

    init {
        this.serverUrl = serverUrl ?: sessionManager.fetchServerUrl() ?: ""
    }

    private val http = http.config {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(ContentNegotiation) {
            json(json)
        }
        WebSockets {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
        install("TokenHandler") {
            requestPipeline.intercept(HttpRequestPipeline.Before) {
                sessionManager.fetchToken()?.let { token ->
                    context.header(SESSION_KEY, token)
                }
            }
            responsePipeline.intercept(HttpResponsePipeline.Receive) {
                context.response.headers[SESSION_KEY]?.let { token ->
                    if (token != sessionManager.fetchToken()) {
                        sessionManager.writeToken(token)
                    }
                }
                val sentToken = context.request.headers[SESSION_KEY]
                if (context.response.status == Unauthorized && sentToken != null) {
                    sessionManager.clear()
                }
            }
        }
    }

    suspend fun verifyAndSetServerUrl(serverUrl: String): Boolean {
        return try {
            check(http.get(serverUrl).status == HttpStatusCode.OK)
            this.serverUrl = serverUrl
            sessionManager.writeServerUrl(this.serverUrl)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun isAuthenticated(): Boolean {
        return sessionManager.fetchToken() != null
    }

    fun userPermissions(): Set<Permission> {
        return sessionManager.fetchPermissions() ?: emptySet()
    }

    fun hasPermission(permission: Permission): Boolean {
        return userPermissions().run { contains(Permission.Global) || contains(permission) }
    }

    fun authedUser(): User? {
        return sessionManager.fetchUser()
    }

    fun torrentListChanges(): Flow<List<String>> = callbackFlow {
        http.wss("$serverUrlWs/api/ws/torrents/observe") {
            send(sessionManager.fetchToken()!!)
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

    fun globalInfoChanges(): Flow<GlobalTransferInfo> = callbackFlow {
        http.wss("$serverUrlWs/api/ws/torrents/global") {
            send(sessionManager.fetchToken()!!)
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

    suspend fun getHomeData(): HomeResponse = http.get("$serverUrl/api/home").body()

    suspend fun getMovies(page: Int = 1): MoviesResponse =
        http.get("$serverUrl/api/movies") { pageParam(page) }.body()

    suspend fun getMovieSources(id: String): List<TorrentDescription2> =
        http.get("$serverUrl/api/media/movies/$id/sources").body()

    suspend fun getTvShowSources(id: String): List<TorrentDescription2> =
        http.get("$serverUrl/api/media/tv/$id/sources").body()

    suspend fun getTvShows(page: Int = 1): TvShowsResponse =
        http.get("$serverUrl/api/tv") { pageParam(page) }.body()

    suspend fun getMovie(id: String): MovieResponse =
        http.get("$serverUrl/api/movies/$id").body()

    suspend fun deleteMovie(id: String) {
        http.delete("$serverUrl/api/movies/$id")
    }

    suspend fun getMediaRefs(): List<MediaReference> =
        http.get("$serverUrl/api/media/refs").body()

    suspend fun getMediaRef(refId: String): MediaReference =
        http.get("$serverUrl/api/media/refs/$refId").body()

    suspend fun getMediaRefsForMovie(movieId: String): List<MediaReference> =
        http.get("$serverUrl/api/movies/$movieId/refs").body()

    suspend fun getMediaRefsForShow(showId: String): List<MediaReference> =
        http.get("$serverUrl/api/tv/$showId/refs").body()

    suspend fun importMedia(importMedia: ImportMedia, importAll: Boolean) {
        http.post("$serverUrl/api/media/import") {
            contentType(ContentType.Application.Json)
            parameter("importAll", importAll)
            setBody(importMedia)
        }
    }

    suspend fun unmappedMedia(importMedia: ImportMedia): List<String> {
        return http.post("$serverUrl/api/media/unmapped") {
            contentType(ContentType.Application.Json)
            setBody(importMedia)
        }.body()
    }

    suspend fun refreshMetadata(mediaId: String): MediaLookupResponse {
        return http.get("$serverUrl/api/media/$mediaId/refresh-metadata").body()
    }

    suspend fun refreshStreamDetails(mediaId: String): List<ImportStreamDetailsResult> {
        return http.get("$serverUrl/api/media/$mediaId/refresh-stream-details").body()
    }

    suspend fun getTvShow(id: String): TvShowResponse =
        http.get("$serverUrl/api/tv/$id").body()

    suspend fun getEpisodes(showId: String): EpisodesResponse =
        http.get("$serverUrl/api/tv/$showId/episodes").body()

    suspend fun getEpisode(id: String): EpisodeResponse =
        http.get("$serverUrl/api/tv/episode/$id").body()

    suspend fun getSeason(seasonId: String): SeasonResponse =
        http.get("$serverUrl/api/tv/season/$seasonId").body()

    suspend fun lookupMedia(mediaId: String): MediaLookupResponse =
        http.get("$serverUrl/api/media/$mediaId").body()

    suspend fun lookupMediaByRefId(refId: String): MediaLookupResponse =
        http.get("$serverUrl/api/media/by-ref/$refId").body()

    suspend fun getTmdbSources(tmdbId: Int): List<TorrentDescription2> =
        http.get("$serverUrl/api/media/tmdb/$tmdbId/sources").body()

    suspend fun getGlobalTransferInfo(): GlobalTransferInfo =
        http.get("$serverUrl/api/torrents/global").body()

    suspend fun getTorrents(): List<Torrent> =
        http.get("$serverUrl/api/torrents").body()

    suspend fun getTorrentFiles(hash: String): List<TorrentFile> =
        http.get("$serverUrl/api/torrents/$hash/files").body()

    suspend fun resumeTorrent(hash: String) {
        http.get("$serverUrl/api/torrents/$hash/resume")
    }

    suspend fun pauseTorrent(hash: String) {
        http.get("$serverUrl/api/torrents/$hash/pause")
    }

    suspend fun deleteTorrent(hash: String, deleteFiles: Boolean = false) {
        http.delete("$serverUrl/api/torrents/$hash") {
            parameter("deleteFiles", deleteFiles)
        }
    }

    suspend fun downloadTorrent(description: TorrentDescription2, movieId: String?) {
        http.post("$serverUrl/api/torrents") {
            contentType(ContentType.Application.Json)
            setBody(description)

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
        val mutex = Mutex(locked = true)
        scope.launch {
            http.wss("$serverUrlWs/api/ws/stream/$mediaRefId/state") {
                send(sessionManager.fetchToken()!!)
                val playbackState = receiveDeserialized<PlaybackState>()
                currentState.value = playbackState
                init(playbackState)
                progressFlow
                    .sample(5000)
                    .distinctUntilChanged()
                    .onEach { progress ->
                        currentState.update { it?.copy(position = progress) }
                        sendSerialized(currentState.value!!)
                    }
                    .launchIn(this)
                mutex.withLock { close() }
            }
            scope.cancel()
        }
        return PlaybackSessionHandle(
            update = progressFlow,
            cancel = { mutex.unlock() }
        )
    }

    suspend fun createUser(
        username: String,
        password: String,
        inviteCode: String?,
        rememberUser: Boolean = true,
    ): CreateUserResponse = http.post("$serverUrl/api/users") {
        contentType(ContentType.Application.Json)
        parameter("createSession", rememberUser)
        setBody(CreateUserBody(username, password, inviteCode))
    }.body<CreateUserResponse>().also { response ->
        if (rememberUser && response is CreateUserResponse.Success) {
            sessionManager.writeUser(response.user)
            sessionManager.writePermissions(response.permissions)
        }
    }

    suspend fun getUser(id: String): User {
        return http.get("$serverUrl/api/users/$id").body()
    }

    suspend fun getUsers(): List<User> {
        return http.get("$serverUrl/api/users").body()
    }

    suspend fun updateUser(
        userId: Int,
        displayName: String,
        password: String?,
        currentPassword: String?
    ) {
        http.put("$serverUrl/api/users/$userId") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateUserBody(
                    displayName = displayName,
                    password = password,
                    currentPassword = currentPassword
                )
            )
        }
    }

    suspend fun deleteUser(id: String): HttpResponse {
        return http.delete("$serverUrl/api/users/$id")
    }

    suspend fun login(username: String, password: String, pairing: Boolean = false): CreateSessionResponse {
        return http.post("$serverUrl/api/users/session") {
            contentType(ContentType.Application.Json)
            setBody(CreateSessionBody(username, password))
        }.body<CreateSessionResponse>().also { response ->
            if (!pairing && response is CreateSessionResponse.Success) {
                sessionManager.writeUser(response.user)
                sessionManager.writePermissions(response.permissions)
            }
        }
    }

    suspend fun logout() {
        val token = sessionManager.fetchToken() ?: return
        sessionManager.clear()
        http.delete("$serverUrl/api/users/session") {
            header(SESSION_KEY, token)
        }
    }

    suspend fun getInvites(): List<InviteCode> {
        return http.get("$serverUrl/api/users/invite").body()
    }

    suspend fun createInvite(permissions: Set<Permission>): InviteCode {
        return http.post("$serverUrl/api/users/invite") {
            contentType(ContentType.Application.Json)
            setBody(permissions)
        }.body()
    }

    suspend fun deleteInvite(id: String): Boolean {
        return try {
            http.delete("$serverUrl/api/users/invite/$id")
            true
        } catch (e: ClientRequestException) {
            if (e.response.status == NotFound) false else throw e
        }
    }

    suspend fun createPairedSession(pairingCode: String, secret: String): CreateSessionResponse {
        val response = http.post("$serverUrl/api/users/session/paired") {
            parameter("pairingCode", pairingCode)
            parameter("secret", secret)
        }.body<CreateSessionResponse>()

        (response as? CreateSessionResponse.Success)?.run {
            sessionManager.writeUser(user)
            sessionManager.writePermissions(permissions)
        }

        return response
    }

    suspend fun createPairingSession(): Flow<PairingMessage> = flow {
        http.wss("$serverUrlWs/api/ws/users/pair") {
            while (!incoming.isClosedForReceive) {
                try {
                    emit(receiveDeserialized())
                } catch (e: WebsocketDeserializeException) {
                    // ignored
                } catch (e: ClosedReceiveChannelException) {
                    // ignored
                }
            }
        }
    }

    suspend fun search(query: String, limit: Int? = null): SearchResponse {
        return http.get("$serverUrl/api/search") {
            parameter("query", query)
            parameter("limit", limit)
        }.body()
    }

    fun observeLogs(): Flow<String> = callbackFlow {
        http.wss("$serverUrlWs/api/ws/admin/logs") {
            send(sessionManager.fetchToken()!!)
            for (frame in incoming) {
                if (frame is Frame.Text) trySend(frame.readText())
            }
            awaitClose()
        }
    }

    suspend fun getStreams(): PlaybackSessionsResponse {
        return http.get("$serverUrl/api/stream").body()
    }

    fun createHlsStreamUrl(mediaRefId: String, token: String): String {
        return "$serverUrl/api/stream/$mediaRefId/hls/playlist.m3u8?token=$token"
    }

    data class PlaybackSessionHandle(
        val update: MutableSharedFlow<Double>,
        val cancel: () -> Unit,
    )
}

fun HttpRequestBuilder.pageParam(page: Int) {
    parameter(PAGE, page)
}
