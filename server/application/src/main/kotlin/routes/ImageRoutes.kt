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
package anystream.routes

import anystream.di.DATA_PATH
import anystream.di.ServerScope
import anystream.metadata.MetadataService
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ClosedByteChannelException
import java.nio.file.Path
import kotlin.io.path.exists

@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<RoutingController>(),
)
@Inject
class ImageRoutes(
    @param:Named(DATA_PATH)
    val dataPath: Path,
    val metadataService: MetadataService,
) : RoutingController {
    override fun init(parent: Route) {
        parent.route("/image") {
            get("/previews/{mediaLinkId}") {
                getPreviewBif()
            }
            get("/{imageKey}/{imageType}.jpg") {
                getImage()
            }
        }
    }

    suspend fun RoutingContext.getPreviewBif() {
        val mediaLinkId = call.parameters["mediaLinkId"] ?: return call.respond(NotFound)
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

    suspend fun RoutingContext.getImage() {
        val imageKey = call.parameters["imageKey"] ?: return call.respond(NotFound)
        val imageType = call.parameters["imageType"]
            ?: return call.respond(UnprocessableEntity, "url param 'imageType' is required")
        val width = call.parameters["width"]?.toIntOrNull() ?: 0

        val imagePath = when (imageType) {
            "people" -> metadataService.getImagePathForPerson(imageKey)
            else -> metadataService.getImagePath(imageKey, imageType)
        }
        if (imagePath?.exists() == true) {
            try {
                call.response.header(
                    name = HttpHeaders.ContentType,
                    value = ContentType.Image.JPEG.toString(),
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
