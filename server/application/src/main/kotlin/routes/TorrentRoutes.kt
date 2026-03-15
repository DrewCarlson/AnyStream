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
import anystream.db.MediaLinkDao
import anystream.di.ServerScope
import anystream.json
import anystream.models.Permission
import anystream.torrent.search.TorrentDescription2
import anystream.util.extractUserSession
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.plugins.*
import io.ktor.client.utils.*
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.*
import org.drewcarlson.ktor.permissions.withAnyPermission
import qbittorrent.QBittorrentClient
import qbittorrent.QBittorrentException
import qbittorrent.models.TorrentFile

@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<RoutingController>(),
)
@Inject
class TorrentRoutes(
    private val qbtClient: QBittorrentClient,
    private val mediaLinkDao: MediaLinkDao,
) : RoutingController {
    override fun init(parent: Route) {
        parent.apply {
            authenticate {
                withAnyPermission(Permission.ManageTorrents) {
                    route("/torrents") {
                        get { getTorrents() }

                        post { addTorrent() }

                        route("/global") {
                            get { getGlobal() }
                        }

                        route("/{hash}") {
                            get("/files") { getTorrentFiles() }

                            get("/pause") { getPauseTorrent() }

                            get("/resume") { getResumeTorrent() }

                            delete { deleteTorrent() }
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
            }

            webSocket("/ws/torrents/observe") { observeTorrents() }
            webSocket("/ws/torrents/global") { observeGlobal() }
        }
    }

    private suspend fun RoutingContext.getTorrents() {
        val response = try {
            qbtClient.getTorrents()
        } catch (_: Exception) {
            emptyList()
        }
        call.respond(response)
    }

    private suspend fun RoutingContext.addTorrent() {
        val session = checkNotNull(call.principal<UserSession>())
        val description = runCatching { call.receiveNullable<TorrentDescription2>() }
            .getOrNull() ?: return call.respond(UnprocessableEntity)
        try {
            qbtClient.getTorrentProperties(description.hash)
        } catch (e: ResponseException) {
            if (e.response.status == NotFound) {
                return call.respond(Conflict)
            } else {
                throw e
            }
        }
        qbtClient.addTorrent {
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

    private suspend fun RoutingContext.getGlobal() {
        try {
            call.respond(qbtClient.getGlobalTransferInfo())
        } catch (e: QBittorrentException) {
            call.respond(EmptyContent)
        }
    }

    private suspend fun RoutingContext.getTorrentFiles() {
        val hash = call.parameters["hash"]!!
        call.respond(qbtClient.getTorrentFiles(hash))
    }

    private suspend fun RoutingContext.getPauseTorrent() {
        val hash = call.parameters["hash"]!!
        qbtClient.pauseTorrents(listOf(hash))
        call.respond(OK)
    }

    private suspend fun RoutingContext.getResumeTorrent() {
        val hash = call.parameters["hash"]!!
        qbtClient.resumeTorrents(listOf(hash))
        call.respond(OK)
    }

    private suspend fun RoutingContext.deleteTorrent() {
        val hash = call.parameters["hash"]!!
        val deleteFiles = call.request.queryParameters["deleteFiles"]!!.toBoolean()
        qbtClient.deleteTorrents(listOf(hash), deleteFiles = deleteFiles)
        mediaLinkDao.deleteDownloadByHash(hash)
        call.respond(OK)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun DefaultWebSocketServerSession.observeTorrents() {
        val session = checkNotNull(extractUserSession())
        check(Permission.check(Permission.ManageTorrents, session.permissions))
        qbtClient
            .observeMainData()
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

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun DefaultWebSocketServerSession.observeGlobal() {
        val session = checkNotNull(extractUserSession())
        check(Permission.check(Permission.ManageTorrents, session.permissions))
        qbtClient
            .observeMainData()
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
