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

import anystream.data.MediaDbQueries
import anystream.data.UserSession
import anystream.json
import anystream.models.*
import anystream.models.api.MediaLookupResponse
import anystream.models.api.PlaybackSessionsResponse
import anystream.stream.StreamManager
import anystream.util.withPermission
import com.github.kokorin.jaffree.ffmpeg.*
import com.mongodb.MongoException
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
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
import org.bson.types.ObjectId
import org.litote.kmongo.combine
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import java.io.File
import java.nio.file.StandardOpenOption.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.*
import kotlin.math.roundToInt
import kotlin.time.Duration

private const val PLAYBACK_COMPLETE_PERCENT = 90

fun Route.addStreamRoutes(
    streamManager: StreamManager,
    queries: MediaDbQueries,
    mongodb: CoroutineDatabase,
    ffmpeg: () -> FFmpeg,
) {
    val transcodePath = application.environment.config.property("app.transcodePath").getString()
    val playbackStateDb = mongodb.getCollection<PlaybackState>()
    val mediaRefs = mongodb.getCollection<MediaReference>()
    route("/stream") {
        authenticate {
            withPermission(Permissions.CONFIGURE_SYSTEM) {
                get {
                    val sessions = streamManager.getSessions()
                    val sessionIds = sessions.map(TranscodeSession::token)
                    val playbackStates = queries.findPlaybackStatesByIds(sessionIds)
                    val userIds = playbackStates.map(PlaybackState::userId).distinct()
                    val users = queries.findUsersByIds(userIds)
                    val mediaIds = playbackStates.map(PlaybackState::mediaId).distinct()
                    val mediaLookups = mediaIds.associateWith { id ->
                        MediaLookupResponse(
                            movie = queries.findMovieById(id),
                            episode = queries.findEpisodeById(id),
                        )
                    }
                    call.respond(
                        PlaybackSessionsResponse(
                            playbackStates = playbackStates,
                            transcodeSessions = sessions.associateBy(TranscodeSession::token),
                            users = users.associateBy(User::id),
                            mediaLookups = mediaLookups,
                        )
                    )
                }
            }
        }

        route("/{mediaRefId}") {
            route("/state") {
                get {
                    val session = call.principal<UserSession>()!!
                    val mediaRefId = call.parameters["mediaRefId"]!!
                    val state = playbackStateDb.findOne(
                        PlaybackState::userId eq session.userId,
                        PlaybackState::mediaReferenceId eq mediaRefId
                    )
                    call.respond(state ?: NotFound)
                }
                put {
                    val session = call.principal<UserSession>()!!
                    val mediaRefId = call.parameters["mediaRefId"]!!
                    val state = call.receiveOrNull<PlaybackState>()
                        ?: return@put call.respond(UnprocessableEntity)

                    playbackStateDb.deleteOne(
                        PlaybackState::userId eq session.userId,
                        PlaybackState::mediaReferenceId eq mediaRefId
                    )
                    playbackStateDb.insertOne(state)
                    call.respond(OK)
                }
            }

            val videoFileCache = ConcurrentHashMap<String, File>()
            get("/direct") {
                val mediaRefId = call.parameters["mediaRefId"]!!
                val file = if (videoFileCache.containsKey(mediaRefId)) {
                    videoFileCache[mediaRefId]!!
                } else {
                    val mediaRef = mediaRefs.find(MediaReference::id eq mediaRefId).first()
                        ?: return@get call.respond(NotFound)

                    when (mediaRef) {
                        is LocalMediaReference -> mediaRef.filePath
                        is DownloadMediaReference -> mediaRef.filePath
                    }?.run(::File)
                } ?: return@get call.respond(NotFound)

                if (file.name.endsWith(".mp4")) {
                    videoFileCache.putIfAbsent(mediaRefId, file)
                    call.respond(LocalFileContent(file))
                } else {
                    val name = UUID.randomUUID().toString()
                    val outfile = File(transcodePath, "$name.mp4")

                    ffmpeg().addInput(UrlInput.fromPath(file.toPath()))
                        .addOutput(UrlOutput.toPath(outfile.toPath()).copyAllCodecs())
                        .setOverwriteOutput(true)
                        .execute()
                    videoFileCache.putIfAbsent(mediaRefId, outfile)
                    call.respond(LocalFileContent(outfile))
                }
            }

            route("/hls") {
                get("/playlist.m3u8") {
                    val mediaRefId = call.parameters["mediaRefId"]!!
                    val mediaRef = mediaRefs.findOneById(mediaRefId)
                        ?: return@get call.respond(NotFound)
                    val token = call.parameters["token"]
                        ?: return@get call.respond(Unauthorized)

                    val file = when (mediaRef) {
                        is LocalMediaReference -> mediaRef.filePath
                        is DownloadMediaReference -> mediaRef.filePath
                    }?.run(::File) ?: return@get call.respond(NotFound)


                    val runtime = Duration.Companion.seconds(streamManager.getFileDuration(file))
                    if (!streamManager.hasSession(token)) {
                        val output = File("$transcodePath/$mediaRefId/$token")
                        streamManager.startTranscode(
                            token = token,
                            name = mediaRefId,
                            mediaFile = file,
                            outputDir = output,
                            runtime = runtime,
                        )
                    }

                    val playlist = streamManager.createVariantPlaylist(
                        name = mediaRefId,
                        mediaFile = file,
                        token = token,
                        runtime = runtime,
                    )
                    call.respond(playlist)
                }

                get("/{segmentFile}") {
                    val segmentFile = call.parameters["segmentFile"]
                        ?: return@get call.respond(NotFound)
                    val token = call.request.queryParameters["token"]
                        ?: return@get call.respond(Unauthorized)
                    val session = streamManager.getSession(token)
                        ?: return@get call.respond(NotFound)

                    val segmentIndex = segmentFile.substringAfter(session.mediaRefId)
                        .substringBefore(".ts")
                        .toInt()
                    streamManager.setSegmentTarget(token, segmentIndex)
                    val output = File("${session.outputPath}/$segmentFile")
                    if (output.exists()) {
                        call.respond(LocalFileContent(output, ContentType.Application.OctetStream))
                    } else {
                        call.respond(NotFound)
                    }
                }
            }
        }

        get("/stop/{token}") {
            val token = call.parameters["token"]!!
            streamManager.stopSession(token, call.parameters["delete"]?.toBoolean() ?: false)
            call.respond(OK)
        }
    }
}

fun Route.addStreamWsRoutes(
    streamManager: StreamManager,
    mongodb: CoroutineDatabase,
) {
    val playbackStateDb = mongodb.getCollection<PlaybackState>()
    val mediaRefs = mongodb.getCollection<MediaReference>()
    val transcodePath = application.environment.config.property("app.transcodePath").getString()

    webSocket("/ws/stream/{mediaRefId}/state") {
        val userId = (incoming.receive() as Frame.Text).readText()
        val mediaRefId = call.parameters["mediaRefId"]!!
        val mediaRef = mediaRefs.findOneById(mediaRefId)!!
        val file = when (mediaRef) {
            is LocalMediaReference -> mediaRef.filePath
            is DownloadMediaReference -> mediaRef.filePath
        }?.run(::File) ?: return@webSocket close()

        val runtime = streamManager.getFileDuration(file)

        val state = playbackStateDb.findOne(
            PlaybackState::userId eq userId,
            PlaybackState::mediaReferenceId eq mediaRefId
        ) ?: PlaybackState(
            id = ObjectId.get().toString(),
            mediaReferenceId = mediaRefId,
            position = 0.0,
            userId = userId,
            mediaId = mediaRef.contentId,
            runtime = runtime,
            updatedAt = Instant.now().toEpochMilli(),
        ).also {
            playbackStateDb.insertOne(it)
        }

        if (!streamManager.hasSession(state.id)) {
            val output = File("$transcodePath/$mediaRefId/${state.id}")
            streamManager.startTranscode(
                token = state.id,
                name = mediaRefId,
                mediaFile = file,
                outputDir = output,
                runtime = Duration.seconds(runtime),
                startAt = state.position,
            )
        }

        send(Frame.Text(json.encodeToString(state)))

        val finalPosition = incoming.receiveAsFlow()
            .takeWhile { it !is Frame.Close }
            .filterIsInstance<Frame.Text>()
            .map { frame ->
                val newState = json.decodeFromString<PlaybackState>(frame.readText())
                playbackStateDb.updateOne(
                    filter = PlaybackState::id eq newState.id,
                    update = combine(
                        setValue(PlaybackState::position, newState.position),
                        setValue(PlaybackState::updatedAt, Instant.now().toEpochMilli()),
                    )
                )
                newState.position
            }
            .lastOrNull() ?: state.position

        val completePercent = ((finalPosition / runtime) * 100).roundToInt()
        val isComplete = completePercent >= PLAYBACK_COMPLETE_PERCENT
        if (isComplete) {
            try {
                playbackStateDb.deleteOneById(state.id)
            } catch (e: MongoException) {
                e.printStackTrace()
            }
        }
        streamManager.stopSession(state.id, isComplete)
    }
}
