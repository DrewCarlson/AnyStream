/*
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
package anystream.routes

import anystream.data.UserSession
import anystream.di.ServerScope
import anystream.json
import anystream.models.*
import anystream.service.stream.StreamService
import anystream.util.extractUserSession
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drewcarlson.ktor.permissions.withPermission
import kotlin.time.Duration.Companion.seconds

private const val PLAYBACK_COMPLETE_PERCENT = 90

@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<RoutingController>(),
)
@Inject
class StreamRoutes(
    private val streamService: StreamService,
) : RoutingController {
    override fun init(parent: Route) {
        parent.apply {
            route("/stream") {
                authenticate {
                    withPermission(Permission.ConfigureSystem) {
                        get { getPlaybackSessions() }
                    }
                }

                route("/{mediaLinkId}") {
                    authenticate {
                        route("/state") {
                            get { getPlaybackState() }
                            put { updatePlaybackState() }
                        }
                    }

                    route("/hls") {
                        get("/playlist.m3u8") { getHlsPlaylist() }
                        get("/{segmentFile}") { getHlsSegment() }
                    }
                }

                delete("/stop/{token}") { stopSession() }
            }
            webSocket("/ws/stream/{mediaLinkId}/state") { wsPlaybackState() }
        }
    }

    suspend fun RoutingContext.getPlaybackSessions() {
        call.respond(streamService.getPlaybackSessions())
    }

    suspend fun RoutingContext.getPlaybackState() {
        val session = checkNotNull(call.principal<UserSession>())
        val mediaLinkId = call.parameters["mediaLinkId"]!!
        val playbackState =
            streamService.getPlaybackState(mediaLinkId, session.userId, false)
        if (playbackState == null) {
            call.respond(NotFound)
        } else {
            call.respond(playbackState)
        }
    }

    suspend fun RoutingContext.updatePlaybackState() {
        val session = checkNotNull(call.principal<UserSession>())
        val mediaLinkId = call.parameters["mediaLinkId"]!!
        val state = runCatching { call.receiveNullable<PlaybackState>() }
            .getOrNull() ?: return call.respond(UnprocessableEntity)

        val actualState =
            streamService.getPlaybackState(mediaLinkId, session.userId, false)

        if (actualState == null) {
            call.respond(NotFound)
        } else {
            val success =
                streamService.updateStatePosition(actualState, state.position)
            call.respond(if (success) OK else InternalServerError)
        }
    }

    suspend fun RoutingContext.getHlsPlaylist() {
        val mediaLinkId = call.parameters["mediaLinkId"]!!
        val token = call.parameters["token"]
            ?: return call.respond(Unauthorized)

        val clientCapabilities = call.request.queryParameters["capabilities"]!!.let {
            json.decodeFromString<ClientCapabilities>(it)
        }

        val playlist = streamService.getPlaylist(mediaLinkId, token, clientCapabilities)
        if (playlist == null) {
            call.respond(NotFound)
        } else {
            call.respondText(playlist, ContentType("application", "vnd.apple.mpegURL"))
        }
    }

    suspend fun RoutingContext.getHlsSegment() {
        val segmentFilePath = call.parameters["segmentFile"]
            ?: return call.respond(NotFound)
        val (token, segmentFile) = segmentFilePath.split('-', limit = 2)

        val filePath = streamService.getFilePathForSegment(token, segmentFile)
        if (filePath == null) {
            call.respond(NotFound)
        } else {
            val contentType = if (segmentFile.endsWith(".mp4")) {
                ContentType.Video.MP4
            } else {
                ContentType.Video.MPEG
            }
            call.respond(LocalPathContent(filePath, contentType))
        }
    }

    suspend fun RoutingContext.stopSession() {
        val token = call.parameters["token"]!!
        val delete = call.parameters["delete"]?.toBoolean() ?: true
        streamService.stopSession(token, delete)
        call.respond(OK)
    }

    suspend fun DefaultWebSocketServerSession.wsPlaybackState() {
        val session = checkNotNull(extractUserSession())
        check(Permission.check(Permission.ViewCollection, session.permissions))
        val userId = session.userId
        val mediaLinkId = call.parameters["mediaLinkId"]!!

        val clientCapabilities = receiveDeserialized<ClientCapabilities>()

        val state = streamService.getPlaybackState(
            mediaLinkId = mediaLinkId,
            userId = userId,
            create = true,
            clientCapabilities = clientCapabilities,
        ) ?: return close()

        send(Frame.Text(json.encodeToString(state)))

        launch {
            // todo: provide flow from stream service, remove polling
            while (true) {
                delay(2.seconds)
                if (!streamService.isSessionActive(state.id)) {
                    close(CloseReason(CloseReason.Codes.GOING_AWAY, "Session ended by server"))
                }
            }
        }

        var finalState = state
        for (frame in incoming) {
            if (frame is Frame.Text) {
                finalState = json.decodeFromString<PlaybackState>(frame.readText())
                streamService.updateStatePosition(state, finalState.position)
            }
        }

        val isComplete = finalState.completedPercent >= PLAYBACK_COMPLETE_PERCENT
        if (isComplete) {
            streamService.deletePlaybackState(state.id)
        }
        streamService.stopSession(state.id, deleteOutput = true)
    }
}
