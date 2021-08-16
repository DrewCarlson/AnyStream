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

import anystream.media.MediaImporter
import anystream.media.processor.MovieImportProcessor
import anystream.media.processor.TvImportProcessor
import anystream.models.*
import anystream.stream.StreamManager
import anystream.torrent.search.KMongoTorrentProviderCache
import anystream.util.SinglePageApp
import anystream.util.withAnyPermission
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import com.mongodb.MongoException
import drewcarlson.qbittorrent.QBittorrentClient
import drewcarlson.torrentsearch.TorrentSearch
import info.movito.themoviedbapi.TmdbApi
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.textIndex
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

    val mediaRefs = mongodb.getCollection<MediaReference>()

    val importScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val processors = listOf(
        MovieImportProcessor(tmdb, mongodb, log),
        TvImportProcessor(tmdb, mongodb, importScope, log),
    )
    val importer = MediaImporter(tmdb, ffprobe, processors, mediaRefs, importScope, log)

    val streamManager = StreamManager(ffmpeg, ffprobe, log)

   runBlocking {
       try {
           mongodb.getCollection<Movie>()
               .ensureIndex(Movie::title.textIndex())
           mongodb.getCollection<TvShow>()
               .ensureIndex(TvShow::name.textIndex())
           mongodb.getCollection<Episode>()
               .ensureIndex(Episode::name.textIndex())
       } catch (e: MongoException) {
           log.error("Failed to create search indexes", e)
       }
   }

    routing {
        route("/api") {
            addUserRoutes(mongodb)
            authenticate {
                addHomeRoutes(tmdb, mongodb)
                withAnyPermission(Permissions.VIEW_COLLECTION) {
                    addTvShowRoutes(tmdb, mongodb)
                    addMovieRoutes(tmdb, mongodb)
                    addSearchRoutes(tmdb, mongodb)
                }
                withAnyPermission(Permissions.TORRENT_MANAGEMENT) {
                    addTorrentRoutes(qbClient, mongodb)
                }
                withAnyPermission(Permissions.MANAGE_COLLECTION) {
                    addMediaRoutes(tmdb, mongodb, torrentSearch, importer)
                }
            }

            // TODO: WS endpoint Authentication and permissions
            addStreamRoutes(streamManager, mongodb, ffmpeg)
            addStreamWsRoutes(streamManager, mongodb)
            addTorrentWsRoutes(qbClient)
            addUserWsRoutes(mongodb)
        }
    }

    install(SinglePageApp) {
        ignoreBasePath = "/api"
        staticFilePath = frontEndPath
    }
}
