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
package anystream.routes

import anystream.data.UserSession
import anystream.json
import anystream.models.*
import anystream.service.stream.StreamService
import anystream.util.extractUserSession
import anystream.util.withPermission
import com.github.kokorin.jaffree.ffmpeg.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.StandardOpenOption.*
import java.util.*
import kotlin.math.roundToInt

private const val PLAYBACK_COMPLETE_PERCENT = 90

fun Route.addStreamRoutes(
    streamService: StreamService,
) {
    route("/stream") {
        authenticate {
            withPermission(Permissions.CONFIGURE_SYSTEM) {
                get {
                    call.respond(streamService.getPlaybackSessions())
                }
            }
        }

        route("/{mediaRefId}") {
            authenticate {
                route("/state") {
                    get {
                        val session = call.principal<UserSession>()!!
                        val mediaRefId = call.parameters["mediaRefId"]!!
                        val playbackState =
                            streamService.getPlaybackState(mediaRefId, session.userId, false)
                        if (playbackState == null) {
                            call.respond(NotFound)
                        } else {
                            call.respond(playbackState)
                        }
                    }
                    put {
                        val session = call.principal<UserSession>()!!
                        val mediaRefId = call.parameters["mediaRefId"]!!
                        val state = call.receiveOrNull<PlaybackState>()
                            ?: return@put call.respond(UnprocessableEntity)

                        val success = streamService.updateStatePosition(
                            mediaRefId,
                            session.userId,
                            state.position
                        )
                        call.respond(if (success) OK else InternalServerError)
                    }
                }
            }

            route("/hls") {
                get("/playlist.m3u8") {
                    val mediaRefId = call.parameters["mediaRefId"]!!
                    val token = call.parameters["token"]
                        ?: return@get call.respond(Unauthorized)
                    val playlist = streamService.getPlaylist(mediaRefId, token)
                    if (playlist == null) {
                        call.respond(NotFound)
                    } else {
                        call.respond(playlist)
                    }
                }

                get("/{segmentFile}") {
                    val segmentFile = call.parameters["segmentFile"]
                        ?: return@get call.respond(NotFound)
                    val token = call.request.queryParameters["token"]
                        ?: return@get call.respond(Unauthorized)

                    val filePath = streamService.getFilePathForSegment(token, segmentFile)
                    if (filePath == null) {
                        call.respond(NotFound)
                    } else {
                        call.respond(
                            LocalFileContent(
                                File(filePath),
                                ContentType.Application.OctetStream
                            )
                        )
                    }
                }
            }
        }

        get("/stop/{token}") {
            val token = call.parameters["token"]!!
            val delete = call.parameters["delete"]?.toBoolean() ?: false
            streamService.stopSession(token, delete)
            call.respond(OK)
        }
    }
}

fun Route.addStreamWsRoutes(
    streamService: StreamService,
) {
    webSocket("/ws/stream/{mediaRefId}/state") {
        val userSession = checkNotNull(extractUserSession())
        val userId = userSession.userId
        val mediaRefId = call.parameters["mediaRefId"]!!

        val state = streamService.getPlaybackState(mediaRefId, userId, create = true)
            ?: return@webSocket close()

        send(Frame.Text(json.encodeToString(state)))

        val finalPosition = incoming.receiveAsFlow()
            .takeWhile { it !is Frame.Close }
            .filterIsInstance<Frame.Text>()
            .map { frame ->
                val newState = json.decodeFromString<PlaybackState>(frame.readText())
                streamService.updateStatePosition(mediaRefId, userId, newState.position)
                newState.position
            }
            .lastOrNull() ?: state.position

        val completePercent = ((finalPosition / state.runtime) * 100).roundToInt()
        val isComplete = completePercent >= PLAYBACK_COMPLETE_PERCENT
        if (isComplete) {
            streamService.deletePlaybackState(state.id)
        }
        streamService.stopSession(state.id, isComplete)
    }
}
