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
import io.ktor.client.plugins.*
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
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.io.Buffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import qbittorrent.models.GlobalTransferInfo
import qbittorrent.models.Torrent
import qbittorrent.models.TorrentFile

private const val PAGE = "page"
private const val QUERY = "query"

val json = Json {
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
            level = LogLevel.INFO
        }
        WebSockets {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
        install(AdaptiveProtocolPlugin)
        defaultRequest {
            attributes.put(ServerUrlAttribute, this@AnyStreamClient.serverUrl)
            url {
                takeFrom(this@AnyStreamClient.serverUrl)
            }
            headers {
                sessionManager.fetchToken()?.let { token ->
                    header(SESSION_KEY, token)
                }
            }
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

    val user by lazy {
        UserApiClient(
            http = http,
            sessionManager = sessionManager,
        )
    }
    val stream by lazy {
        StreamApiClient(
            http = http,
            sessionManager = sessionManager,
            getServerUrl = { this.serverUrl },
        )
    }

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

    @OptIn(DelicateCoroutinesApi::class)
    private inline fun <reified T> createWsStateFlow(path: String, default: T): StateFlow<T> {
        return callbackFlow<T> {
            launch {
                try {
                    http.wss(path = path) {
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

    fun buildImageUrl(imageType: String, metadataId: String, width: Int = 0): String {
        return "${serverUrl}/api/image/$metadataId/${imageType}.jpg?width=$width"
    }

    suspend fun getMediaLinkBif(mediaLinkId: String): Buffer? {
        val response = http.get("/api/image/previews/$mediaLinkId")
        if (!response.status.isSuccess()) {
            return null
        }
        return Buffer().apply { write(response.bodyAsBytes()) }
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

    fun torrentListChanges(): Flow<List<String>> = callbackFlow {
        http.wss(path = "/api/ws/torrents/observe") {
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
        http.wss(path = "/api/ws/torrents/global") {
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

    suspend fun getHomeData(): HomeResponse = http.get("api/home").bodyOrThrow()

    suspend fun getHomeWatching(): CurrentlyWatching =
        http.get("/api/home/watching").bodyOrThrow()

    suspend fun getHomePopular(): Popular = http.get("/api/home/popular").bodyOrThrow()

    suspend fun getMovies(): MoviesResponse =
        http.get("/api/movies").bodyOrThrow()

    suspend fun getMovies(offset: Int, limit: Int = 30): MoviesResponse =
        http.get("/api/movies") {
            parameter("offset", offset)
            parameter("limit", limit)
        }.bodyOrThrow()

    suspend fun getTvShows(page: Int = 1): TvShowsResponse =
        http.get("/api/tv") { pageParam(page) }.bodyOrThrow()

    suspend fun getLibraries(): List<Library> {
        return http.get("/api/library").bodyOrThrow()
    }

    suspend fun addLibraryFolder(libraryId: String, path: String): AddLibraryFolderResponse {
        return try {
            http.put("/api/library/${libraryId}") {
                contentType(ContentType.Application.Json)
                setBody(AddLibraryFolderRequest(path))
            }.bodyOrThrow()
        } catch (e: AnyStreamClientException) {
            e.printStackTrace()
            AddLibraryFolderResponse.RequestError(e.stackTraceToString())
        }
    }

    suspend fun removeMediaLink(mediaLinkId: String): Boolean {
        return try {
            http.delete("/api/medialink/$mediaLinkId").orThrow()
            true
        } catch (e: AnyStreamClientException) {
            if (e.response?.status == NotFound) false else throw e
        }
    }

    suspend fun getDirectories(libraryId: String): List<Directory> {
        return try {
            http.get("/api/library/$libraryId/directories").bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun removeDirectory(directoryId: String): Boolean {
        return try {
            http.delete("/api/library/directory/$directoryId").bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun scanDirectory(directoryId: String, refreshMetadata: Boolean = false) {
        try {
            http.get("/api/library/directory/$directoryId/scan") {
                parameter("refreshMetadata", refreshMetadata)
            }.bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun scanLibrary(libraryId: String) {
        try {
            http.get("/api/library/$libraryId/scan").orThrow()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun getLibraryFolderList(): LibraryFolderList {
        return try {
            http.get("/api/medialink/libraries").bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
            LibraryFolderList(emptyList())
        }
    }

    suspend fun unmappedMedia(mediaScanRequest: MediaScanRequest): List<String> {
        return http.post("/api/metadata/libraries/unmapped") {
            contentType(ContentType.Application.Json)
            setBody(mediaScanRequest)
        }.bodyOrThrow()
    }

    suspend fun refreshMetadata(metadataId: String): MediaLookupResponse {
        return http.get("/api/media/$metadataId/refresh-metadata").bodyOrThrow()
    }

    suspend fun analyzeMediaLink(mediaLinkId: String): List<MediaAnalyzerResult> {
        return http.get("/api/medialink/$mediaLinkId/analyze") {
            parameter("waitForResult", true)
        }.bodyOrThrow()
    }

    suspend fun analyzeMediaLinksAsync(mediaLinkId: String) {
        http.get("/api/medialink/$mediaLinkId/analyze") {
            parameter("waitForResult", false)
        }.bodyOrThrow<String>()
    }

    suspend fun lookupMedia(mediaId: String): MediaLookupResponse =
        http.get("/api/media/$mediaId").bodyOrThrow()

    suspend fun getTmdbSources(tmdbId: Int): List<TorrentDescription2> =
        http.get("/api/media/tmdb/$tmdbId/sources").bodyOrThrow()

    suspend fun getGlobalTransferInfo(): GlobalTransferInfo =
        http.get("/api/torrents/global").bodyOrThrow()

    suspend fun getTorrents(): List<Torrent> =
        http.get("/api/torrents").body()

    suspend fun getTorrentFiles(hash: String): List<TorrentFile> =
        http.get("/api/torrents/$hash/files").bodyOrThrow()

    suspend fun resumeTorrent(hash: String) {
        http.get("/api/torrents/$hash/resume").orThrow()
    }

    suspend fun pauseTorrent(hash: String) {
        http.get("/api/torrents/$hash/pause").orThrow()
    }

    suspend fun deleteTorrent(hash: String, deleteFiles: Boolean = false) {
        http.delete("/api/torrents/$hash") {
            parameter("deleteFiles", deleteFiles)
        }.orThrow()
    }

    suspend fun downloadTorrent(description: TorrentDescription2, movieId: String?) {
        http.post("/api/torrents") {
            contentType(ContentType.Application.Json)
            setBody(description)

            movieId?.let { parameter("movieId", it) }
        }.orThrow()
    }

    suspend fun search(query: String, limit: Int? = null): SearchResponse {
        return http.get("/api/search") {
            parameter("query", query)
            parameter("limit", limit)
        }.bodyOrThrow()
    }

    fun observeLogs(): Flow<String> = callbackFlow {
        http.wss(path = "/api/ws/admin/logs") {
            send(sessionManager.fetchToken()!!)
            for (frame in incoming) {
                if (frame is Frame.Text) trySend(frame.readText())
            }
            awaitClose()
        }
    }

    suspend fun getStreams(): PlaybackSessions {
        return http.get("/api/stream").bodyOrThrow()
    }

    suspend fun listFiles(path: String? = null, showFiles: Boolean = false): ListFilesResponse? {
        val response = http.get("/api/medialink/libraries/list-files") {
            parameter("showFiles", showFiles)
            if (!path.isNullOrBlank()) {
                parameter("root", path)
            }
        }

        if (response.status == NotFound) return null

        return response.bodyOrThrow()
    }

    suspend fun scanMediaLink(mediaLinkId: String): MediaScanResult? {
        val response = http.get("/api/medialink/$mediaLinkId/scan")
        return response.bodyOrThrow()
    }

    suspend fun matchesFor(mediaLinkId: String): List<MediaLinkMatchResult> {
        return http.get("/api/medialink/$mediaLinkId/matches").bodyOrThrow()
    }

    suspend fun matchFor(mediaLinkId: String, remoteId: String) {
        val body = buildJsonObject {
            put("remoteId", remoteId)
        }
        http.put("/api/medialink/$mediaLinkId/matches") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun getMediaLinks(parentId: String? = null): List<MediaLink> {
        return http.get("/api/medialink") {
            parameter("parent", parentId)
        }.bodyOrThrow()
    }

    suspend fun findMediaLink(
        mediaLinkId: String,
        includeMetadata: Boolean = true,
    ): MediaLinkResponse {
        val response = http.get("/api/medialink/$mediaLinkId") {
            parameter("includeMetadata", includeMetadata)
        }
        return response.bodyOrThrow()
    }


    suspend fun generatePreview(mediaLinkId: String): Boolean {
        return http.get("/api/medialink/$mediaLinkId/generate-preview").status == OK
    }

}

internal suspend fun HttpResponse.orThrow() {
    if (!status.isSuccess()) {
        throw call.attributes.takeOrNull(KEY_INTERNAL_ERROR)?.run(::AnyStreamClientException)
            ?: AnyStreamClientException(this, bodyAsText())
    }
}

internal suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
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

fun HttpRequestBuilder.pageParam(page: Int) {
    parameter(PAGE, page)
}
