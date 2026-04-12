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

import anystream.models.Library
import anystream.models.MediaKind
import anystream.models.api.MoviesResponse
import anystream.models.api.TvShowsResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

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
                val library = waitForLibrary(MediaKind.MOVIE, session)

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
                val library = waitForLibrary(MediaKind.TV, session)

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
