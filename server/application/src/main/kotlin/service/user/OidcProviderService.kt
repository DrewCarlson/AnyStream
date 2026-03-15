/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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
package anystream.service.user

import anystream.config.AnyStreamConfig
import anystream.di.ServerScope
import anystream.oauthRedirectUrls
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.plugins.origin
import io.ktor.server.request.ApplicationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.set

@SingleIn(ServerScope::class)
@Inject
class OidcProviderService(
    private val config: AnyStreamConfig,
    private val http: HttpClient,
    scope: CoroutineScope,
) {
    private val discoveryData = scope.async(start = CoroutineStart.LAZY) {
        checkNotNull(config.oidc.provider)
        http
            .get {
                url {
                    val endpoint = config.oidc.provider.endpoint
                    takeFrom(endpoint.trimEnd('/'))
                    takeFrom("/.well-known/openid-configuration")
                }
            }.apply {
                check(status.isSuccess()) {
                    "OIDC discovery failed for ${config.oidc.provider.endpoint}:\n${bodyAsText()}"
                }
            }.body<JsonObject>()
    }

    init {
        if (config.oidc.enable && config.oidc.provider != null) {
            scope.launch { discoveryData.await() }
        }
    }

    fun urlProvider(call: ApplicationCall): String {
        return buildString {
            if (config.baseUrl == null) {
                append(call.request.baseUrl())
            } else {
                append(call.request.origin.scheme)
                append("://")
                append(config.baseUrl.trimEnd('/'))
            }
            append("/api/users/oidc/callback")
        }
    }

    suspend fun providerLookup(): OAuthServerSettings {
        checkNotNull(config.oidc.provider)
        val discovery = discoveryData.await()
        val authorizeUrl = requireNotNull(discovery["authorization_endpoint"]) {
            "OIDC discovery result did not contain `authorization_endpoint`"
        }.jsonPrimitive.content
        val accessTokenUrl = requireNotNull(discovery["token_endpoint"]) {
            "OIDC discovery result did not contain `token_endpoint`"
        }.jsonPrimitive.content
        return OAuthServerSettings.OAuth2ServerSettings(
            name = config.oidc.provider.name,
            authorizeUrl = authorizeUrl,
            accessTokenUrl = accessTokenUrl,
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

    suspend fun getUserInfo(accessToken: String): JsonObject {
        checkNotNull(config.oidc.provider)
        val discovery = discoveryData.await()
        val userInfoUrl = checkNotNull(discovery["userinfo_endpoint"]) {
            "OIDC discovery result did not contain `userinfo_endpoint`"
        }.jsonPrimitive.content
        return http
            .get(userInfoUrl) {
                header("Authorization", "Bearer $accessToken")
            }.body<JsonObject>()
    }

    private fun ApplicationRequest.baseUrl(): String {
        val point = origin
        val scheme = point.scheme
        val host = point.serverHost
        val port = point.serverPort
        return if ((scheme == "http" && port == 80) || (scheme == "https" && port == 443)) {
            "$scheme://$host"
        } else {
            "$scheme://$host:$port"
        }
    }
}
