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
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
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
import qbittorrent.models.GlobalTransferInfo
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentFile

private const val PAGE = "page"
private const val QUERY = "query"

private val json = Json {
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    allowStructuredMapKeys = true
    useAlternativeNames = false
}

private val KEY_INTERNAL_ERROR = AttributeKey<Throwable>("INTERNAL_ERROR")

class AnyStreamClient(
    /** The AnyStream server URL, ex. `http://localhost:3000`. */
    serverUrl: String?,
    httpClient: HttpClient,
    private val sessionManager: SessionManager,
) {
    companion object {
        const val SESSION_KEY = "as_user_session"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val authenticated: Flow<Boolean> = sessionManager.tokenFlow.map { it != null }
    val permissions: Flow<Set<Permission>?> = sessionManager.permissionsFlow
    val user: Flow<User?> = sessionManager.userFlow
    val token: String?
        get() = sessionManager.fetchToken()

    private val serverUrlInternal = atomic("")
    private val serverUrlWssInternal = atomic("")
    var serverUrl: String
        get() = serverUrlInternal.value
        private set(value) {
            val trimmedUrl = value.trimEnd('/')
            serverUrlInternal.value = trimmedUrl
            serverUrlWssInternal.value = trimmedUrl
                .replace("https://", "wss://", ignoreCase = true)
                .replace("http://", "ws://", ignoreCase = true)
        }
    private val serverUrlWs: String
        get() = serverUrlWssInternal.value

    init {
        this.serverUrl = serverUrl ?: sessionManager.fetchServerUrl() ?: ""
    }

    private val http = httpClient.config {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.BODY
        }
        WebSockets {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
        install("ErrorTransformer") {
            requestPipeline.intercept(HttpRequestPipeline.State) {
                try {
                    proceed()
                } catch (e: Throwable) {
                    val responseData = HttpResponseData(
                        statusCode = HttpStatusCode(-1, ""),
                        requestTime = GMTDate(),
                        body = ByteReadChannel(byteArrayOf()),
                        callContext = context.executionContext,
                        headers = Headers.Empty,
                        version = HttpProtocolVersion.HTTP_1_0,
                    )
                    context.attributes.put(KEY_INTERNAL_ERROR, e)
                    @OptIn(InternalAPI::class)
                    subject = HttpClientCall(this@install, context.build(), responseData)
                    proceed()
                }
            }
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

    private val playbackSessionsFlow =
        createWsStateFlow("/api/ws/admin/sessions", PlaybackSessions())
    private val libraryActivityFlow = createWsStateFlow("/api/ws/admin/activity", LibraryActivity())

    val playbackSessions: StateFlow<PlaybackSessions> = playbackSessionsFlow
    val libraryActivity: StateFlow<LibraryActivity> = libraryActivityFlow

    @OptIn(DelicateCoroutinesApi::class)
    private inline fun <reified T> createWsStateFlow(path: String, default: T): StateFlow<T> {
        return callbackFlow<T> {
            launch {
                try {
                    http.wss("$serverUrlWs$path") {
                        send(sessionManager.fetchToken()!!)
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
        }.stateIn(scope, SharingStarted.WhileSubscribed(), default)
    }

    suspend fun verifyAndSetServerUrl(serverUrl: String): Boolean {
        if (this.serverUrl.equals(serverUrl, ignoreCase = true)) return true
        return try {
            check(http.get(serverUrl).status == OK)
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

    @OptIn(DelicateCoroutinesApi::class)
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

    suspend fun getHomeData(): HomeResponse = http.get("$serverUrl/api/home").bodyOrThrow()

    suspend fun getHomeWatching(): CurrentlyWatching =
        http.get("$serverUrl/api/home/watching").bodyOrThrow()

    suspend fun getHomeRecent(): RecentlyAdded =
        http.get("$serverUrl/api/home/recent").bodyOrThrow()

    suspend fun getHomePopular(): Popular = http.get("$serverUrl/api/home/popular").bodyOrThrow()

    suspend fun getMovies(): MoviesResponse =
        http.get("$serverUrl/api/movies").bodyOrThrow()

    suspend fun getMovies(offset: Int, limit: Int = 30): MoviesResponse =
        http.get("$serverUrl/api/movies") {
            parameter("offset", offset)
            parameter("limit", limit)
        }.bodyOrThrow()

    suspend fun getTvShows(page: Int = 1): TvShowsResponse =
        http.get("$serverUrl/api/tv") { pageParam(page) }.bodyOrThrow()

    suspend fun addLibraryFolder(path: String, mediaKind: MediaKind): AddLibraryFolderResponse {
        return try {
            http.post("$serverUrl/api/medialink/libraries") {
                contentType(ContentType.Application.Json)
                setBody(AddLibraryFolderRequest(path, mediaKind))
            }.bodyOrThrow()
        } catch (e: AnyStreamClientException) {
            e.printStackTrace()
            AddLibraryFolderResponse.RequestError(e.stackTraceToString())
        }
    }

    suspend fun removeMediaLink(gid: String): Boolean {
        return try {
            http.delete("$serverUrl/api/medialink/$gid").orThrow()
            true
        } catch (e: AnyStreamClientException) {
            if (e.response?.status == NotFound) false else throw e
        }
    }

    suspend fun getLibraryFolderList(): LibraryFolderList {
        return try {
            http.get("$serverUrl/api/medialink/libraries").bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
            LibraryFolderList(emptyList())
        }
    }

    suspend fun unmappedMedia(mediaScanRequest: MediaScanRequest): List<String> {
        return http.post("$serverUrl/api/metadata/libraries/unmapped") {
            contentType(ContentType.Application.Json)
            setBody(mediaScanRequest)
        }.bodyOrThrow()
    }

    suspend fun refreshMetadata(metadataGid: String): MediaLookupResponse {
        return http.get("$serverUrl/api/media/$metadataGid/refresh-metadata").bodyOrThrow()
    }

    suspend fun analyzeMediaLink(mediaLinkGid: String): List<MediaAnalyzerResult> {
        return http.get("$serverUrl/api/medialink/$mediaLinkGid/analyze") {
            parameter("waitForResult", true)
        }.bodyOrThrow()
    }

    suspend fun analyzeMediaLinksAsync(mediaLinkGid: String) {
        http.get("$serverUrl/api/medialink/$mediaLinkGid/analyze") {
            parameter("waitForResult", false)
        }.bodyOrThrow<String>()
    }

    suspend fun lookupMedia(mediaId: String): MediaLookupResponse =
        http.get("$serverUrl/api/media/$mediaId").bodyOrThrow()

    suspend fun getTmdbSources(tmdbId: Int): List<TorrentDescription2> =
        http.get("$serverUrl/api/media/tmdb/$tmdbId/sources").bodyOrThrow()

    suspend fun getGlobalTransferInfo(): GlobalTransferInfo =
        http.get("$serverUrl/api/torrents/global").bodyOrThrow()

    suspend fun getTorrents(): List<Torrent> =
        http.get("$serverUrl/api/torrents").body()

    suspend fun getTorrentFiles(hash: String): List<TorrentFile> =
        http.get("$serverUrl/api/torrents/$hash/files").bodyOrThrow()

    suspend fun resumeTorrent(hash: String) {
        http.get("$serverUrl/api/torrents/$hash/resume").orThrow()
    }

    suspend fun pauseTorrent(hash: String) {
        http.get("$serverUrl/api/torrents/$hash/pause").orThrow()
    }

    suspend fun deleteTorrent(hash: String, deleteFiles: Boolean = false) {
        http.delete("$serverUrl/api/torrents/$hash") {
            parameter("deleteFiles", deleteFiles)
        }.orThrow()
    }

    suspend fun downloadTorrent(description: TorrentDescription2, movieId: String?) {
        http.post("$serverUrl/api/torrents") {
            contentType(ContentType.Application.Json)
            setBody(description)

            movieId?.let { parameter("movieId", it) }
        }.orThrow()
    }

    suspend fun playbackSession(
        mediaLinkId: String,
        init: (state: PlaybackState) -> Unit,
    ): PlaybackSessionHandle {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val currentState = MutableStateFlow<PlaybackState?>(null)
        val progressFlow = MutableSharedFlow<Double>(0, 1, BufferOverflow.DROP_OLDEST)
        val mutex = Mutex(locked = true)
        scope.launch {
            http.wss("$serverUrlWs/api/ws/stream/$mediaLinkId/state") {
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
            cancel = { mutex.unlock() },
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
    }.bodyOrThrow<CreateUserResponse>().also { response ->
        if (rememberUser && response is CreateUserResponse.Success) {
            sessionManager.writeUser(response.user)
            sessionManager.writePermissions(response.permissions)
        }
    }

    suspend fun getUser(id: String): User {
        return http.get("$serverUrl/api/users/$id").bodyOrThrow()
    }

    suspend fun getUsers(): List<User> {
        return http.get("$serverUrl/api/users").bodyOrThrow()
    }

    suspend fun updateUser(
        userId: Int,
        displayName: String,
        password: String?,
        currentPassword: String?,
    ) {
        http.put("$serverUrl/api/users/$userId") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateUserBody(
                    displayName = displayName,
                    password = password,
                    currentPassword = currentPassword,
                ),
            )
        }.orThrow()
    }

    suspend fun deleteUser(id: String) {
        http.delete("$serverUrl/api/users/$id").orThrow()
    }

    suspend fun login(
        username: String,
        password: String,
        pairing: Boolean = false,
    ): CreateSessionResponse {
        return http.post("$serverUrl/api/users/session") {
            contentType(ContentType.Application.Json)
            setBody(CreateSessionBody(username, password))
        }.bodyOrThrow<CreateSessionResponse>().also { response ->
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
        }.orThrow()
    }

    suspend fun getInvites(): List<InviteCode> {
        return http.get("$serverUrl/api/users/invite").bodyOrThrow()
    }

    suspend fun createInvite(permissions: Set<Permission>): InviteCode {
        return http.post("$serverUrl/api/users/invite") {
            contentType(ContentType.Application.Json)
            setBody(permissions)
        }.bodyOrThrow()
    }

    suspend fun deleteInvite(id: String): Boolean {
        return try {
            http.delete("$serverUrl/api/users/invite/$id").orThrow()
            true
        } catch (e: AnyStreamClientException) {
            if (e.response?.status == NotFound) false else throw e
        }
    }

    suspend fun createPairedSession(pairingCode: String, secret: String): CreateSessionResponse {
        val response = http.post("$serverUrl/api/users/session/paired") {
            parameter("pairingCode", pairingCode)
            parameter("secret", secret)
        }.bodyOrThrow<CreateSessionResponse>()

        (response as? CreateSessionResponse.Success)?.run {
            sessionManager.writeUser(user)
            sessionManager.writePermissions(permissions)
        }

        return response
    }

    @OptIn(DelicateCoroutinesApi::class)
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
        }.bodyOrThrow()
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

    suspend fun getStreams(): PlaybackSessions {
        return http.get("$serverUrl/api/stream").bodyOrThrow()
    }

    suspend fun listFiles(path: String? = null, showFiles: Boolean = false): ListFilesResponse? {
        val response = http.get("$serverUrl/api/medialink/libraries/list-files") {
            parameter("showFiles", showFiles)
            if (!path.isNullOrBlank()) {
                parameter("root", path)
            }
        }

        if (response.status == NotFound) return null

        return response.bodyOrThrow()
    }

    suspend fun scanMediaLink(mediaLinkGid: String): MediaScanResult? {
        val response = http.get("$serverUrl/api/medialink/$mediaLinkGid/scan")
        return response.bodyOrThrow()
    }

    suspend fun matchesFor(mediaLinkGid: String): List<MediaLinkMatchResult> {
        return http.get("$serverUrl/api/medialink/matches/$mediaLinkGid").bodyOrThrow()
    }

    suspend fun matchFor(mediaLinkGid: String, match: MetadataMatch) {
        http.put("$serverUrl/api/medialink/matches/$mediaLinkGid") {
            contentType(ContentType.Application.Json)
            setBody(match)
        }
    }

    suspend fun findMediaLink(
        mediaLinkGid: String,
        includeMetadata: Boolean = true,
    ): MediaLinkResponse {
        val response = http.get("$serverUrl/api/medialink/$mediaLinkGid") {
            parameter("includeMetadata", includeMetadata)
        }
        return response.bodyOrThrow()
    }

    fun createHlsStreamUrl(mediaLinkId: String, token: String): String {
        return "$serverUrl/api/stream/$mediaLinkId/hls/playlist.m3u8?token=$token"
    }

    data class PlaybackSessionHandle(
        val update: MutableSharedFlow<Double>,
        val cancel: () -> Unit,
    )

    private suspend fun HttpResponse.orThrow() {
        if (!status.isSuccess()) {
            throw call.attributes.takeOrNull(KEY_INTERNAL_ERROR)?.run(::AnyStreamClientException)
                ?: AnyStreamClientException(this, bodyAsText())
        }
    }

    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
        return if (status.isSuccess()) {
            when (T::class) {
                String::class -> bodyAsText() as T
                else -> body()
            }
        } else {
            throw call.attributes.takeOrNull(KEY_INTERNAL_ERROR)?.run(::AnyStreamClientException)
                ?: AnyStreamClientException(this, bodyAsText())
        }
    }
}

class AnyStreamClientException : Exception {

    private var body: String? = null
    var response: HttpResponse? = null
        private set

    constructor(
        response: HttpResponse,
        body: String,
    ) : super() {
        this.response = response
        this.body = body
    }

    constructor(cause: Throwable) : super(cause)

    override val message: String
        get() = if (response == null) {
            super.message ?: "<no message>"
        } else {
            body?.ifBlank { "${response?.status?.value}: <no message>" }.orEmpty()
        }
}

fun HttpRequestBuilder.pageParam(page: Int) {
    parameter(PAGE, page)
}
