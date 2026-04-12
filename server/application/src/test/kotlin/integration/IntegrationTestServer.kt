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

import anystream.ServerGraph
import anystream.config.AnyStreamConfig
import anystream.config.getPath
import anystream.json
import anystream.metadata.testing.WireFixtures
import anystream.models.Library
import anystream.models.MediaKind
import anystream.models.api.CreateUserBody
import anystream.models.api.MoviesResponse
import anystream.models.api.TvShowsResponse
import anystream.module
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import dev.zacsweers.metro.createDynamicGraphFactory
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Spins up the production [module] inside a Ktor [testApplication] with:
 *  - Jimfs in-memory filesystem (library + data + transcode paths live here),
 *  - file-backed in-memory SQLite (so Flyway and the app pool share the same DB),
 *  - a [MockEngine] [HttpClient] that serves [WireFixtures] for `wire.anystream.dev` and
 *    empty 200s for any other URL (covers ImageStore image downloads).
 *
 * Usage:
 * ```
 * integrationTest {
 *     val response = client.get("/api/users/auth-types")
 *     // ...
 * }
 * ```
 */
fun integrationTest(
    libraryFileTree: String? = null,
    block: suspend IntegrationTestScope.() -> Unit,
) {
    WireFixtures.verifyFixturesAvailable()

    val fs: FileSystem = Jimfs.newFileSystem(Configuration.unix())
    val dataPath = fs.getPath("/anystream").apply { createDirectories() }
    val transcodePath = fs.getPath("/transcode").apply { createDirectories() }

    val librariesConfig = libraryFileTree?.let { seedTree(fs, it) }
        ?: AnyStreamConfig.LibrariesConfig()

    // SQLite ":memory:" gives a different DB per connection, so back the test DB with a
    // throwaway file on the host FS — Flyway and the app pool both connect via JDBC URL.
    val dbFile = Files.createTempFile("anystream-it-", ".db")
    Files.deleteIfExists(dbFile)
    val databaseUrl = "jdbc:sqlite:${dbFile.toAbsolutePath()}"

    getPath = { fs.getPath(it) }
    val testConfig = AnyStreamConfig(
        web = AnyStreamConfig.WebConfig(enable = false, path = null),
        paths = AnyStreamConfig.PathsConfig(
            data = dataPath,
            transcode = transcodePath,
            ffmpeg = dataPath.resolve("ffmpeg"),
        ),
        databaseUrl = databaseUrl,
        libraries = librariesConfig,
    )

    try {
        testApplication {
            lateinit var serverGraph: ServerGraph

            application {
                serverGraph = createDynamicGraphFactory<ServerGraph.Factory>(
                    TestIOBindings(fs),
                ).create(testConfig, this@application)
                module(serverGraph)
            }
            startApplication()
            val scope = IntegrationTestScope(this)
            block(scope)
        }
    } finally {
        runCatching { fs.close() }
        runCatching { Files.deleteIfExists(dbFile) }
        getPath = { FileSystems.getDefault().getPath(it) }
    }
}

// ── Tree parser ─────────────────────────────────────────────────────────────

private val TREE_DRAW_CHARS = Regex("[│├└─]")
private val FILE_EXT_REGEX = Regex("\\.[A-Za-z0-9]{1,5}$")
private val BRACKET_REGEX = Regex("\\[([^]]+)]")
private const val INDENT_WIDTH = 4

/**
 * Parse an ASCII box-drawing tree, create files/directories on [fs], and return a
 * [AnyStreamConfig.LibrariesConfig] with any library roots declared via `[bracket]` syntax.
 *
 * Both fully-expanded and compressed (multi-segment) entries are supported:
 *
 * ```
 * /media
 * ├── [Movies]/Memento (2000)/Memento (2000).mkv
 * └── [TV]/The Pitt (2025)/Season 1
 *     ├── The Pitt - S01E01 - Pilot.mkv
 *     └── The Pitt - S01E02 - Episode 2.mkv
 * ```
 *
 * A segment wrapped in `[brackets]` marks its resolved absolute path as a library root.
 * The media kind is determined by a case-insensitive prefix of the folder name:
 * `movie*` → MOVIE, `tv*` → TV, `music*` → MUSIC. The brackets are stripped from the
 * actual directory name so `[Movies]` creates a directory called `Movies`.
 */
private fun seedTree(
    fs: FileSystem,
    tree: String,
): AnyStreamConfig.LibrariesConfig {
    val parents = ArrayDeque<Pair<Int, String>>() // (depth, absolutePath)
    val roots = mutableMapOf<String, MutableList<String>>() // kind-prefix → paths

    tree
        .lineSequence()
        .filter { it.isNotBlank() }
        .forEach { rawLine ->
            val normalized = rawLine.replace(TREE_DRAW_CHARS, " ")
            val column = normalized.indexOfFirst { !it.isWhitespace() }
            if (column < 0) return@forEach
            val entry = normalized.substring(column).trim()
            val depth = column / INDENT_WIDTH

            while (parents.isNotEmpty() && parents.last().first >= depth) {
                parents.removeLast()
            }

            // Single-pass: walk segments, strip brackets, build path, detect library roots.
            val parentBase = parents.lastOrNull()?.second?.trimEnd('/') ?: ""
            var pathSoFar = parentBase
            for (segment in entry.split("/")) {
                val bracketMatch = BRACKET_REGEX.find(segment)
                val dirName = bracketMatch?.groupValues?.get(1) ?: segment
                pathSoFar = if (pathSoFar.isEmpty() && dirName.startsWith("/")) {
                    dirName
                } else {
                    "$pathSoFar/$dirName"
                }
                if (bracketMatch != null) {
                    val kind = dirName.lowercase()
                    val key = LIBRARY_KIND_PREFIXES.firstOrNull { kind.startsWith(it) }
                    if (key != null) {
                        roots.getOrPut(key) { mutableListOf() }.add(pathSoFar)
                    }
                }
            }

            // Create on the filesystem.
            val target = fs.getPath(pathSoFar)
            val isFile = FILE_EXT_REGEX.containsMatchIn(pathSoFar.substringAfterLast('/'))
            if (isFile) {
                target.parent?.let { Files.createDirectories(it) }
                if (!Files.exists(target)) target.createFile()
            } else {
                Files.createDirectories(target)
            }

            parents.addLast(depth to pathSoFar)
        }

    fun paths(prefix: String) =
        roots[prefix]
            ?.distinct()
            ?.map(fs::getPath)
            .orEmpty()

    return AnyStreamConfig.LibrariesConfig(
        movies = AnyStreamConfig.LibraryConfig(paths("movie")),
        tv = AnyStreamConfig.LibraryConfig(paths("tv")),
        music = AnyStreamConfig.LibraryConfig(paths("music")),
    )
}

private val LIBRARY_KIND_PREFIXES = listOf("movie", "tv", "music")

class IntegrationTestScope(
    val app: ApplicationTestBuilder,
) {
    /** A pre-configured client that speaks JSON. */
    val client by lazy {
        app.createClient {
            install(ContentNegotiation) { json(json) }
        }
    }

    /** Apply a session token (returned by login/signup) as the header the server expects. */
    fun HttpRequestBuilder.withSession(sessionId: String) {
        headers { append(SESSION_HEADER, sessionId) }
    }

    companion object {
        const val SESSION_HEADER: String = "as_user_session"
    }
}

// ── Shared test helpers ─────────────────────────────────────────────────────

suspend fun IntegrationTestScope.signupAdmin(
    username: String = "admin",
    password: String = "supersecret",
): String {
    val response = client.post("/api/users") {
        contentType(ContentType.Application.Json)
        setBody(CreateUserBody(username, password, null))
    }
    check(response.status == HttpStatusCode.OK) {
        "Failed to create admin: ${response.status}"
    }
    return checkNotNull(response.headers[IntegrationTestScope.SESSION_HEADER]) {
        "Signup did not return a session header"
    }
}

suspend fun IntegrationTestScope.listLibraries(session: String): List<Library> {
    val response = client.get("/api/library") { withSession(session) }
    check(response.status == HttpStatusCode.OK) { "GET /api/library failed: ${response.status}" }
    return response.body()
}

/**
 * Poll until the library for [mediaKind] has imported content, then return it.
 * Works for both MOVIE and TV by checking the appropriate response type.
 */
suspend fun IntegrationTestScope.waitForLibrary(
    mediaKind: MediaKind,
    session: String,
    timeout: Duration = 15.seconds,
): Library {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    var lastErr: String? = null
    while (deadline.hasNotPassedNow()) {
        val libraries = listLibraries(session)
        val target = libraries.firstOrNull { it.mediaKind == mediaKind }
        if (target != null) {
            val detail = client.get("/api/library/${target.id.value}") { withSession(session) }
            if (detail.status == HttpStatusCode.OK) {
                val populated = when (mediaKind) {
                    MediaKind.MOVIE -> detail.body<MoviesResponse>().movies.isNotEmpty()
                    MediaKind.TV -> detail.body<TvShowsResponse>().tvShows.isNotEmpty()
                    else -> true
                }
                if (populated) return target
                lastErr = "Library ${mediaKind.name} has no content yet"
            } else {
                lastErr = "Library detail status=${detail.status}"
            }
        } else {
            lastErr = "No $mediaKind library found"
        }
        delay(200.milliseconds)
    }
    error("Timed out waiting for $mediaKind library to populate. Last: $lastErr")
}
