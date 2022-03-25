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

import io.ktor.client.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlin.io.path.Path
import kotlin.io.path.exists

fun Route.addImageRoutes(storagePath: String) {
    val imageClient = HttpClient {
        install(HttpCache) {
            // TODO: Add disk catching
            publicStorage = HttpCacheStorage.Unlimited()
        }
    }
    route("/image") {
        get("/previews/{mediaRefId}/{imageName}") {
            val mediaRefId = call.parameters["mediaRefId"] ?: return@get call.respond(NotFound)
            val imageName = call.parameters["imageName"] ?: return@get call.respond(NotFound)
            val imagePath = Path(storagePath, "previews", mediaRefId, imageName)
            if (imagePath.exists()) {
                call.respondFile(imagePath.toFile())
            } else {
                call.respond(NotFound)
            }
        }
        get("/{imageKey}") {
            val imageKey = call.parameters["imageKey"] ?: return@get call.respond(NotFound)

            if (imageKey.startsWith("tmdb:")) {
                val (_, fileName) = imageKey.split(':')
                val response = imageClient.get("https://image.tmdb.org/t/p/original/$fileName")
                if (response.status == HttpStatusCode.OK) {
                    call.respondBytesWriter(response.contentType()) {
                        response.bodyAsChannel().copyTo(this)
                    }
                } else {
                    call.respond(NotFound)
                }
            } else {
                call.respond(NotFound)
            }
        }
    }
}
