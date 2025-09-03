/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.client

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLProtocol.Companion.HTTP
import io.ktor.http.URLProtocol.Companion.HTTPS
import io.ktor.http.URLProtocol.Companion.WS
import io.ktor.http.URLProtocol.Companion.WSS
import io.ktor.util.AttributeKey

/**
 * Contains the server url at the time the request was created.
 */
internal val ServerUrlAttribute = AttributeKey<String>("server_url")

/**
 * Upgrade or downgrade the requested protocol to match the
 * [ServerUrlAttribute]'s protocol security.
 *
 * This is required because the [io.ktor.client.plugins.DefaultRequest] plugin
 * cannot change a WebSocket protocol so all request call-sites must specify
 * the required protocol.  This plugin allows us to assume WSS in all call-sites
 * and downgrade to insecure automatically as required.
 *
 * HTTP protocol handling is applied here for completeness, but it would work
 * via the [io.ktor.client.plugins.DefaultRequest] plugin.
 */
internal val AdaptiveProtocolPlugin by lazy {
    createClientPlugin("AdaptiveProtocolPlugin") {
        onRequest { request, _ ->
            val serverUrl = request.attributes[ServerUrlAttribute]
            val secure = serverUrl.startsWith("https")
            request.url.protocol = when (request.url.protocol) {
                WSS, WS -> if (secure) WSS else WS
                HTTPS, HTTP -> if (secure) HTTPS else HTTP
                else -> request.url.protocol
            }
        }
    }
}