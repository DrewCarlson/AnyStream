/*
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
package anystream.metadata

import anystream.di.DATA_PATH
import anystream.di.ServerScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.seconds

@SingleIn(ServerScope::class)
@Inject
class ImageStore(
    @param:Named(DATA_PATH)
    private val dataPath: Path,
    private val httpClient: HttpClient,
) {
    suspend fun cacheImage(
        metadataId: String,
        imageType: String,
        url: String,
        rootMetadataId: String = metadataId,
    ): Boolean {
        return downloadInto(getMetadataImagePath(imageType, metadataId, rootMetadataId), url)
    }

    fun getMetadataImagePath(
        imageType: String,
        metadataId: String,
        rootMetadataId: String = metadataId,
    ): Path {
        return dataPath
            .resolve("metadata")
            .resolve(rootMetadataId)
            .resolve(imageType)
            .resolve(metadataId)
            .createParentDirectories()
    }

    fun getPersonImagePath(personId: String): Path {
        return dataPath
            .resolve("metadata")
            .resolve("people")
            .resolve(personId)
            .createParentDirectories()
    }

    suspend fun downloadInto(
        path: Path,
        url: String,
        retry: Boolean = true,
    ): Boolean =
        withContext(IO) {
            val response = try {
                httpClient.get(url)
            } catch (_: Throwable) {
                null
            }
            if (response?.status?.isSuccess() == true) {
                val body = response.bodyAsChannel()
                try {
                    path
                        .outputStream()
                        .use { out -> body.copyTo(out) }
                } finally {
                    body.cancel()
                }
                return@withContext true
            } else if (retry && response?.status != NotFound) {
                var attempt = 0
                do {
                    attempt += 1
                    if (attempt == 4) {
                        return@withContext false
                    }
                    delay(1.seconds * attempt)
                } while (!downloadInto(path, url, retry = false))
            }
            return@withContext true
        }
}
