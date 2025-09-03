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

import anystream.AnyStreamConfig
import anystream.metadata.MetadataService
import anystream.util.koinGet
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ClosedByteChannelException
import kotlin.io.path.exists

fun Route.addImageRoutes(
    config: AnyStreamConfig = koinGet(),
    metadataService: MetadataService = koinGet(),
) {
    val dataPath = config.dataPath
    route("/image") {
        get("/previews/{mediaLinkId}") {
            val mediaLinkId = call.parameters["mediaLinkId"] ?: return@get call.respond(NotFound)
            val imagePath = dataPath
                .resolve("previews")
                .resolve(mediaLinkId)
                .resolve("index-sd.bif")
            if (imagePath.exists()) {
                call.respondFile(imagePath.toFile())
            } else {
                call.respond(NotFound)
            }
        }
        get("/{imageKey}/{imageType}.jpg") {
            val imageKey = call.parameters["imageKey"] ?: return@get call.respond(NotFound)
            val imageType = call.parameters["imageType"]
                ?: return@get call.respond(UnprocessableEntity, "url param 'imageType' is required")
            val width = call.parameters["width"]?.toIntOrNull() ?: 0

            val imagePath = metadataService.getImagePath(imageKey, imageType)
            if (imagePath?.exists() == true) {
                try {
                    call.response.header(
                        name = HttpHeaders.ContentType,
                        value = ContentType.Image.JPEG.toString()
                    )
                    call.respondPath(imagePath)
                } catch (_: ClosedByteChannelException) {
                    // request was cancelled while reading file
                }
            } else {
                call.respond(NotFound)
            }
        }
    }
}
