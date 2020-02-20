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
import anystream.models.LocalMediaReference
import anystream.models.DownloadMediaReference
import anystream.models.MediaReference
import anystream.models.PlaybackState
import com.github.kokorin.jaffree.ffmpeg.*
import drewcarlson.qbittorrent.QBittorrentClient
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
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
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.replaceOne
import org.litote.kmongo.eq
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.*


fun Route.addStreamRoutes(
    qbClient: QBittorrentClient,
    mongodb: CoroutineDatabase,
    ffmpeg: FFmpeg,
) {
    val transcodePath = application.environment.config.property("app.transcodePath").getString()
    val playbackStateDb = mongodb.getCollection<PlaybackState>()
    val mediaRefs = mongodb.getCollection<MediaReference>()
    route("/stream") {
        route("/{media_ref_id}") {
            route("/state") {
                get {
                    val session = call.principal<UserSession>()!!
                    val mediaRefId = call.parameters["media_ref_id"]!!
                    val state = playbackStateDb.findOne(
                        PlaybackState::userId eq session.userId,
                        PlaybackState::mediaReferenceId eq mediaRefId
                    )
                    call.respond(state ?: NotFound)
                }
                put {
                    val session = call.principal<UserSession>()!!
                    val mediaRefId = call.parameters["media_ref_id"]!!
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
            @Suppress("BlockingMethodInNonBlockingContext")
            get("/direct") {
                val mediaRefId = call.parameters["media_ref_id"]!!
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

                    ffmpeg.addInput(UrlInput.fromPath(file.toPath()))
                        .addOutput(UrlOutput.toPath(outfile.toPath()).copyAllCodecs())
                        .setOverwriteOutput(true)
                        .execute()
                    videoFileCache.putIfAbsent(mediaRefId, outfile)
                    call.respond(LocalFileContent(outfile))
                }
            }
        }
    }
}

fun Route.addStreamWsRoutes(
    qbClient: QBittorrentClient,
    mongodb: CoroutineDatabase
) {
    val playbackStateDb = mongodb.getCollection<PlaybackState>()
    val mediaRefs = mongodb.getCollection<MediaReference>()

    webSocket("/ws/stream/{media_ref_id}/state") {
        val userId = (incoming.receive() as Frame.Text).readText()
        val mediaRefId = call.parameters["media_ref_id"]!!
        val mediaRef = mediaRefs.findOneById(mediaRefId)!!
        val state = playbackStateDb.findOne(
            PlaybackState::userId eq userId,
            PlaybackState::mediaReferenceId eq mediaRefId
        ) ?: PlaybackState(
            id = ObjectId.get().toString(),
            mediaReferenceId = mediaRefId,
            position = 0,
            userId = userId,
            mediaId = mediaRef.contentId,
            updatedAt = Instant.now().toEpochMilli()
        ).also {
            playbackStateDb.insertOne(it)
        }

        send(Frame.Text(json.encodeToString(state)))

        incoming.receiveAsFlow()
            .takeWhile { !outgoing.isClosedForSend }
            .filterIsInstance<Frame.Text>()
            .collect { frame ->
                val newState = json.decodeFromString<PlaybackState>(frame.readText())
                playbackStateDb.replaceOne(newState.copy(updatedAt = Instant.now().toEpochMilli()))
            }
    }
}
