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
import anystream.module
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import dev.zacsweers.metro.createDynamicGraphFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

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

    val libraries = libraryFileTree?.let { seedTree(fs, it) } ?: ParsedLibraries()

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
        libraries = AnyStreamConfig.LibrariesConfig(
            movies = AnyStreamConfig.LibraryConfig(directories = libraries.movies),
            tv = AnyStreamConfig.LibraryConfig(directories = libraries.tv),
            music = AnyStreamConfig.LibraryConfig(directories = libraries.music),
        ),
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

/**
 * Library roots discovered by the tree parser from `[bracket]` annotations.
 */
data class ParsedLibraries(
    val movies: List<Path> = emptyList(),
    val tv: List<Path> = emptyList(),
    val music: List<Path> = emptyList(),
)

// ── Tree parser ─────────────────────────────────────────────────────────────

private val TREE_DRAW_CHARS = Regex("[│├└─]")
private val FILE_EXT_REGEX = Regex("\\.[A-Za-z0-9]{1,5}$")
private val BRACKET_REGEX = Regex("\\[([^]]+)]")
private const val INDENT_WIDTH = 4

/**
 * Parse an ASCII box-drawing tree, create files/directories on [fs], and return any
 * library roots declared with `[bracket]` syntax.
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
): ParsedLibraries {
    val parents = ArrayDeque<Pair<Int, String>>() // (depth, absolutePath)
    val movieRoots = mutableListOf<String>()
    val tvRoots = mutableListOf<String>()
    val musicRoots = mutableListOf<String>()

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

            // Resolve absolute path, processing [bracket] annotations on each segment.
            val absolutePath = buildAbsolutePath(entry, parents.lastOrNull()?.second)
            createTreeNodes(fs, absolutePath)

            // Check each segment for [brackets] to register library roots.
            val segments = entry.split("/")
            var pathSoFar = parents.lastOrNull()?.second?.trimEnd('/') ?: ""
            for (segment in segments) {
                val bracketMatch = BRACKET_REGEX.find(segment)
                val dirName = bracketMatch?.groupValues?.get(1) ?: segment
                pathSoFar = if (segment == entry && entry.startsWith("/")) {
                    entry.replace(BRACKET_REGEX, bracketMatch?.groupValues?.get(1) ?: "")
                } else {
                    "$pathSoFar/$dirName"
                }
                if (bracketMatch != null) {
                    val kind = dirName.lowercase()
                    when {
                        kind.startsWith("movie") -> movieRoots.add(pathSoFar)
                        kind.startsWith("tv") -> tvRoots.add(pathSoFar)
                        kind.startsWith("music") -> musicRoots.add(pathSoFar)
                    }
                }
            }

            parents.addLast(depth to absolutePath)
        }

    return ParsedLibraries(
        movies = movieRoots.distinct().map { fs.getPath(it) },
        tv = tvRoots.distinct().map { fs.getPath(it) },
        music = musicRoots.distinct().map { fs.getPath(it) },
    )
}

/** Build the absolute path for an entry, stripping any `[brackets]` from segment names. */
private fun buildAbsolutePath(
    entry: String,
    parentPath: String?,
): String {
    val cleaned = entry.replace(BRACKET_REGEX) { it.groupValues[1] }
    return if (cleaned.startsWith("/")) {
        cleaned
    } else {
        val parent = parentPath
            ?: error("Tree entry '$entry' has no parent — first line must be an absolute path")
        "${parent.trimEnd('/')}/$cleaned"
    }
}

/** Create file or directory nodes for the given absolute path (leaf detection by extension). */
private fun createTreeNodes(
    fs: FileSystem,
    absolutePath: String,
) {
    val target = fs.getPath(absolutePath)
    val leafName = absolutePath.substringAfterLast('/')
    val isFile = FILE_EXT_REGEX.containsMatchIn(leafName)
    if (isFile) {
        target.parent?.let { Files.createDirectories(it) }
        if (!Files.exists(target)) target.createFile()
    } else {
        Files.createDirectories(target)
    }
}

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
