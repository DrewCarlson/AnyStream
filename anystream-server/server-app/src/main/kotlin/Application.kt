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

import anystream.data.UserSession
import anystream.db.SessionsDao
import anystream.db.mappers.registerMappers
import anystream.db.runMigrations
import anystream.models.Permission
import anystream.routes.installRouting
import anystream.util.SqlSessionStorage
import anystream.util.WebsocketAuthorization
import anystream.util.headerOrQuery
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.content.CachingOptions
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.WebSockets
import io.ktor.util.date.GMTDate
import io.ktor.websocket.*
import org.bouncycastle.util.encoders.Hex
import org.drewcarlson.ktor.permissions.PermissionAuthorization
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.JdbiException
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.statement.Slf4JSqlLogger
import org.jdbi.v3.sqlite3.SQLitePlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.attach
import org.slf4j.event.Level
import java.io.File
import java.time.Duration
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.random.Random
import kotlin.system.exitProcess

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused", "UNUSED_PARAMETER") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    val dataPath = environment.config.propertyOrNull("app.dataPath")?.getString().let { path ->
        Path(path.orEmpty().ifBlank { "${System.getProperty("user.home")}${File.separator}anystream" })
            .createDirectories()
            .absolutePathString()
    }
    val databaseUrl = environment.config
        .property("app.databaseUrl")
        .getString()
        .ifBlank {
            Path("${dataPath}${File.separator}config${File.separator}").run {
                createDirectories()
                "sqlite:${resolve("anystream.db").absolutePathString()}"
            }
        }

    check(runMigrations("jdbc:$databaseUrl", log))

    val jdbi = Jdbi.create("jdbc:$databaseUrl").apply {
        setSqlLogger(Slf4JSqlLogger())
        installPlugin(SQLitePlugin())
        installPlugin(SqlObjectPlugin())
        installPlugin(KotlinSqlObjectPlugin())
        installPlugin(KotlinPlugin())
        registerMappers()
    }
    val dbHandle = try {
        jdbi.open()
    } catch (e: JdbiException) {
        log.error("failed to create database connection", e)
        exitProcess(-1)
    }

    val sessionsDao = dbHandle.attach<SessionsDao>()
    val sessionStorage = SqlSessionStorage(sessionsDao)

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
        options { outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> CachingOptions(
                    CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60),
                    expires = null as? GMTDate?
                )
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
            challenge { context.respond(Unauthorized) }
            validate { it }
        }
    }
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
    installRouting(dataPath, dbHandle)
}
