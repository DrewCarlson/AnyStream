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
package anystream.routes

import anystream.data.UserSession
import anystream.db.MediaLinkDao
import anystream.json
import anystream.models.Permission
import anystream.torrent.search.TorrentDescription2
import anystream.util.extractUserSession
import anystream.util.koinGet
import io.ktor.client.plugins.*
import io.ktor.client.utils.*
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import qbittorrent.QBittorrentClient
import qbittorrent.QBittorrentException
import qbittorrent.models.TorrentFile

fun Route.addTorrentRoutes(
    qbClient: QBittorrentClient = koinGet(),
    mediaLinkDao: MediaLinkDao = koinGet(),
) {
    route("/torrents") {
        get {
            val response = try {
                qbClient.getTorrents()
            } catch (e: Exception) {
                emptyList()
            }
            call.respond(response)
        }

        post {
            val session = checkNotNull(call.principal<UserSession>())
            val description = runCatching { call.receiveNullable<TorrentDescription2>() }
                .getOrNull() ?: return@post call.respond(UnprocessableEntity)
            try {
                qbClient.getTorrentProperties(description.hash)
            } catch (e: ResponseException) {
                if (e.response.status == NotFound) {
                    return@post call.respond(Conflict)
                } else {
                    throw e
                }
            }
            qbClient.addTorrent {
                urls.add(description.magnetUrl)
                savepath = "/downloads"
                sequentialDownload = true
                firstLastPiecePriority = true
            }
            call.respond(OK)

            /*val movieId = call.parameters["movieId"] ?: return@post
            val downloadId = ObjectId.get().toString()
            mediaLinkDao.insertLink(
                MediaReferenceDb.fromRefModel(
                    DownloadMediaReference(
                        id = downloadId,
                        metadataId = movieId,
                        hash = description.hash,
                        added = Instant.now().toEpochMilli(),
                        fileIndex = null,
                        filePath = null,
                        mediaKind = MediaKind.MOVIE,
                    )
                )
            )
            qbClient.torrentFlow(description.hash)
                .dropWhile { it.state == Torrent.State.META_DL }
                .mapNotNull { torrent ->
                    qbClient.getTorrentFiles(torrent.hash)
                        .filter(videoFile)
                        .maxByOrNull(TorrentFile::size)
                        ?.run { this to torrent }
                }
                .take(1)
                .onEach { // (file, torrent) ->
                    // TODO: Update download reference details
                    /*val download = mediaLinks.findOneById(downloadId) as DownloadMediaReference
                    mediaLinks.updateOneById(
                        downloadId,
                        download.copy(
                            fileIndex = file.id,
                            filePath = "${torrent.savePath}/${file.name}"
                        )
                    )*/
                }
                .launchIn(application)*/
        }

        route("/global") {
            get {
                try {
                    call.respond(qbClient.getGlobalTransferInfo())
                } catch (e: QBittorrentException) {
                    call.respond(EmptyContent)
                }
            }
        }

        route("/{hash}") {
            get("/files") {
                val hash = call.parameters["hash"]!!
                call.respond(qbClient.getTorrentFiles(hash))
            }

            get("/pause") {
                val hash = call.parameters["hash"]!!
                qbClient.pauseTorrents(listOf(hash))
                call.respond(OK)
            }

            get("/resume") {
                val hash = call.parameters["hash"]!!
                qbClient.resumeTorrents(listOf(hash))
                call.respond(OK)
            }

            delete {
                val hash = call.parameters["hash"]!!
                val deleteFiles = call.request.queryParameters["deleteFiles"]!!.toBoolean()
                qbClient.deleteTorrents(listOf(hash), deleteFiles = deleteFiles)
                mediaLinkDao.deleteDownloadByHash(hash)
                call.respond(OK)
            }
        }

        /*route("/quickstart") {
            get("/tmdb/{tmdb_id}") {
                val tmdbId = call.parameters["tmdb_id"]!!.toInt()
                val movie = tmdb.movies.getMovie(tmdbId, null)
                val results = torrentSearch.search(movie.title, Category.MOVIES)
                // TODO: Better quickstart behavior, consider file size and transcoding reqs
                val selection = results.maxByOrNull { it.seeds }!!

                if (qbClient.getTorrents().any { it.hash == selection.hash }) {
                    call.respond(selection.hash)
                } else {
                    qbClient.addTorrent {
                        urls.add(selection.magnetUrl)
                        savePath = "/downloads"
                        category = "movies"
                        rootFolder = true
                        sequentialDownload = true
                        firstLastPiecePriority = true
                    }
                    call.respond(selection.hash)
                }
            }
        }*/
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun Route.addTorrentWsRoutes(qbClient: QBittorrentClient = koinGet()) {
    webSocket("/ws/torrents/observe") {
        val session = checkNotNull(extractUserSession())
        check(Permission.check(Permission.ManageTorrents, session.permissions))
        qbClient.observeMainData()
            .takeWhile { !outgoing.isClosedForSend }
            .collect { data ->
                val changed = data.torrents.keys
                val removed = data.torrentsRemoved
                if (changed.isNotEmpty() || removed.isNotEmpty()) {
                    val listText = (changed + removed)
                        .distinct()
                        .joinToString(",")
                    send(Frame.Text(listText))
                }
            }
    }
    webSocket("/ws/torrents/global") {
        val session = checkNotNull(extractUserSession())
        check(Permission.check(Permission.ManageTorrents, session.permissions))
        qbClient.observeMainData()
            .takeWhile { !outgoing.isClosedForSend }
            .collect { data ->
                outgoing.send(Frame.Text(json.encodeToString(data.serverState)))
            }
    }
}

private val videoExtensions = listOf(".mp4", ".avi", ".mkv")
private val videoFile = { torrentFile: TorrentFile ->
    torrentFile.name.run {
        videoExtensions.any { endsWith(it) } && !contains("sample", true)
    }
}
