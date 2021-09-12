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
import anystream.stream.StreamManager
import anystream.torrent.search.KMongoTorrentProviderCache
import anystream.util.SinglePageApp
import anystream.util.withAnyPermission
import anystream.util.withPermission
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import drewcarlson.qbittorrent.QBittorrentClient
import drewcarlson.torrentsearch.TorrentSearch
import info.movito.themoviedbapi.TmdbApi
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.nio.file.Path

fun Application.installRouting(mongodb: CoroutineDatabase) {
    val frontEndPath = environment.config.property("app.frontEndPath").getString()
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

    val streamManager = StreamManager(ffmpeg, ffprobe, log)

    routing {
        route("/api") {
            addUserRoutes(mongodb)
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

            // TODO: WS endpoint Authentication and permissions
            addStreamRoutes(streamManager, queries, mongodb, ffmpeg)
            addStreamWsRoutes(streamManager, mongodb)
            addTorrentWsRoutes(qbClient)
            addUserWsRoutes(mongodb)
            addAdminWsRoutes()
        }
    }

    install(SinglePageApp) {
        ignoreBasePath = "/api"
        staticFilePath = frontEndPath
    }
}
