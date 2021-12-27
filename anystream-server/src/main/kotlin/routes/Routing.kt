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
import anystream.media.MediaImporter
import anystream.media.processor.MovieImportProcessor
import anystream.media.processor.TvImportProcessor
import anystream.metadata.MetadataManager
import anystream.metadata.MetadataProvider
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.*
import anystream.service.stream.StreamService
import anystream.service.stream.StreamServiceQueriesMongo
import anystream.service.user.UserService
import anystream.service.user.UserServiceQueriesMongo
import anystream.torrent.search.KMongoTorrentProviderCache
import anystream.util.SinglePageApp
import org.drewcarlson.ktor.permissions.withAnyPermission
import org.drewcarlson.ktor.permissions.withPermission
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import drewcarlson.qbittorrent.QBittorrentClient
import drewcarlson.torrentsearch.TorrentSearch
import info.movito.themoviedbapi.TmdbApi
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.io.File
import java.nio.file.Path

fun Application.installRouting(mongodb: CoroutineDatabase) {
    val webClientPath = environment.config.propertyOrNull("app.webClientPath")?.getString()
    val ffmpegPath = environment.config.property("app.ffmpegPath").getString()
    val tmdbApiKey = environment.config.property("app.tmdbApiKey").getString()
    val qbittorrentUrl = environment.config.property("app.qbittorrentUrl").getString()
    val qbittorrentUser = environment.config.property("app.qbittorrentUser").getString()
    val qbittorrentPass = environment.config.property("app.qbittorrentPassword").getString()

    val tmdb by lazy { TmdbApi(tmdbApiKey) }

    val torrentSearch = TorrentSearch(KMongoTorrentProviderCache(mongodb))

    val qbClient = QBittorrentClient(
        baseUrl = qbittorrentUrl,
        username = qbittorrentUser,
        password = qbittorrentPass,
    )
    val ffmpeg = { FFmpeg.atPath(Path.of(ffmpegPath)) }
    val ffprobe = { FFprobe.atPath(Path.of(ffmpegPath)) }

    val queries = MediaDbQueries(mongodb)
    launch { queries.createIndexes() }

    val mediaRefs = mongodb.getCollection<MediaReference>()

    val providers = listOf<MetadataProvider>(
        TmdbMetadataProvider(tmdb, queries)
    )
    val metadataManager = MetadataManager(providers, log)

    val importScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val processors = listOf(
        MovieImportProcessor(metadataManager, queries, log),
        TvImportProcessor(metadataManager, queries, importScope, log),
    )
    val importer = MediaImporter(ffprobe, processors, mediaRefs, importScope, log)

    val userService = UserService(UserServiceQueriesMongo(mongodb))

    val transcodePath = environment.config.property("app.transcodePath").getString()
    val streamService =
        StreamService(StreamServiceQueriesMongo(mongodb), ffmpeg, ffprobe, transcodePath)

    routing {
        route("/api") {
            addUserRoutes(userService)
            authenticate {
                addHomeRoutes(tmdb, queries)
                withAnyPermission(Permissions.VIEW_COLLECTION) {
                    addTvShowRoutes(queries)
                    addMovieRoutes(queries)
                    addSearchRoutes(tmdb, mongodb)
                    addMediaViewRoutes(metadataManager, queries)
                }
                withAnyPermission(Permissions.TORRENT_MANAGEMENT) {
                    addTorrentRoutes(qbClient, mongodb)
                }
                withAnyPermission(Permissions.MANAGE_COLLECTION) {
                    addMediaManageRoutes(tmdb, mongodb, torrentSearch, importer, queries)
                }
                withPermission(Permissions.CONFIGURE_SYSTEM) {
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
