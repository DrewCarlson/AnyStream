/**
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
package anystream.routes

import anystream.util.SinglePageApp
import io.ktor.server.application.*
import java.io.File

fun Application.installWebClientRoutes() {
    val disableWebClient = environment.config.property("app.disableWebClient").getString().toBoolean()
    val webClientPath = environment.config.propertyOrNull("app.webClientPath")?.getString()
    if (disableWebClient) {
        log.debug("Web client disabled, this instance will serve the API only.")
    } else if (
        webClientPath.isNullOrBlank() ||
        !File(webClientPath).exists() &&
        checkNotNull(javaClass.classLoader).getResource("anystream-client-web") != null
    ) {
        log.debug("This instance will serve the web client from jar resources.")
        install(SinglePageApp) {
            ignoreBasePath = "/api"
            staticFilePath = "anystream-client-web"
            useResources = true
        }
    } else if (File(webClientPath).exists()) {
        log.debug("This instance will serve the web client from '$webClientPath'.")
        install(SinglePageApp) {
            ignoreBasePath = "/api"
            staticFilePath = webClientPath
        }
    } else {
        log.error("Failed to find web client, this instance will serve the API only.")
    }
}
