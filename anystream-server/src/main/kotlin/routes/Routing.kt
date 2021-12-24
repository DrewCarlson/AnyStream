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

import anystream.data.MediaDbQueries
import anystream.db.*
import anystream.media.MediaImporter
import anystream.media.processor.MovieImportProcessor
import anystream.media.processor.TvImportProcessor
import anystream.metadata.MetadataManager
import anystream.metadata.MetadataProvider
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.*
import anystream.service.stream.StreamService
import anystream.service.stream.StreamServiceQueriesJdbi
import anystream.service.user.UserService
import anystream.service.user.UserServiceQueriesJdbi
import anystream.util.SinglePageApp
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import drewcarlson.qbittorrent.QBittorrentClient
import drewcarlson.torrentsearch.TorrentSearch
import info.movito.themoviedbapi.TmdbApi
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.drewcarlson.ktor.permissions.withAnyPermission
import org.drewcarlson.ktor.permissions.withPermission
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.transaction.TransactionException
import org.jdbi.v3.sqlobject.kotlin.attach
import java.io.File
import java.nio.file.Path

fun Application.installRouting(dbHandle: Handle) {
    val webClientPath = environment.config.propertyOrNull("app.webClientPath")?.getString()
    val ffmpegPath = environment.config.property("app.ffmpegPath").getString()
    val tmdbApiKey = environment.config.property("app.tmdbApiKey").getString()
    val qbittorrentUrl = environment.config.property("app.qbittorrentUrl").getString()
    val qbittorrentUser = environment.config.property("app.qbittorrentUser").getString()
    val qbittorrentPass = environment.config.property("app.qbittorrentPassword").getString()

    val tmdb by lazy { TmdbApi(tmdbApiKey) }

    val torrentSearch = TorrentSearch()

    val qbClient = QBittorrentClient(
        baseUrl = qbittorrentUrl,
        username = qbittorrentUser,
        password = qbittorrentPass,
    )
    val ffmpeg = { FFmpeg.atPath(Path.of(ffmpegPath)) }
    val ffprobe = { FFprobe.atPath(Path.of(ffmpegPath)) }

    val mediaDao = dbHandle.attach<MediaDao>().apply { createTable() }
    val playbackStatesDao = dbHandle.attach<PlaybackStatesDao>().apply { createTable() }
    val mediaReferencesDao = dbHandle.attach<MediaReferencesDao>().apply { createTable() }
    val usersDao = dbHandle.attach<UsersDao>().apply { createTable() }
    val invitesDao = dbHandle.attach<InvitesDao>().apply { createTable() }
    val permissionsDao = dbHandle.attach<PermissionsDao>().apply { createTable() }
    val searchableContentDao = dbHandle.attach<SearchableContentDao>().apply { createTable() }

    val queries = MediaDbQueries(searchableContentDao, mediaDao, mediaReferencesDao, playbackStatesDao)

    val providers = listOf<MetadataProvider>(
        TmdbMetadataProvider(tmdb, queries)
    )
    val metadataManager = MetadataManager(providers, log)

    val importScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val processors = listOf(
        MovieImportProcessor(metadataManager, queries, log),
        TvImportProcessor(metadataManager, queries, importScope, log),
    )
    val importer = MediaImporter(ffprobe, processors, mediaReferencesDao, importScope, log)

    val userService = UserService(UserServiceQueriesJdbi(usersDao, permissionsDao, invitesDao))

    val transcodePath = environment.config.property("app.transcodePath").getString()
    val streamQueries = StreamServiceQueriesJdbi(usersDao, mediaDao, mediaReferencesDao, playbackStatesDao)
    val streamService = StreamService(streamQueries, ffmpeg, ffprobe, transcodePath)

    routing {
        route("/api") {
            addUserRoutes(userService)
            authenticate {
                addHomeRoutes(tmdb, queries)
                withAnyPermission(Permission.ViewCollection) {
                    addTvShowRoutes(queries)
                    addMovieRoutes(queries)
                    addSearchRoutes(searchableContentDao, mediaDao, mediaReferencesDao)
                    addMediaViewRoutes(metadataManager, queries)
                }
                withAnyPermission(Permission.ManageTorrents) {
                    addTorrentRoutes(qbClient, mediaReferencesDao)
                }
                withAnyPermission(Permission.ManageCollection) {
                    addMediaManageRoutes(tmdb, torrentSearch, importer, queries)
                }
                withPermission(Permission.ConfigureSystem) {
                    addAdminRoutes()
                }
            }

            addStreamRoutes(streamService)
            addStreamWsRoutes(streamService)
            addTorrentWsRoutes(qbClient)
            addUserWsRoutes(userService)
            addAdminWsRoutes()
        }
    }

    when {
        webClientPath.isNullOrBlank() ->
            log.debug("No web client path provided, this instance will serve the API only.")
        !File(webClientPath).exists() ->
            log.error("Specified web client path is empty: $webClientPath")
        else -> {
            log.debug("This instance will serve the web client from '$webClientPath'.")
            install(SinglePageApp) {
                ignoreBasePath = "/api"
                staticFilePath = webClientPath
            }
        }
    }
}
