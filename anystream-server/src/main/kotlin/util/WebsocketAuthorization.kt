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
package anystream.util

import anystream.data.UserSession
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*


class WebsocketAuthorization {

    private lateinit var extractUserSession: suspend (token: String) -> UserSession?

    suspend fun userSessionFor(token: String): UserSession? {
        return extractUserSession(token)
    }

    fun extractUserSession(body: suspend (token: String) -> UserSession?) {
        this.extractUserSession = body
    }

    companion object Feature :
        ApplicationFeature<ApplicationCallPipeline, WebsocketAuthorization, WebsocketAuthorization> {
        override val key = AttributeKey<WebsocketAuthorization>("WebsocketAuthorization")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: WebsocketAuthorization.() -> Unit
        ): WebsocketAuthorization {
            return WebsocketAuthorization().also(configure)
        }
    }
}

suspend fun DefaultWebSocketServerSession.extractUserSession(): UserSession? {
    val token = (incoming.receive() as? Frame.Text)?.readText()
    if (token.isNullOrBlank()) {
        return null
    }
    return application.feature(WebsocketAuthorization).userSessionFor(token)
}