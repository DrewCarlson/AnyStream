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
import anystream.torrent.search.KMongoTorrentProviderCache
import anystream.util.SinglePageApp
import anystream.util.withAnyPermission
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import drewcarlson.qbittorrent.QBittorrentClient
import drewcarlson.torrentsearch.TorrentSearch
import info.movito.themoviedbapi.TmdbApi
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
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
    val ffmpeg = FFmpeg.atPath(Path.of(ffmpegPath))

    val mediaRefs = mongodb.getCollection<MediaReference>()

    val processors = listOf(
        MovieImportProcessor(tmdb, mongodb, log),
        TvImportProcessor(tmdb, mongodb, log),
    )
    val importer = MediaImporter(tmdb, processors, mediaRefs, log)

    routing {
        route("/api") {
            addUserRoutes(mongodb)
            authenticate {
                addHomeRoutes(tmdb, mongodb)
                withAnyPermission(Permissions.GLOBAL, Permissions.VIEW_COLLECTION) {
                    addTvShowRoutes(tmdb, mongodb)
                    addMovieRoutes(tmdb, mongodb)
                }
                withAnyPermission(Permissions.GLOBAL, Permissions.TORRENT_MANAGEMENT) {
                    addTorrentRoutes(qbClient, mongodb)
                }
                withAnyPermission(Permissions.GLOBAL, Permissions.MANAGE_COLLECTION) {
                    addMediaRoutes(tmdb, mongodb, torrentSearch, importer)
                }
            }

            // TODO: WS endpoint Authentication and permissions
            addStreamRoutes(qbClient, mongodb, ffmpeg)
            addStreamWsRoutes(qbClient, mongodb)
            addTorrentWsRoutes(qbClient)
            addUserWsRoutes(mongodb)
        }
    }

    install(SinglePageApp) {
        ignoreBasePath = "/api"
        staticFilePath = frontEndPath
    }
}
