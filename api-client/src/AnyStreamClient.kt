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
import com.soywiz.korio.net.ws.WebSocketClient
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val wsProto = if (serverUrl.startsWith("https")) "wss" else "ws"
    private val wsServerUrl = "$wsProto://${serverUrl.substringAfter("://")}"
    private val http = http.config {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        WebSockets { }
        Json {
            serializer = KotlinxSerializer(json)
        }
        defaultRequest {
            url {
                host = serverUrl.substringAfter("://").substringBefore(':')
                port = serverUrl.substringAfterLast(':').takeWhile { it != '/' }.toIntOrNull() ?: 0
                protocol = URLProtocol.createOrDefault(serverUrl.substringBefore("://"))
            }
        }
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

    fun authedUser(): User? {
        return sessionManager.fetchUser()
    }

    fun torrentListChanges(): Flow<List<String>> = callbackFlow {
        val client = wsClient("/api/ws/torrents/observe")
        client.onStringMessage { msg ->
            if (msg.isNotBlank()) {
                trySend(msg.split(","))
            }
        }
        client.onError { close(it) }
        client.onClose { close() }
        awaitClose { client.close() }
    }

    fun globalInfoChanges(): Flow<GlobalTransferInfo> = callbackFlow {
        val client = wsClient("/api/ws/torrents/global")
        client.onStringMessage { msg ->
            if (msg.isNotBlank()) {
                trySend(json.decodeFromString(msg))
            }
        }
        client.onError { close(it) }
        client.onClose { close() }
        awaitClose { client.close() }
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

    suspend fun searchTmdbMovies(query: String, page: Int = 1) =
        http.get<TmdbMoviesResponse>("/api/movies/tmdb/search") {
            parameter(QUERY, query)
            pageParam(page)
        }

    suspend fun getTmdbSources(tmdbId: Int) =
        http.get<List<TorrentDescription2>>("/api/media/tmdb/$tmdbId/sources")

    suspend fun searchTmbdTvShows(query: String, page: Int = 1) =
        http.get<TmdbTvShowResponse>("/api/tv/tmdb/search") {
            parameter(QUERY, query)
            pageParam(page)
        }

    suspend fun getTmdbPopularMovies(page: Int = 1) =
        http.get<TmdbMoviesResponse>("/api/movies/tmdb/popular") {
            pageParam(page)
        }

    suspend fun getTmdbPopularTvShows(page: Int = 1) =
        http.get<TmdbTvShowResponse>("/api/tv/tmdb/popular") {
            pageParam(page)
        }

    suspend fun addMovieFromTmdb(tmdbId: Int) =
        http.get<Unit>("/api/movies/tmdb/$tmdbId/add")

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
        val progressFlow = MutableSharedFlow<Long>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        var open = false
        val client = wsClient("/api/ws/stream/$mediaRefId/state")
        client.send(sessionManager.fetchUser()!!.id)
        client.onStringMessage { msg ->
            currentState.value = json.decodeFromString(msg)
            init(currentState.value!!)
            open = true
        }
        client.onError { open = false }
        client.onClose { open = false }
        progressFlow
            .sample(5000)
            .distinctUntilChanged()
            .onEach { progress ->
                if (open) {
                    currentState.update { currentState ->
                        currentState?.copy(position = progress)
                    }
                    client.send(json.encodeToString(currentState.value))
                }
            }
            .launchIn(scope)
        return PlaybackSessionHandle(
            update = progressFlow,
            cancel = {
                scope.cancel()
                client.close()
            }
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
        val response =  http.post<CreateSessionResponse>("/api/users/session/paired") {
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
        val client = wsClient("/api/ws/users/pair")
        client.onStringMessage { msg ->
            if (msg.isNotBlank()) {
                trySend(json.decodeFromString(msg))
            }
        }
        client.onError { close(it) }
        client.onClose { close() }
        awaitClose { client.close() }
    }

    suspend fun search(query: String, limit: Int? = null): SearchResponse =
        http.get("/api/search") {
            parameter("query", query)
            parameter("limit", limit)
        }

    suspend fun closeStream(token: String) {

    }

    fun createHlsStreamUrl(mediaRefId: String, token: String): String {
        return "${serverUrl}/api/stream/${mediaRefId}/hls/playlist.m3u8?token=$token"
    }

    private suspend fun wsClient(path: String): WebSocketClient {
        return WebSocketClient(url = "$wsServerUrl$path")
    }

    data class PlaybackSessionHandle(
        val update: MutableSharedFlow<Long>,
        val cancel: () -> Unit,
    )
}

fun HttpRequestBuilder.pageParam(page: Int) {
    parameter(PAGE, page)
}
