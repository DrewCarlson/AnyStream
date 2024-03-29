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
package anystream

import anystream.data.MetadataDbQueries
import anystream.data.UserSession
import anystream.db.*
import anystream.db.extensions.PooledExtensions
import anystream.db.extensions.pooled
import anystream.db.mappers.registerMappers
import anystream.jobs.registerJobs
import anystream.media.LibraryManager
import anystream.media.processor.MovieFileProcessor
import anystream.media.processor.TvFileProcessor
import anystream.metadata.MetadataManager
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.Permission
import anystream.routes.installRouting
import anystream.service.search.SearchService
import anystream.service.stream.StreamService
import anystream.service.stream.StreamServiceQueries
import anystream.service.stream.StreamServiceQueriesJdbi
import anystream.service.user.UserService
import anystream.service.user.UserServiceQueriesJdbi
import anystream.util.SqlSessionStorage
import anystream.util.WebsocketAuthorization
import anystream.util.headerOrQuery
import app.moviebase.tmdb.Tmdb3
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kjob.core.KJob
import kjob.core.job.JobExecutionType
import kjob.core.kjob
import kjob.jdbi.JdbiKJob
import kotlinx.coroutines.CoroutineScope
import org.bouncycastle.util.encoders.Hex
import org.drewcarlson.ktor.permissions.PermissionAuthorization
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.statement.Slf4JSqlLogger
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level
import qbittorrent.QBittorrentClient
import java.nio.file.Path
import java.time.Duration
import kotlin.random.Random

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused", "UNUSED_PARAMETER") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Koin) {
        slf4jLogger()
    }

    val applicationScope = this as CoroutineScope
    koin {
        modules(
            org.koin.dsl.module {
                val config = AnyStreamConfig(environment.config)
                single { config }
                single { applicationScope }

                factory { FFmpeg.atPath(Path.of(config.ffmpegPath)) }
                factory { FFprobe.atPath(Path.of(config.ffmpegPath)) }

                single {
                    Jdbi.create(config.databaseUrl).apply {
                        setSqlLogger(Slf4JSqlLogger())
                        installPlugin(SqlObjectPlugin())
                        installPlugin(KotlinSqlObjectPlugin())
                        installPlugin(KotlinPlugin())
                        configure(PooledExtensions::class.java) {}
                        registerMappers()
                    }
                }

                single { SqlSessionStorage(get()) }

                single { Tmdb3(config.tmdbApiKey) }

                single {
                    QBittorrentClient(
                        baseUrl = config.qbittorrentUrl,
                        username = config.qbittorrentUser,
                        password = config.qbittorrentPass,
                    )
                }

                single {
                    kjob(JdbiKJob) {
                        this.jdbi = get()
                        defaultJobExecutor = JobExecutionType.NON_BLOCKING
                    }.apply { start() }
                }

                single { get<Jdbi>().pooled<SessionsDao>() }
                single { get<Jdbi>().pooled<MetadataDao>() }
                single { get<Jdbi>().pooled<TagsDao>() }
                single { get<Jdbi>().pooled<PlaybackStatesDao>() }
                single { get<Jdbi>().pooled<MediaLinkDao>() }
                single { get<Jdbi>().pooled<UsersDao>() }
                single { get<Jdbi>().pooled<InvitesDao>() }
                single { get<Jdbi>().pooled<PermissionsDao>() }
                single { get<Jdbi>().pooled<SearchableContentDao>().apply { createTable() } }
                single { MetadataDbQueries(get(), get(), get(), get(), get()) }

                single { MetadataManager(listOf(TmdbMetadataProvider(get(), get()))) }
                single {
                    val processors = listOf(
                        MovieFileProcessor(get(), get()),
                        TvFileProcessor(get(), get()),
                    )
                    LibraryManager({ get() }, processors, get(), get())
                }
                single { UserService(UserServiceQueriesJdbi(get(), get(), get())) }

                single<StreamServiceQueries> { StreamServiceQueriesJdbi(get(), get(), get(), get()) }
                single { StreamService(get(), get(), { get() }, { get() }, get<AnyStreamConfig>().transcodePath) }
                single { SearchService(get(), get(), get()) }
            },
        )
    }
    environment.monitor.subscribe(ApplicationStopped) {
        get<Jdbi>().configure(PooledExtensions::class.java) { extension ->
            extension.shutdown()
        }
        get<KJob>().shutdown()
    }

    check(runMigrations(get<AnyStreamConfig>().databaseUrl, log))
    registerJobs()

    install(DefaultHeaders) {}
    install(ContentNegotiation) {
        json(json)
    }
    install(AutoHeadResponse)
    install(ConditionalHeaders)
    install(PartialContent)
    // install(ForwardedHeaderSupport) WARNING: for security, do not include this if not behind a reverse proxy
    // install(XForwardedHeaderSupport) WARNING: for security, do not include this if not behind a reverse proxy
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
        excludeContentType(ContentType.Video.Any)
    }

    install(CORS) {
        methods.addAll(HttpMethod.DefaultMethods)
        allowNonSimpleContentTypes = true
        allowHeaders { true }
        exposeHeader(UserSession.KEY)
        anyHost()
    }

    install(CallLogging) {
        level = Level.TRACE
    }

    install(CachingHeaders) {
        options { _, outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                /*ContentType.Text.CSS -> CachingOptions(
                    CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60),
                    expires = null as? GMTDate?
                )*/
                else -> null
            }
        }
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60)
        timeout = Duration.ofSeconds(15)
        contentConverter = KotlinxWebsocketSerializationConverter(json)
    }

    install(Authentication) {
        session<UserSession> {
            challenge { call.respond(Unauthorized) }
            validate { it }
        }
    }
    val sessionStorage = get<SqlSessionStorage>()
    install(Sessions) {
        headerOrQuery(UserSession.KEY, sessionStorage, SqlSessionStorage.Serializer) {
            Hex.toHexString(Random.nextBytes(48))
        }
    }
    install(WebsocketAuthorization) {
        extractUserSession(sessionStorage::readSession)
    }
    install(PermissionAuthorization) {
        global(Permission.Global)
        extract { (it as UserSession).permissions }
    }
    installRouting()
}
