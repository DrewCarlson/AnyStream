/*
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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

import anystream.data.MetadataDbQueries
import anystream.db.*
import anystream.metadata.providers.WireMetadataProvider
import anystream.metadata.testing.WireFixtures
import anystream.models.MediaKind
import anystream.models.MetadataId
import anystream.models.Movie
import anystream.models.api.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.*
import kotlin.time.Duration

class MetadataServiceTests :
    FunSpec({
        val db by bindTestDatabase()
        val metadataDao by bindForTest({ MetadataDao(db) })
        val queries by bindForTest({
            val tagsDao = TagsDao(db)
            val playbackStatesDao = PlaybackStatesDao(db)
            val mediaLinkDao = MediaLinkDao(db)

            MetadataDbQueries(db, metadataDao, tagsDao, mediaLinkDao, playbackStatesDao)
        })

        val fs by bindFileSystem()
        val manager by bindForTest({
            val imageHttpClient = HttpClient(
                MockEngine { _ -> respond(byteArrayOf(), HttpStatusCode.OK, headersOf()) },
            )
            val imageStore = ImageStore(fs.getPath("/test"), imageHttpClient)
            val provider = WireMetadataProvider(WireFixtures.mockWireApi(), queries, imageStore)
            MetadataService(setOf(provider), metadataDao, imageStore)
        })

        // Constants pulled from the recorded fixtures.
        val mementoWireId = "69d9d842010203040537cfea"
        val pittShowWireId = "69d9548401020304052e2405"

        test("import wire movie") {
            val request = ImportMetadata(
                metadataIds = listOf("77"),
                mediaKind = MediaKind.MOVIE,
                providerId = "wire",
                refresh = false,
            )
            val importResults = manager.importMetadata(request)
            val importResult = assertIs<ImportMetadataResult.Success>(importResults.first())
            val metadataMatch = assertIs<MetadataMatch.MovieMatch>(importResult.match)

            assertEquals("77", metadataMatch.remoteMetadataId)
            assertEquals("wire:movie:77", metadataMatch.remoteId)
            assertEquals("Memento", metadataMatch.movie.title)
            assertEquals(MetadataId(mementoWireId), metadataMatch.movie.id)
            assertTrue(metadataMatch.exists)

            val dbResult = assertNotNull(metadataDao.find(metadataMatch.movie.id))
            assertEquals(metadataMatch.movie.title, dbResult.title)
        }

        test("import wire tv show") {
            val request = ImportMetadata(
                metadataIds = listOf("250307"),
                mediaKind = MediaKind.TV,
                providerId = "wire",
                refresh = false,
            )
            val importResults = manager.importMetadata(request)
            val importResult = assertIs<ImportMetadataResult.Success>(importResults.first())
            val metadataMatch = assertIs<MetadataMatch.TvShowMatch>(importResult.match)

            assertEquals("250307", metadataMatch.remoteMetadataId)
            assertEquals("wire:tv:250307", metadataMatch.remoteId)
            assertEquals("The Pitt", metadataMatch.tvShow.name)
            assertEquals(MetadataId(pittShowWireId), metadataMatch.tvShow.id)
            assertTrue(metadataMatch.exists)

            val dbResult = assertNotNull(metadataDao.find(metadataMatch.tvShow.id))
            assertEquals(metadataMatch.tvShow.name, dbResult.title)
        }

        test("query wire movie") {
            val queryResult = manager.search(MediaKind.MOVIE) {
                providerId = "wire"
                query = "Memento"
            }
            val searchResult = queryResult.first()
            assertIs<QueryMetadataResult.Success>(searchResult)
            val result = searchResult.results.first()
            assertIs<MetadataMatch.MovieMatch>(result)

            assertEquals("Memento", result.movie.title)
        }

        test("query wire movie firstResultOnly") {
            val queryResult = manager.search(MediaKind.MOVIE) {
                providerId = "wire"
                query = "Memento"
                firstResultOnly = true
            }
            val searchResult = queryResult.first()
            assertIs<QueryMetadataResult.Success>(searchResult)
            assertEquals(1, searchResult.results.size)
            val result = searchResult.results.first()
            assertIs<MetadataMatch.MovieMatch>(result)

            assertEquals("Memento", result.movie.title)
        }

        test("query wire tv show") {
            val queryResult = manager.search(MediaKind.TV) {
                providerId = "wire"
                query = "The Pitt"
                firstResultOnly = true
            }
            val searchResult = queryResult.first()
            assertIs<QueryMetadataResult.Success>(searchResult)
            val result = searchResult.results.first()
            assertIs<MetadataMatch.TvShowMatch>(result)

            assertEquals("The Pitt", result.tvShow.name)
        }

        context("MetadataService unit tests") {
            fun createMovieMatch(
                id: String,
                title: String,
                providerId: String,
            ) = MetadataMatch.MovieMatch(
                movie = Movie(
                    id = MetadataId(id),
                    tmdbId = 1,
                    title = title,
                    overview = "",
                    releaseDate = null,
                    runtime = Duration.ZERO,
                    createdAt = kotlin.time.Clock.System
                        .now(),
                    tmdbRating = 0,
                    imdbId = null,
                    contentRating = null,
                ),
                remoteMetadataId = "1",
                remoteId = "$providerId:movie:1",
                exists = false,
                providerId = providerId,
            )

            test("search routes to all providers matching mediaKind when no providerId given") {
                val provider1 = mockk<MetadataProvider> {
                    coEvery { id } returns "provider1"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE)
                    coEvery { search(any()) } returns QueryMetadataResult.Success(
                        "provider1",
                        listOf(createMovieMatch("m1", "Movie A", "provider1")),
                        null,
                    )
                }
                val provider2 = mockk<MetadataProvider> {
                    coEvery { id } returns "provider2"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE)
                    coEvery { search(any()) } returns QueryMetadataResult.Success(
                        "provider2",
                        listOf(createMovieMatch("m2", "Movie B", "provider2")),
                        null,
                    )
                }
                val tvOnlyProvider = mockk<MetadataProvider> {
                    coEvery { id } returns "tvonly"
                    coEvery { mediaKinds } returns listOf(MediaKind.TV)
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider1, provider2, tvOnlyProvider), metadataDao, imageStore)

                val results = service.search(MediaKind.MOVIE) {
                    query = "Movie"
                }

                results.shouldHaveSize(2)
                results.forEach { it.shouldBeInstanceOf<QueryMetadataResult.Success>() }
            }

            test("search routes to specific provider when providerId given") {
                val provider1 = mockk<MetadataProvider> {
                    coEvery { id } returns "provider1"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE)
                    coEvery { search(any()) } returns QueryMetadataResult.Success(
                        "provider1",
                        listOf(createMovieMatch("m1", "Movie A", "provider1")),
                        null,
                    )
                }
                val provider2 = mockk<MetadataProvider> {
                    coEvery { id } returns "provider2"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE)
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider1, provider2), metadataDao, imageStore)

                val results = service.search(MediaKind.MOVIE) {
                    providerId = "provider1"
                    query = "Movie"
                }

                results.shouldHaveSize(1)
                val success = results.first().shouldBeInstanceOf<QueryMetadataResult.Success>()
                success.providerId shouldBe "provider1"
            }

            test("search returns empty when providerId not found") {
                val provider1 = mockk<MetadataProvider> {
                    coEvery { id } returns "provider1"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE)
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider1), metadataDao, imageStore)

                val results = service.search(MediaKind.MOVIE) {
                    providerId = "nonexistent"
                    query = "Movie"
                }

                results.shouldBeEmpty()
            }

            test("search returns empty when no providers match mediaKind") {
                val tvProvider = mockk<MetadataProvider> {
                    coEvery { id } returns "tvonly"
                    coEvery { mediaKinds } returns listOf(MediaKind.TV)
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(tvProvider), metadataDao, imageStore)

                val results = service.search(MediaKind.MOVIE) {
                    query = "Movie"
                }

                results.shouldBeEmpty()
            }

            test("findByRemoteId parses movie remote id correctly") {
                val provider = mockk<MetadataProvider> {
                    coEvery { id } returns "wire"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE, MediaKind.TV)
                    coEvery { search(any()) } returns QueryMetadataResult.Success(
                        "wire",
                        listOf(createMovieMatch("wire:movie:42", "Test Movie", "wire")),
                        null,
                    )
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider), metadataDao, imageStore)

                val result = service.findByRemoteId("wire:movie:42")
                result.shouldBeInstanceOf<QueryMetadataResult.Success>()
            }

            test("findByRemoteId parses tv remote id with season and episode") {
                val provider = mockk<MetadataProvider> {
                    coEvery { id } returns "wire"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE, MediaKind.TV)
                    coEvery {
                        search(
                            match { req ->
                                req.providerId == "wire" &&
                                    req.mediaKind == MediaKind.TV &&
                                    req.metadataId == "456" &&
                                    req.extras is QueryMetadata.Extras.TvShowExtras
                            },
                        )
                    } returns QueryMetadataResult.Success("wire", emptyList(), null)
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider), metadataDao, imageStore)

                val result = service.findByRemoteId("wire:tv:456-2-5")
                result.shouldBeInstanceOf<QueryMetadataResult.Success>()
            }

            test("findByRemoteId returns ErrorProviderNotFound when no providers match") {
                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(emptySet(), metadataDao, imageStore)

                val result = service.findByRemoteId("wire:movie:1")
                result shouldBe QueryMetadataResult.ErrorProviderNotFound
            }

            test("importMetadata returns empty when provider not found") {
                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(emptySet(), metadataDao, imageStore)

                val results = service.importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("1"),
                        providerId = "nonexistent",
                        mediaKind = MediaKind.MOVIE,
                    ),
                )
                results.shouldBeEmpty()
            }

            test("importMetadata delegates to matching provider") {
                val expectedResult = ImportMetadataResult.Success(
                    match = createMovieMatch("m1", "Imported Movie", "wire"),
                )
                val provider = mockk<MetadataProvider> {
                    coEvery { id } returns "wire"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE)
                    coEvery { importMetadata(any()) } returns listOf(expectedResult)
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider), metadataDao, imageStore)

                val results = service.importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("1"),
                        providerId = "wire",
                        mediaKind = MediaKind.MOVIE,
                    ),
                )
                results.shouldHaveSize(1)
                results.first().shouldBeInstanceOf<ImportMetadataResult.Success>()
            }
        }
    })
