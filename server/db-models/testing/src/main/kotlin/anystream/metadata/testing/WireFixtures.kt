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
package anystream.metadata.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import wire.client.WireApiClient
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Pre-recorded Wire JSON fixtures served through a real [WireApiClient] over a [MockEngine], so tests
 * exercise the production deserialization path.
 *
 * Routing — `/movie/{any}` → Memento, `/tv/{any}` → The Pitt, `/search?filter=MOVIE` → Memento search,
 * `/search?filter=TV&query=$EMPTY_TV_QUERY` → empty, any other TV search → The Pitt search.
 *
 * Flip [RECORD_MODE] to `true` to re-fetch every fixture from [WIRE_BASE_URL] into the source tree
 * before tests run, then flip it back before committing.
 */
object WireFixtures {
    private const val RECORD_MODE: Boolean = false
    private const val WIRE_BASE_URL: String = "https://wire.anystream.dev"
    private const val MOCK_BASE_URL: String = "http://wire.test/"
    private val SOURCE_FIXTURES_DIR: Path = Path.of("server/db-models/testing/src/main/resources/wire")

    /** Sentinel query that exercises the empty-result branch of the TV search route. */
    const val EMPTY_TV_QUERY: String = "NoMatchExists12345Zxq"

    private data class Fixture(
        val name: String,
        val urlPath: String,
    )

    private val fixtures = listOf(
        Fixture("movie-memento", "/movie/77"),
        Fixture("tv-pitt", "/tv/250307"),
        Fixture("search-movies-Memento", "/search?query=Memento&filter=MOVIE&offset=0&limit=20"),
        Fixture("search-tv-Pitt", "/search?query=The%20Pitt&filter=TV&offset=0&limit=20"),
        Fixture("search-tv-empty", "/search?query=$EMPTY_TV_QUERY&filter=TV&offset=0&limit=20"),
    )

    init {
        if (RECORD_MODE) refreshAll()
    }

    /** Real [WireApiClient] serving recorded JSON via a [MockEngine]. */
    fun mockWireApi(): WireApiClient {
        val client = HttpClient(mockEngine()) {
            defaultRequest { url(MOCK_BASE_URL) }
        }
        return WireApiClient(client)
    }

    /** [WireApiClient] whose engine throws [throwable] on every request. */
    fun wireApiThrowing(throwable: Throwable): WireApiClient {
        val client = HttpClient(MockEngine { _ -> throw throwable }) {
            defaultRequest { url(MOCK_BASE_URL) }
        }
        return WireApiClient(client)
    }

    private fun mockEngine(): MockEngine {
        val mementoJson = loadJson("movie-memento")
        val pittJson = loadJson("tv-pitt")
        val searchMementoJson = loadJson("search-movies-Memento")
        val searchPittJson = loadJson("search-tv-Pitt")
        val searchEmptyJson = loadJson("search-tv-empty")

        val handler: MockRequestHandler = { request ->
            val path = request.url.encodedPath
            val params = request.url.parameters
            val body = when {
                path.startsWith("/movie/") -> mementoJson

                path.startsWith("/tv/") -> pittJson

                path == "/search" -> when (params["filter"]) {
                    "MOVIE" -> searchMementoJson
                    "TV" -> if (params["query"] == EMPTY_TV_QUERY) searchEmptyJson else searchPittJson
                    else -> error("Unknown search filter: '${params["filter"]}'")
                }

                else -> error("No fixture for path: $path")
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return MockEngine(handler)
    }

    private fun loadJson(name: String): String {
        WireFixtures::class.java.getResourceAsStream("/wire/$name.json")?.use { stream ->
            return stream.bufferedReader().readText()
        }
        // Fallback for record runs before resources are reprocessed.
        val sourcePath = SOURCE_FIXTURES_DIR.resolve("$name.json")
        check(sourcePath.exists()) {
            "Missing wire fixture '$name.json'. Flip RECORD_MODE in WireFixtures.kt to capture it from $WIRE_BASE_URL."
        }
        return sourcePath.readText()
    }

    private fun refreshAll() {
        runBlocking {
            val client = HttpClient(CIO)
            try {
                fixtures.forEach { fix ->
                    val response = client.get("$WIRE_BASE_URL${fix.urlPath}")
                    val body = response.bodyAsText()
                    val target = SOURCE_FIXTURES_DIR.resolve("${fix.name}.json")
                    target.createParentDirectories()
                    target.writeText(body)
                    println("[WireFixtures] Recorded ${fix.name} ← ${fix.urlPath} (${body.length} bytes)")
                }
            } finally {
                client.close()
            }
        }
    }

    /** Fail fast at spec init if no fixtures are available on the classpath or in the source tree. */
    fun verifyFixturesAvailable() {
        val classpathPresent = WireFixtures::class.java.getResource("/wire") != null
        val sourcePresent = java.nio.file.Files
            .exists(SOURCE_FIXTURES_DIR)
        check(classpathPresent || sourcePresent) {
            "No wire fixtures found on the classpath or under $SOURCE_FIXTURES_DIR. " +
                "Flip RECORD_MODE in WireFixtures.kt to capture them."
        }
    }
}
