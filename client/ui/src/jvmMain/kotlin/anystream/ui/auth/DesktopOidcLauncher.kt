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
package anystream.ui.auth

import anystream.client.AnyStreamClient
import anystream.presentation.auth.BaseOidcLauncher
import anystream.presentation.auth.OidcLaunchResult
import anystream.presentation.auth.OidcLauncher
import dev.zacsweers.metro.*
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.awt.Desktop
import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    binding = binding<OidcLauncher>(),
)
@Inject
class DesktopOidcLauncher(
    private val client: AnyStreamClient,
) : BaseOidcLauncher() {
    private val serverReference = AtomicReference<EmbeddedServer<*, *>?>(null)

    override fun launchOidcLogin() {
        if (!Desktop.isDesktopSupported() || !Desktop
                .getDesktop()
                .isSupported(Desktop.Action.BROWSE)
        ) {
            // TODO: Provide user feedback if not supported
            return
        }

        val port = findAvailablePort()
        val redirectUrl = "http://127.0.0.1:$port/callback"
        val oidcUrl = client.user.getOidcLoginUrl(redirectUrl)

        startServer(port)

        try {
            Desktop.getDesktop().browse(URI(oidcUrl))
        } catch (_: IOException) {
            stopServer()
        }
    }

    override fun observeOidcResult(): Flow<OidcLaunchResult> {
        return super
            .observeOidcResult()
            .onEach { stopServer() }
    }

    private fun startServer(port: Int) {
        val server = embeddedServer(
            factory = CIO,
            host = "127.0.0.1",
            port = port,
        ) {
            routing {
                get("/callback") {
                    val uri = call.request.uri
                    call.respondText(CALLBACK_HTML, ContentType.Text.Html)
                    onAuthResult(uri)
                }
            }
        }

        serverReference.set(server)
        server.start(wait = false)
    }

    private fun stopServer() {
        serverReference
            .getAndSet(null)
            ?.stop(1000, 2000)
    }

    private fun findAvailablePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }
}

private const val CALLBACK_HTML = """
<!DOCTYPE html>
<html>
    <head>
        <title>Authentication Successful</title>
        <style>
            body { font-family: sans-serif; text-align: center; padding-top: 50px; line-height: 1.6; }
        </style>
    </head>
    <body>
        <h1>Successfully Authenticated!</h1>
        <p>You close this page and return to AnyStream.</p>
    </body>
</html>
"""
