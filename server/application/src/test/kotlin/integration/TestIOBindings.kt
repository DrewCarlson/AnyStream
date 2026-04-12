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
package anystream.integration

import anystream.di.ServerScope
import anystream.metadata.testing.WireFixtures
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.nio.file.FileSystem

@BindingContainer
class TestIOBindings(
    val fileSystem: FileSystem,
) {
    @SingleIn(ServerScope::class)
    @Provides
    fun provideFileSystem(): FileSystem = fileSystem

    @SingleIn(ServerScope::class)
    @Provides
    fun provideHttpClient(): HttpClient {
        return mockHttpClient()
    }
}

private fun mockHttpClient(): HttpClient {
    // Replicate WireFixtures' routing inline so we can also catch image / unknown URLs.
    val mementoJson = loadFixture("movie-memento")
    val pittJson = loadFixture("tv-pitt")
    val searchMementoJson = loadFixture("search-movies-Memento")
    val searchPittJson = loadFixture("search-tv-Pitt")
    val searchEmptyJson = loadFixture("search-tv-empty")

    val engine = MockEngine { request ->
        val host = request.url.host
        val path = request.url.encodedPath
        if (host == "wire.anystream.dev") {
            val params = request.url.parameters
            val body = when {
                path.startsWith("/movie/") -> mementoJson

                path.startsWith("/tv/") -> pittJson

                path == "/search" -> when (params["filter"]) {
                    "MOVIE" -> {
                        searchMementoJson
                    }

                    "TV" -> {
                        if (params["query"] == WireFixtures.EMPTY_TV_QUERY) {
                            searchEmptyJson
                        } else {
                            searchPittJson
                        }
                    }

                    else -> {
                        error("Unknown search filter: '${params["filter"]}'")
                    }
                }

                else -> error("No fixture for wire path: $path")
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        } else {
            // Image downloads or any other 3rd-party call: return empty 200.
            respond(
                content = byteArrayOf(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
            )
        }
    }
    return HttpClient(engine)
}

private fun loadFixture(name: String): String {
    val stream = WireFixtures::class.java.getResourceAsStream("/wire/$name.json")
        ?: error("Missing wire fixture '$name.json' on classpath")
    return stream.bufferedReader().use { it.readText() }
}
