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

import anystream.AnyStreamConfig
import anystream.data.MetadataDbQueries
import anystream.db.*
import anystream.db.extensions.pooled
import anystream.media.LibraryManager
import anystream.media.processor.MovieFileProcessor
import anystream.media.processor.TvFileProcessor
import anystream.metadata.MetadataManager
import anystream.metadata.MetadataProvider
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.Permission
import anystream.service.search.SearchService
import anystream.service.stream.StreamService
import anystream.service.stream.StreamServiceQueriesJdbi
import anystream.service.user.UserService
import anystream.service.user.UserServiceQueriesJdbi
import app.moviebase.tmdb.Tmdb3
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kjob.core.job.JobExecutionType
import kjob.core.kjob
import kjob.jdbi.JdbiKJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.drewcarlson.ktor.permissions.withAnyPermission
import org.drewcarlson.ktor.permissions.withPermission
import org.jdbi.v3.core.Jdbi
import qbittorrent.QBittorrentClient
import torrentsearch.TorrentSearch
import java.nio.file.Path

fun Application.installRouting(jdbi: Jdbi, config: AnyStreamConfig) {
    val kjob = kjob(JdbiKJob) {
        this.jdbi = jdbi
        defaultJobExecutor = JobExecutionType.NON_BLOCKING
    }.start()
    environment.monitor.subscribe(ApplicationStopped) { kjob.shutdown() }
    val tmdb by lazy { Tmdb3(config.tmdbApiKey) }

    val torrentSearch = TorrentSearch()

    val qbClient = QBittorrentClient(
        baseUrl = config.qbittorrentUrl,
        username = config.qbittorrentUser,
        password = config.qbittorrentPass,
    )
    val ffmpeg = { FFmpeg.atPath(Path.of(config.ffmpegPath)) }
    val ffprobe = { FFprobe.atPath(Path.of(config.ffmpegPath)) }

    val mediaDao = jdbi.pooled<MetadataDao>()
    val tagsDao = jdbi.pooled<TagsDao>()
    val playbackStatesDao = jdbi.pooled<PlaybackStatesDao>()
    val mediaLinkDao = jdbi.pooled<MediaLinkDao>()
    val usersDao = jdbi.pooled<UsersDao>()
    val invitesDao = jdbi.pooled<InvitesDao>()
    val permissionsDao = jdbi.pooled<PermissionsDao>()
    val searchableContentDao = jdbi.pooled<SearchableContentDao>().apply { createTable() }

    val queries = MetadataDbQueries(searchableContentDao, mediaDao, tagsDao, mediaLinkDao, playbackStatesDao)

    val providers = listOf<MetadataProvider>(
        TmdbMetadataProvider(tmdb, queries)
    )
    val metadataManager = MetadataManager(providers)

    val processors = listOf(
        MovieFileProcessor(metadataManager, queries),
        TvFileProcessor(metadataManager, queries),
    )
    val libraryManager = LibraryManager(ffprobe, processors, mediaLinkDao)

    val userService = UserService(UserServiceQueriesJdbi(usersDao, permissionsDao, invitesDao))

    val streamQueries = StreamServiceQueriesJdbi(usersDao, mediaDao, mediaLinkDao, playbackStatesDao)
    val streamService = StreamService(this, streamQueries, ffmpeg, ffprobe, config.transcodePath)
    val searchService = SearchService(searchableContentDao, mediaDao, mediaLinkDao)

    installWebClientRoutes(config)

    routing {
        route("/api") {
            addUserRoutes(userService)
            authenticate {
                addHomeRoutes(tmdb, queries)
                withAnyPermission(Permission.ViewCollection) {
                    addImageRoutes(config.dataPath)
                    addTvShowRoutes(queries)
                    addMovieRoutes(queries)
                    addSearchRoutes(searchService)
                    addMediaViewRoutes(metadataManager, queries)
                }
                withAnyPermission(Permission.ManageTorrents) {
                    addTorrentRoutes(qbClient, mediaLinkDao)
                }
                withAnyPermission(Permission.ManageCollection) {
                    addMediaManageRoutes(libraryManager, queries)
                }
                withPermission(Permission.ConfigureSystem) {
                    addAdminRoutes()
                }
            }

            addStreamRoutes(streamService)
            addStreamWsRoutes(streamService)
            addTorrentWsRoutes(qbClient)
            addUserWsRoutes(userService)
            addAdminWsRoutes(libraryManager, streamService)
        }
    }
}
