/*
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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

import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.AttributeKey

val OidcRedirectUrl = AttributeKey<URLBuilder>("OidcRedirectUrl")

inline fun <reified T : Any> SessionsConfig.headerOrQuery(
    key: String,
    storage: SessionStorage,
    serializer: SessionSerializer<T>,
    noinline sessionIdProvider: () -> String,
) {
    register(
        SessionProvider(
            key,
            T::class,
            object : SessionTransport {
                override fun send(
                    call: ApplicationCall,
                    value: String,
                ) {
                    call.response.header(key, value)

                    val redirectUrl = call.attributes.getOrNull(OidcRedirectUrl)
                    if (redirectUrl != null) {
                        val redirectUrlString = redirectUrl
                            .apply { parameters["token"] = value }
                            .buildString()
                        call.response.header(HttpHeaders.Location, redirectUrlString)
                    }
                }

                override fun clear(call: ApplicationCall) = Unit

                override fun receive(call: ApplicationCall): String? {
                    return call.request.queryParameters[key]
                        ?: call.request.headers[key]
                }
            },
            SessionTrackerById(T::class, serializer, storage, sessionIdProvider),
        ),
    )
}
