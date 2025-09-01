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
import anystream.db.converter.JooqConverterProvider
import anystream.jobs.registerJobs
import anystream.media.LibraryService
import anystream.media.analyzer.MediaFileAnalyzer
import anystream.media.processor.MovieFileProcessor
import anystream.media.processor.TvFileProcessor
import anystream.metadata.ImageStore
import anystream.metadata.MetadataService
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.Permission
import anystream.routes.installRouting
import anystream.service.search.SearchService
import anystream.service.stream.*
import anystream.service.user.UserService
import anystream.util.SqlSessionStorage
import anystream.util.WebsocketAuthorization
import anystream.util.headerOrQuery
import app.moviebase.tmdb.Tmdb3
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import com.typesafe.config.ConfigFactory
import io.ktor.client.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bouncycastle.util.encoders.Hex
import org.drewcarlson.ktor.permissions.PermissionAuthorization
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.koin.ktor.ext.get
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koinModule
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level
import org.sqlite.SQLiteConfig
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import qbittorrent.QBittorrentClient
import java.nio.file.FileSystems
import javax.sql.DataSource
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


suspend fun main(args: Array<String>) {
    val defaultConfig = ConfigFactory.load()
    val userConfig = args
        .firstOrNull { it.startsWith("-config=") }
        ?.substringAfter("=")
        ?.let { ConfigFactory.parseFile(java.io.File(it)) }
    val mergedConfig = if (userConfig == null) {
        defaultConfig
    } else {
        userConfig.withFallback(defaultConfig).resolve()
    }
    val appConfig = HoconApplicationConfig(mergedConfig)

    embeddedServer(Netty, environment = applicationEnvironment {
        config = appConfig
    }, {
        // TODO: this prevents user config files from setting engine config, is this a problem?
        val config = CommandLineConfig(args.filter { !it.startsWith("-config") }.toTypedArray())
        takeFrom(config.engineConfig)
        loadCommonConfiguration(appConfig.config("ktor.deployment"))
    }).startSuspend(wait = true)
}

@Suppress("unused", "UNUSED_PARAMETER") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Koin) {
        slf4jLogger()
    }

    val applicationScope = this as CoroutineScope
    koinModule {
        val fs = FileSystems.getDefault()
        val config = AnyStreamConfig(environment.config, fs)
        single { config }
        single { applicationScope }
        single { fs }

        factory { FFmpeg.atPath(config.ffmpegPath) }
        factory { FFprobe.atPath(config.ffmpegPath) }

        single<DataSource> {
            SQLiteConnectionPoolDataSource().apply {
                url = config.databaseUrl
                this.config = SQLiteConfig().apply {
                    enforceForeignKeys(true)
                    setJournalMode(SQLiteConfig.JournalMode.WAL)
                    setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
                }
            }
        }

        single { SqlSessionStorage(get(), get()) }

        single { Tmdb3(config.tmdbApiKey) }

        single {
            QBittorrentClient(
                baseUrl = config.qbittorrent.url,
                username = config.qbittorrent.user,
                password = config.qbittorrent.password,
            )
        }

        /*single {
            kjob(JdbiKJob) {
                this.jdbi = get()
                defaultJobExecutor = JobExecutionType.NON_BLOCKING
            }.apply { start() }
        }*/

        single { SessionsDao(get()) }
        single { MetadataDao(get()) }
        single { UserDao(get()) }
        single { LibraryDao(get()) }
        single { InviteCodeDao(get()) }
        single { TagsDao(get()) }
        single { PlaybackStatesDao(get()) }
        single { MediaLinkDao(get()) }
        single { SearchableContentDao(get()) }
        single { MetadataDbQueries(get(), get(), get(), get(), get()) }

        single {
            HttpClient {
                install(HttpCache) {
                    // TODO: Add disk catching
                    publicStorage(CacheStorage.Unlimited())
                }
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json()
                }
            }
        }

        single {
            ImageStore(
                dataPath = get<AnyStreamConfig>().dataPath,
                httpClient = get<HttpClient>(),
            )
        }
        single { MetadataService(listOf(TmdbMetadataProvider(get(), get(), get())), get(), get()) }
        single { MediaFileAnalyzer({ get() }, get()) }
        single<LibraryService> {
            val processors = listOf(
                MovieFileProcessor(get(), get(), get(), get()),
                TvFileProcessor(get(), get(), get(), get()),
            )
            LibraryService(get(), processors, get(), get(), get())
        }
        single {
            val dbConfig = DefaultConfiguration().apply {
                setDataSource(get())
                setSQLDialect(SQLDialect.SQLITE)
                set(JooqConverterProvider())
            }
            DSL.using(dbConfig)
        }
        single { UserService(get(), get(), get()) }

        single<StreamServiceQueries> { StreamServiceQueriesJooq(get(), get(), get(), get(), get()) }
        single { MediaFileProbe({ get() }) }
        single { TranscodeSessionManager({ get() }, get(), get(), get()) }
        single {
            StreamService(
                queries = get(),
                mediaFileProbe = get(),
                transcodeSessionManager = get(),
                transcodePath = get<AnyStreamConfig>().transcodePath,
                fs = get(),
            )
        }
        single { SearchService(get(), get(), get()) }
    }
    val config = get<AnyStreamConfig>()
    monitor.subscribe(ApplicationStopped) {
        //get<KJob>().shutdown()
    }

    check(runMigrations(get<AnyStreamConfig>().databaseUrl, log))
    applicationScope.launch {
        get<LibraryDao>().insertDefaultLibraries()
    }
    registerJobs()

    install(DefaultHeaders) {}
    install(ContentNegotiation) {
        json(json)
    }
    install(AutoHeadResponse)
    install(ConditionalHeaders)
    install(PartialContent)
    install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
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
        pingPeriod = 60.seconds
        timeout = 15.seconds
        contentConverter = KotlinxWebsocketSerializationConverter(json)
    }

    val httpClient = getKoin().get<HttpClient>()
    install(Authentication) {
        session<UserSession> {
            challenge { call.respond(Unauthorized) }
            validate { it }
        }
        if (config.oidc.enable) {
            oauth(config.oidc.provider.name) {
                client = httpClient
                urlProvider = {
                    buildString {
                        append(request.origin.scheme)
                        append("://")
                        append(config.baseUrl.trimEnd('/'))
                        append("/api/users/oidc/callback")
                    }
                }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = config.oidc.provider.name,
                        authorizeUrl = config.oidc.provider.authorizeUrl,
                        accessTokenUrl = config.oidc.provider.accessTokenUrl,
                        requestMethod = HttpMethod.Post,
                        clientId = config.oidc.provider.clientId,
                        clientSecret = config.oidc.provider.clientSecret,
                        defaultScopes = config.oidc.provider.scopes,
                    )
                }
            }
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
