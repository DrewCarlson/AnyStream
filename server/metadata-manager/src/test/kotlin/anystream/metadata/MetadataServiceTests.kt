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
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.MediaKind
import anystream.models.MetadataId
import anystream.models.Movie
import anystream.models.api.*
import app.moviebase.tmdb.Tmdb3
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.*
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
        val tmdb by bindForTest({
            Tmdb3 {
                tmdbApiKey = "c1e9e8ade306dd9cbc5e17b05ed4badd"
                this.httpClient {
                    install(Logging) {
                        level = LogLevel.ALL
                        logger = Logger.SIMPLE
                    }
                }
            }
        })

        val fs by bindFileSystem()
        val manager by bindForTest({
            val imageStore = ImageStore(fs.getPath("/test"), HttpClient())
            val provider = TmdbMetadataProvider(tmdb, queries, imageStore)
            MetadataService(setOf(provider), metadataDao, imageStore)
        })

        test("import tmdb movie") {
            val request = ImportMetadata(
                metadataIds = listOf("77"),
                mediaKind = MediaKind.MOVIE,
                year = 2000,
                providerId = "tmdb",
                refresh = false,
            )
            val importResults = manager.importMetadata(request)
            val importResult = assertIs<ImportMetadataResult.Success>(importResults.first())
            val metadataMatch = assertIs<MetadataMatch.MovieMatch>(importResult.match)

            assertEquals("77", metadataMatch.remoteMetadataId)
            assertEquals("tmdb:movie:77", metadataMatch.remoteId)
            assertEquals("Memento", metadataMatch.movie.title)
            assertTrue(metadataMatch.exists)

            val dbResult = assertNotNull(metadataDao.find(metadataMatch.movie.id))
            assertEquals(metadataMatch.movie.title, dbResult.title)
        }

        test("import tmdb tv show") {
            val request = ImportMetadata(
                metadataIds = listOf("63333"),
                mediaKind = MediaKind.TV,
                year = 2015,
                providerId = "tmdb",
                refresh = false,
            )
            val importResults = manager.importMetadata(request)
            val importResult = assertIs<ImportMetadataResult.Success>(importResults.first())
            val metadataMatch = assertIs<MetadataMatch.TvShowMatch>(importResult.match)

            assertEquals("63333", metadataMatch.remoteMetadataId)
            assertEquals("tmdb:tv:63333", metadataMatch.remoteId)
            assertEquals("The Last Kingdom", metadataMatch.tvShow.name)
            assertTrue(metadataMatch.exists)

            val dbResult = assertNotNull(metadataDao.find(metadataMatch.tvShow.id))
            assertEquals(metadataMatch.tvShow.name, dbResult.title)
        }

        test("query tmdb movie") {
            val queryResult = manager.search(MediaKind.MOVIE) {
                providerId = "tmdb"
                query = "the avengers"
                year = 2012
            }
            val searchResult = queryResult.first()
            assertIs<QueryMetadataResult.Success>(searchResult)
            val result = searchResult.results.first()
            assertIs<MetadataMatch.MovieMatch>(result)

            assertEquals("The Avengers", result.movie.title)
        }

        test("query tmdb movie sorted") {
            listOf(
                Pair("The BFG", 2016),
                Pair("Soylent Green", 1973),
            ).forEach { (title, year) ->
                val queryResult = manager.search(MediaKind.MOVIE) {
                    providerId = "tmdb"
                    query = title
                    this.year = year
                    firstResultOnly = true
                }
                val searchResult = queryResult.first()
                assertIs<QueryMetadataResult.Success>(searchResult)
                val result = searchResult.results.first()
                assertIs<MetadataMatch.MovieMatch>(result)

                assertEquals(title, result.movie.title)
                assertEquals(year, result.movie.releaseYear?.toIntOrNull())
            }
        }

        test("query tmdb tv show sorted") {
            val queryResult = manager.search(MediaKind.TV) {
                providerId = "tmdb"
                query = "the boys"
                year = null
                firstResultOnly = true
            }
            val searchResult = queryResult.first()
            assertIs<QueryMetadataResult.Success>(searchResult)
            val result = searchResult.results.first()
            assertIs<MetadataMatch.TvShowMatch>(result)

            assertEquals("The Boys", result.tvShow.name)
            assertEquals(2019, result.tvShow.releaseYear?.toIntOrNull())
        }

        test("query tmdb tv show") {
            val queryResult = manager.search(MediaKind.TV) {
                providerId = "tmdb"
                query = "last kingdom"
                year = 2015
            }
            val searchResult = queryResult.first()
            assertIs<QueryMetadataResult.Success>(searchResult)
            val result = searchResult.results.first()
            assertIs<MetadataMatch.TvShowMatch>(result)

            assertEquals("The Last Kingdom", result.tvShow.name)
        }

        test("find tmdb tv show by remote id") {
            val remoteId = "tmdb:tv:456"
            val queryResult = manager.findByRemoteId(remoteId)

            assertIs<QueryMetadataResult.Success>(queryResult)
            assertEquals("tmdb", queryResult.providerId)
            assertNull(queryResult.extras)

            val result = queryResult.results.singleOrNull()
            assertNotNull(result)
            assertIs<MetadataMatch.TvShowMatch>(result)

            assertEquals(remoteId, result.remoteId)
            assertEquals(remoteId, result.tvShow.id.value)
            assertEquals("456", result.remoteMetadataId)

            assertEquals("The Simpsons", result.tvShow.name)
        }

        test("find tmdb movie by remote id") {
            val remoteId = "tmdb:movie:77"
            val queryResult = manager.findByRemoteId(remoteId)

            assertIs<QueryMetadataResult.Success>(queryResult)
            assertEquals("tmdb", queryResult.providerId)
            assertNull(queryResult.extras)

            val result = queryResult.results.singleOrNull()
            assertNotNull(result)
            assertIs<MetadataMatch.MovieMatch>(result)

            assertEquals(remoteId, result.remoteId)
            assertEquals(remoteId, result.movie.id.value)
            assertEquals("77", result.remoteMetadataId)

            assertEquals("Memento", result.movie.title)
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
                    coEvery { id } returns "tmdb"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE, MediaKind.TV)
                    coEvery { search(any()) } returns QueryMetadataResult.Success(
                        "tmdb",
                        listOf(createMovieMatch("tmdb:movie:42", "Test Movie", "tmdb")),
                        null,
                    )
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider), metadataDao, imageStore)

                val result = service.findByRemoteId("tmdb:movie:42")
                result.shouldBeInstanceOf<QueryMetadataResult.Success>()
            }

            test("findByRemoteId parses tv remote id with season and episode") {
                val provider = mockk<MetadataProvider> {
                    coEvery { id } returns "tmdb"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE, MediaKind.TV)
                    coEvery {
                        search(
                            match { req ->
                                req.providerId == "tmdb" &&
                                    req.mediaKind == MediaKind.TV &&
                                    req.metadataId == "456" &&
                                    req.extras is QueryMetadata.Extras.TvShowExtras
                            },
                        )
                    } returns QueryMetadataResult.Success("tmdb", emptyList(), null)
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider), metadataDao, imageStore)

                val result = service.findByRemoteId("tmdb:tv:456-2-5")
                result.shouldBeInstanceOf<QueryMetadataResult.Success>()
            }

            test("findByRemoteId returns ErrorProviderNotFound when no providers match") {
                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(emptySet(), metadataDao, imageStore)

                val result = service.findByRemoteId("tmdb:movie:1")
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
                    match = createMovieMatch("m1", "Imported Movie", "tmdb"),
                )
                val provider = mockk<MetadataProvider> {
                    coEvery { id } returns "tmdb"
                    coEvery { mediaKinds } returns listOf(MediaKind.MOVIE)
                    coEvery { importMetadata(any()) } returns listOf(expectedResult)
                }

                val metadataDao = mockk<MetadataDao>()
                val imageStore = mockk<ImageStore>()
                val service = MetadataService(setOf(provider), metadataDao, imageStore)

                val results = service.importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("1"),
                        providerId = "tmdb",
                        mediaKind = MediaKind.MOVIE,
                    ),
                )
                results.shouldHaveSize(1)
                results.first().shouldBeInstanceOf<ImportMetadataResult.Success>()
            }
        }
    })
