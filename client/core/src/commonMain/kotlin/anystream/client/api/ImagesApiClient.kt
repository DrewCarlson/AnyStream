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

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.io.Buffer

class ImagesApiClient(
    private val core: AnyStreamApiCore,
) {
    fun buildImageUrl(imageType: String, metadataId: String, width: Int = 0): String {
        return "${core.serverUrl}/api/image/$metadataId/${imageType}.jpg?width=$width"
    }

    suspend fun getPreviewBif(mediaLinkId: String): Buffer? {
        val response = core.http.get("/api/image/previews/$mediaLinkId")
        if (!response.status.isSuccess()) {
            return null
        }
        return Buffer().apply { write(response.bodyAsBytes()) }
    }
}