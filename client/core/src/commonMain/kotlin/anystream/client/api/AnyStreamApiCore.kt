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
package anystream.client.api

import anystream.client.AdaptiveProtocolPlugin
import anystream.client.AnyStreamClientException
import anystream.client.ServerUrlAttribute
import anystream.client.SessionManager
import anystream.client.json
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi


private val KEY_INTERNAL_ERROR = AttributeKey<Throwable>("INTERNAL_ERROR")

private const val PAGE = "page"
private const val QUERY = "query"

@OptIn(ExperimentalAtomicApi::class)
class AnyStreamApiCore(
    serverUrl: String?,
    httpClient: HttpClient,
    internal val sessionManager: SessionManager,
) {
    companion object {
        const val SESSION_KEY = "as_user_session"
    }

    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val serverUrlInternal = AtomicReference(serverUrl ?: sessionManager.fetchServerUrl() ?: "")

    var serverUrl: String
        get() = serverUrlInternal.load()
        private set(value) {
            val trimmedUrl = value.trimEnd('/')
            serverUrlInternal.store(trimmedUrl)
        }

    val http = httpClient.config {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }
        WebSockets {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
        install(AdaptiveProtocolPlugin)
        defaultRequest {
            attributes.put(ServerUrlAttribute, this@AnyStreamApiCore.serverUrl)
            url {
                takeFrom(this@AnyStreamApiCore.serverUrl)
            }
            headers {
                sessionManager.fetchToken()?.let { token ->
                    header(SESSION_KEY, token)
                }
            }
        }
        install("ErrorTransformer") {
            requestPipeline.intercept(HttpRequestPipeline.State) {
                try {
                    proceed()
                } catch (e: Throwable) {
                    val responseData = HttpResponseData(
                        statusCode = HttpStatusCode(-1, ""),
                        requestTime = GMTDate(),
                        body = ByteReadChannel(byteArrayOf()),
                        callContext = context.executionContext,
                        headers = Headers.Empty,
                        version = HttpProtocolVersion.HTTP_1_0,
                    )
                    context.attributes.put(KEY_INTERNAL_ERROR, e)
                    @OptIn(InternalAPI::class)
                    subject = HttpClientCall(this@install, context.build(), responseData)
                    proceed()
                }
            }
        }
        install("TokenHandler") {
            responsePipeline.intercept(HttpResponsePipeline.Receive) {
                context.response.headers[SESSION_KEY]?.let { token ->
                    if (token != sessionManager.fetchToken()) {
                        sessionManager.writeToken(token)
                    }
                }
                val sentToken = context.request.headers[SESSION_KEY]
                if (context.response.status == Unauthorized && sentToken != null) {
                    sessionManager.clear()
                }
            }
        }
    }

    suspend fun verifyAndSetServerUrl(serverUrl: String): Boolean {
        if (this.serverUrl.equals(serverUrl, ignoreCase = true)) return true
        return try {
            check(http.get(serverUrl).status == OK)
            this.serverUrl = serverUrl
            sessionManager.writeServerUrl(this.serverUrl)
            true
        } catch (e: Throwable) {
            false
        }
    }
}

internal suspend fun HttpResponse.orThrow() {
    if (!status.isSuccess()) {
        throw call.attributes.takeOrNull(KEY_INTERNAL_ERROR)?.run(::AnyStreamClientException)
            ?: AnyStreamClientException(this, bodyAsText())
    }
}

internal suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    return if (status.isSuccess()) {
        when (T::class) {
            String::class -> bodyAsText() as T
            else -> body()
        }
    } else {
        throw call.attributes.takeOrNull(KEY_INTERNAL_ERROR)?.run(::AnyStreamClientException)
            ?: AnyStreamClientException(this, bodyAsText())
    }
}

fun HttpRequestBuilder.pageParam(page: Int) {
    parameter(PAGE, page)
}
