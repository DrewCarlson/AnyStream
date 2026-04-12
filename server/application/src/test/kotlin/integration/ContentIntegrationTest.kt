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

import anystream.models.MediaKind
import anystream.models.api.HomeResponse
import anystream.models.api.MovieResponse
import anystream.models.api.MoviesResponse
import anystream.models.api.TvShowResponse
import anystream.models.api.TvShowsResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

private val FULL_LAYOUT =
    """
    /media
    ├── [Movies]/Memento (2000)/Memento (2000).mkv
    └── [TV]/The Pitt (2025)/Season 1
        ├── The Pitt - S01E01 - Pilot.mkv
        └── The Pitt - S01E02 - Episode 2.mkv
    """.trimIndent()

class ContentIntegrationTest :
    FunSpec(
        {

            test("GET /api/media/{id} returns the imported movie") {
                integrationTest(libraryFileTree = FULL_LAYOUT) {
                    val session = signupAdmin()
                    val movieLib = waitForLibrary(MediaKind.MOVIE, session)

                    val moviesResponse = client.get("/api/library/${movieLib.id.value}") {
                        withSession(session)
                    }
                    moviesResponse.status shouldBe HttpStatusCode.OK
                    val movies = moviesResponse.body<MoviesResponse>().movies
                    movies.size shouldBeGreaterThan 0

                    val movie = movies.first { it.title == "Memento" }
                    val detailResponse = client.get("/api/media/${movie.id.value}") {
                        withSession(session)
                    }
                    detailResponse.status shouldBe HttpStatusCode.OK
                    val detail = detailResponse.body<MovieResponse>()
                    detail.movie.title shouldBe "Memento"
                    detail.movie.tmdbId shouldNotBe null
                }
            }

            test("GET /api/media/{id} returns the imported tv show with seasons") {
                integrationTest(libraryFileTree = FULL_LAYOUT) {
                    val session = signupAdmin()
                    val tvLib = waitForLibrary(MediaKind.TV, session)

                    val showsResponse = client.get("/api/library/${tvLib.id.value}") {
                        withSession(session)
                    }
                    val shows = showsResponse.body<TvShowsResponse>().tvShows
                    val show = shows.first { it.name == "The Pitt" }

                    val detailResponse = client.get("/api/media/${show.id.value}") {
                        withSession(session)
                    }
                    detailResponse.status shouldBe HttpStatusCode.OK
                    val detail = detailResponse.body<TvShowResponse>()
                    detail.tvShow.name shouldBe "The Pitt"
                    detail.seasons.size shouldBeGreaterThan 0
                }
            }

            test("GET /api/home returns recently added movies and shows after import") {
                integrationTest(libraryFileTree = FULL_LAYOUT) {
                    val session = signupAdmin()
                    // ensure both libraries are populated before hitting /home
                    waitForLibrary(MediaKind.MOVIE, session)
                    waitForLibrary(MediaKind.TV, session)

                    val response = client.get("/api/home") { withSession(session) }
                    response.status shouldBe HttpStatusCode.OK
                    val home = response.body<HomeResponse>()
                    home.recentlyAdded.movies.keys
                        .map { it.title } shouldContain "Memento"
                    home.recentlyAdded.tvShows.map { it.name } shouldContain "The Pitt"
                }
            }

            test("GET /api/media/{id} requires authentication") {
                integrationTest {
                    client.get("/api/media/anything").status shouldBe HttpStatusCode.Unauthorized
                }
            }
        },
    )
