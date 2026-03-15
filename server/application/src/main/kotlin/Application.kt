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
package anystream

import anystream.data.UserSession
import anystream.db.*
import anystream.jobs.registerJobs
import anystream.models.MediaKind
import anystream.models.Permission
import anystream.routes.installRouting
import anystream.util.SqlSessionStorage
import anystream.util.WebsocketAuthorization
import anystream.util.headerOrQuery
import com.typesafe.config.ConfigFactory
import dev.zacsweers.metro.createGraphFactory
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
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.drewcarlson.ktor.permissions.PermissionAuthorization
import org.slf4j.event.Level
import java.nio.file.FileSystems
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

private val configFileSuffixes = listOf("conf", "yml", "yaml")

// TODO: add state expiration for incomplete auth flows
val oauthRedirectUrls = ConcurrentMap<String, String>()

val ServerGraphKey = AttributeKey<ServerGraph>("ServerGraph")
val Attributes.serverGraph: ServerGraph
    get() = get(ServerGraphKey)

suspend fun main(args: Array<String>) {
    val defaultConfig = ConfigFactory.load()
    val configFile = args
        .firstOrNull { it.startsWith("-config=") }
        ?.substringAfter("=")
        .orEmpty()
        .ifBlank { System.getenv("CONFIG_PATH") }
        ?.takeIf { it.isNotBlank() && configFileSuffixes.contains(it.substringAfterLast('.')) }
        ?.let { FileSystems.getDefault().getPath(it) }

    if (configFile?.exists() == false) {
        println("AnyStream config file does not exist, creating at '${configFile.absolutePathString()}'")
        try {
            val defaultConfig = when (configFile.extension) {
                "conf" -> "app {\n\n}"
                else -> "app:\n"
            }
            configFile.writeText(defaultConfig)
        } catch (e: Throwable) {
            println("Failed to write config file:")
            e.printStackTrace()
        }
    }

    val userConfig = configFile?.let { ConfigFactory.parseFile(it.toFile()) }
    val mergedConfig = if (userConfig == null) {
        defaultConfig
    } else {
        userConfig.withFallback(defaultConfig).resolve()
    }
    val appConfig = HoconApplicationConfig(mergedConfig)

    embeddedServer(
        Netty,
        environment = applicationEnvironment {
            config = appConfig
        },
        {
            // TODO: this prevents user config files from setting engine config, is this a problem?
            val config = CommandLineConfig(args.filter { !it.startsWith("-config") }.toTypedArray())
            takeFrom(config.engineConfig)
            loadCommonConfiguration(appConfig.config("ktor.deployment"))
        },
    ).startSuspend(wait = true)
}

@Suppress("unused", "UNUSED_PARAMETER") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    val fs = FileSystems.getDefault()
    val config = AnyStreamConfig(environment.config, fs)
    val serverGraph = createGraphFactory<ServerGraph.Factory>().create(config, this)
    attributes[ServerGraphKey] = serverGraph

    val applicationScope = this as CoroutineScope
    monitor.subscribe(ApplicationStopped) {
        // get<KJob>().shutdown()
    }

    check(runMigrations(config.databaseUrl, log))
    applicationScope.launch {
        serverGraph.libraryService.initializeLibraries(
            buildMap {
                put(MediaKind.TV, config.libraries.tv.directories)
                put(MediaKind.MOVIE, config.libraries.movies.directories)
                put(MediaKind.MUSIC, config.libraries.music.directories)
            },
        )
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

    install(Authentication) {
        session<UserSession> {
            challenge { call.respond(Unauthorized) }
            validate { it }
        }
        if (config.oidc.enable) {
            oauth(config.oidc.provider.name) {
                client = serverGraph.http
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
                        onStateCreated = { call, state ->
                            val redirectUrl = call.request.queryParameters["redirect_url"]
                            if (redirectUrl != null) {
                                oauthRedirectUrls[state] = redirectUrl.decodeURLQueryComponent()
                            }
                        },
                    )
                }
            }
        }
    }
    val sessionStorage = serverGraph.sessionStorage
    install(Sessions) {
        headerOrQuery(UserSession.KEY, sessionStorage, SqlSessionStorage.Serializer) {
            sessionStorage.newId()
        }
    }
    install(WebsocketAuthorization) {
        try {
            extractUserSession(sessionStorage::readSession)
        } catch (_: NoSuchElementException) {
        }
    }
    install(PermissionAuthorization) {
        global(Permission.Global)
        extract { (it as UserSession).permissions }
    }
    installRouting(serverGraph)
}
