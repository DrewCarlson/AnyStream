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

import anystream.integration.IntegrationTestScope.Companion.SESSION_HEADER
import anystream.models.Library
import anystream.models.MediaKind
import anystream.models.api.CreateUserBody
import anystream.models.api.MoviesResponse
import anystream.models.api.TvShowsResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val MEMENTO_LAYOUT =
    """
    /media
    └── [Movies]/Memento (2000)/Memento (2000).mkv
    """.trimIndent()

private val PITT_LAYOUT =
    """
    /media
    └── [TV]/The Pitt (2025)/Season 1
        ├── The Pitt - S01E01 - Pilot.mkv
        └── The Pitt - S01E02 - Episode 2.mkv
    """.trimIndent()

class LibraryIntegrationTest :
    FunSpec({

        test("startup-configured movie library imports Memento via wire fixtures") {
            integrationTest(libraryFileTree = MEMENTO_LAYOUT) {
                val session = signupAdmin()
                val library = waitForLibraryWithMovies(MediaKind.MOVIE, session)

                val response = client.get("/api/library/${library.id.value}") {
                    withSession(session)
                }
                response.status shouldBe HttpStatusCode.OK
                val movies = response.body<MoviesResponse>()
                movies.movies.size shouldBeGreaterThan 0
                movies.movies.map { it.title } shouldContain "Memento"
            }
        }

        test("startup-configured tv library imports The Pitt via wire fixtures") {
            integrationTest(libraryFileTree = PITT_LAYOUT) {
                val session = signupAdmin()
                val library = waitForLibraryWithShows(session)

                val response = client.get("/api/library/${library.id.value}") {
                    withSession(session)
                }
                response.status shouldBe HttpStatusCode.OK
                val shows = response.body<TvShowsResponse>()
                shows.tvShows.map { it.name } shouldContain "The Pitt"
            }
        }

        test("library list returns one library per media kind by default") {
            integrationTest {
                val session = signupAdmin()
                val response = client.get("/api/library") { withSession(session) }
                response.status shouldBe HttpStatusCode.OK
                val libraries = response.body<List<Library>>()
                libraries.map { it.mediaKind } shouldContain MediaKind.MOVIE
                libraries.map { it.mediaKind } shouldContain MediaKind.TV
            }
        }

        test("library detail is unauthorized without a session") {
            integrationTest {
                client.get("/api/library").status shouldBe HttpStatusCode.Unauthorized
            }
        }
    })

internal suspend fun IntegrationTestScope.signupAdmin(
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
    return checkNotNull(response.headers[SESSION_HEADER]) {
        "Signup did not return a session header"
    }
}

internal suspend fun IntegrationTestScope.listLibraries(session: String): List<Library> {
    val response = client.get("/api/library") { withSession(session) }
    check(response.status == HttpStatusCode.OK) { "GET /api/library failed: ${response.status}" }
    return response.body()
}

internal suspend fun IntegrationTestScope.waitForLibraryWithMovies(
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
                val movies = detail.body<MoviesResponse>()
                if (movies.movies.isNotEmpty()) return target
                lastErr = "No movies yet (total=${movies.total})"
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

internal suspend fun IntegrationTestScope.waitForLibraryWithShows(
    session: String,
    timeout: Duration = 15.seconds,
): Library {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    var lastErr: String? = null
    while (deadline.hasNotPassedNow()) {
        val libraries = listLibraries(session)
        val target = libraries.firstOrNull { it.mediaKind == MediaKind.TV }
        if (target != null) {
            val detail = client.get("/api/library/${target.id.value}") { withSession(session) }
            if (detail.status == HttpStatusCode.OK) {
                val shows = detail.body<TvShowsResponse>()
                if (shows.tvShows.isNotEmpty()) return target
                lastErr = "No tv shows yet"
            } else {
                lastErr = "Library detail status=${detail.status}"
            }
        } else {
            lastErr = "No TV library found"
        }
        delay(200.milliseconds)
    }
    error("Timed out waiting for TV library to populate. Last: $lastErr")
}
