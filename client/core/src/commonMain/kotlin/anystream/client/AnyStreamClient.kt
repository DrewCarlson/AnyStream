/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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

import anystream.client.api.AdminApiClient
import anystream.client.api.AnyStreamApiCore
import anystream.client.api.ImagesApiClient
import anystream.client.api.LibraryApiClient
import anystream.client.api.StreamApiClient
import anystream.client.api.TorrentsApiClient
import anystream.client.api.UserApiClient
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json


val json = Json {
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    allowStructuredMapKeys = true
    useAlternativeNames = false
}

class AnyStreamClient(
    /** The AnyStream server URL, ex. `http://localhost:3000`. */
    serverUrl: String?,
    httpClient: HttpClient,
    private val sessionManager: SessionManager,
) {
    val core = AnyStreamApiCore(
        serverUrl = serverUrl,
        httpClient = httpClient,
        sessionManager = sessionManager,
    )

    val user = UserApiClient(core = core)
    val stream = StreamApiClient(core = core)
    val admin = AdminApiClient(core = core)
    val torrents = TorrentsApiClient(core = core)
    val images = ImagesApiClient(core = core)
    val library = LibraryApiClient(core = core)
}
