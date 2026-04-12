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

import anystream.config.AnyStreamConfig
import anystream.data.UserSession
import anystream.db.*
import anystream.jobs.registerJobs
import anystream.models.MediaKind
import anystream.models.Permission
import anystream.modules.installStatusPages
import anystream.routes.installWebClientRoutes
import anystream.util.SqlSessionStorage
import anystream.util.WebsocketAuthorization
import anystream.util.headerOrQuery
import dev.zacsweers.metro.createGraphFactory
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
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
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.mamoe.yamlkt.Yaml
import org.drewcarlson.ktor.permissions.PermissionAuthorization
import org.slf4j.event.Level
import java.nio.file.FileSystems
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

private val configFileSuffixes = listOf("yml", "yaml")

private val yaml = Yaml {
    encodeDefaultValues = true
}

// TODO: add state expiration for incomplete auth flows
val oauthRedirectUrls = ConcurrentMap<String, String>()

suspend fun main() {
    val configFile = System
        .getenv("CONFIG_PATH")
        ?.takeIf { it.isNotBlank() && configFileSuffixes.contains(it.substringAfterLast('.')) }
        ?.let { FileSystems.getDefault().getPath(it) }

    if (configFile != null && !configFile.exists()) {
        println("AnyStream config file does not exist, creating at '${configFile.absolutePathString()}'")
        try {
            configFile.writeText(yaml.encodeToString(AnyStreamConfig.serializer(), AnyStreamConfig()))
        } catch (e: Throwable) {
            println("Failed to write config file:")
            e.printStackTrace()
        }
    }

    val config = configFile
        ?.takeIf { it.exists() }
        ?.let { yaml.decodeFromString(AnyStreamConfig.serializer(), it.readText()) }
        ?: AnyStreamConfig()

    embeddedServer(
        Netty,
        port = config.port,
        host = config.host,
    ) {
        val serverGraph = createGraphFactory<ServerGraph.Factory>()
            .create(config, this)
        installStatusPages()
        module(serverGraph)
    }.startSuspend(wait = true)
}

fun Application.module(serverGraph: ServerGraph) {
    val config = serverGraph.config
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
    if (config.baseUrl == null) {
        install(ForwardedHeaders)
        install(XForwardedHeaders)
    }
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
        if (config.oidc.enable && config.oidc.provider != null) {
            oauth(config.oidc.provider.name) {
                client = serverGraph.http
                urlProvider = { serverGraph.oidcProviderService.urlProvider(this) }
                providerLookup = { serverGraph.oidcProviderService.providerLookup() }
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

    installWebClientRoutes(serverGraph.config)

    serverGraph.routingControllers.init(this)
}
