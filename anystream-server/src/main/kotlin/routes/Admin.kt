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

import anystream.util.extractUserSession
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.AppenderBase
import io.ktor.server.websocket.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory

fun Route.addAdminRoutes() {
    route("/admin") {
    }
}

fun Route.addAdminWsRoutes() {
    webSocket("/ws/admin/logs") {
        checkNotNull(extractUserSession())
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val appender = createAppender { message ->
            if (isActive) outgoing.trySend(Frame.Text(message))
        }
        appender.context = context
        appender.start()
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
        root.addAppender(appender)
        while (isActive) {
            delay(100)
        }
        root.detachAppender(appender)
        close()
    }
}

private fun createAppender(callback: (String) -> Unit): Appender<ILoggingEvent> {
    return object : AppenderBase<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) {
            callback(buildString {
                append(event.level.levelStr)
                append(" : ")
                append(event.timeStamp)
                append(" : ")
                event.marker
                    ?.iterator()
                    ?.forEachRemaining { marker ->
                        append("[")
                        append(marker.name)
                        append("]")
                    }
                    ?.also { append(" : ") }
                append(event.message)
            })
        }
    }
}