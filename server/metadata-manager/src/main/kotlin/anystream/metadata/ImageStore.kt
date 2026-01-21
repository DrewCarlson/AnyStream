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
package anystream.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.seconds


/**
 * Result of an image download operation.
 */
sealed class ImageDownloadResult {
    /** Image was downloaded successfully. */
    data object Success : ImageDownloadResult()
    /** Image was not found (404). */
    data object NotFound : ImageDownloadResult()
    /** Network or other error occurred. */
    data class Error(val message: String) : ImageDownloadResult()

    /** Returns true if the download was successful. */
    val isSuccess: Boolean get() = this is Success
}

class ImageStore(
    private val dataPath: Path,
    private val httpClient: HttpClient,
) {

    suspend fun cacheImage(
        metadataId: String,
        imageType: String,
        url: String,
        rootMetadataId: String = metadataId,
    ): ImageDownloadResult {
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

    suspend fun downloadInto(path: Path, url: String, retry: Boolean = true): ImageDownloadResult {
        val response = try {
            withContext(IO) { httpClient.get(url) }
        } catch (e: Throwable) {
            return ImageDownloadResult.Error("Network error: ${e.message}")
        }

        return when {
            response.status.isSuccess() -> {
                val body = response.bodyAsChannel()
                withContext(IO) {
                    path.outputStream()
                        .use { out -> body.copyTo(out) }
                }
                ImageDownloadResult.Success
            }

            response.status == NotFound -> {
                ImageDownloadResult.NotFound
            }

            retry -> {
                // Retry on non-404 errors
                var attempt = 0
                var lastResult: ImageDownloadResult
                do {
                    attempt += 1
                    if (attempt > 3) {
                        return ImageDownloadResult.Error("Max retries exceeded for status ${response.status}")
                    }
                    delay(1.seconds * attempt)
                    lastResult = downloadInto(path, url, retry = false)
                } while (!lastResult.isSuccess && lastResult !is ImageDownloadResult.NotFound)
                lastResult
            }

            else -> {
                ImageDownloadResult.Error("HTTP error: ${response.status}")
            }
        }
    }
}