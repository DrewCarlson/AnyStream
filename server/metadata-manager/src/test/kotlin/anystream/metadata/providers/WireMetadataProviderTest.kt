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
package anystream.metadata.providers

import anystream.data.MetadataDbQueries
import anystream.db.*
import anystream.metadata.ImageStore
import anystream.metadata.testing.WireFixtures
import anystream.models.MediaKind
import anystream.models.MediaType
import anystream.models.MetadataId
import anystream.models.api.ImportMetadata
import anystream.models.api.ImportMetadataResult
import anystream.models.api.MetadataMatch
import anystream.models.api.QueryMetadata
import anystream.models.api.QueryMetadataResult
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import wire.client.WireApiClient
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Drives [WireMetadataProvider] through a real [WireApiClient] over [WireFixtures]'s [MockEngine] so the
 * production JSON deserialization path is exercised. Assertions reference literal values from the
 * recorded fixtures.
 */
class WireMetadataProviderTest :
    FunSpec({
        WireFixtures.verifyFixturesAvailable()

        val db by bindTestDatabase()
        val metadataDao by bindForTest({ MetadataDao(db) })
        val queries by bindForTest({
            val tagsDao = TagsDao(db)
            val playbackStatesDao = PlaybackStatesDao(db)
            val mediaLinkDao = MediaLinkDao(db)
            MetadataDbQueries(db, metadataDao, tagsDao, mediaLinkDao, playbackStatesDao)
        })
        val fs by bindFileSystem()

        // Stub image cache so import flows don't hit real image URLs.
        fun imageStore(): ImageStore {
            val httpClient = HttpClient(
                MockEngine { _ ->
                    respond(byteArrayOf(), HttpStatusCode.OK, headersOf())
                },
            )
            return ImageStore(fs.getPath("/test"), httpClient)
        }

        val provider by bindForTest({
            WireMetadataProvider(WireFixtures.mockWireApi(), queries, imageStore())
        })

        // Literal values pulled from the recorded fixtures.
        val mementoWireId = "69d9d842010203040537cfea"
        val pittShowWireId = "69d9548401020304052e2405"
        val pittSeason1WireId = "69d9548401020304052e2d29"
        val pittSeason1Episode1WireId = "69d9548401020304052e2ed9"
        val pittExpectedSeasonCount = 3
        val pittExpectedEpisodeCount = 30
        val mementoSearchPageSize = 20
        val pittSearchPageSize = 10

        // -- Provider metadata --------------------------------------------------------------------------

        test("id is wire") {
            provider.id shouldBe "wire"
        }

        test("supports MOVIE and TV media kinds") {
            provider.mediaKinds shouldBe listOf(MediaKind.MOVIE, MediaKind.TV)
        }

        // -- Search input validation --------------------------------------------------------------------

        test("search throws for unsupported media kinds") {
            val unsupported = MediaKind.entries - MediaKind.TV - MediaKind.MOVIE
            unsupported.forEach { kind ->
                shouldThrowExactly<IllegalStateException> {
                    provider.search(
                        QueryMetadata(
                            providerId = null,
                            mediaKind = kind,
                            query = "x",
                            metadataId = null,
                            year = null,
                            extras = null,
                        ),
                    )
                }.asClue { it.message shouldBe "Unsupported MediaKind: $kind" }
            }
        }

        // -- Search by metadataId -----------------------------------------------------------------------

        test("search movie by metadataId returns single match with wire id") {
            val result = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.MOVIE,
                        query = null,
                        metadataId = "77",
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveSize(1)
            val match = result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
            match.providerId shouldBe "wire"
            match.remoteMetadataId shouldBe "77"
            match.remoteId shouldBe "wire:movie:77"
            match.exists shouldBe false
            match.movie.title shouldBe "Memento"
            match.movie.id shouldBe MetadataId(mementoWireId)
            match.movie.tmdbId shouldBe 77
            match.movie.imdbId shouldBe "tt0209144"
            match.movie.runtime shouldBe 113.minutes
            match.movie.releaseDate shouldBe Instant.parse("2001-01-20T00:00:00Z")
            // tmdb-origin rating ~8.2 → 0..100 scale.
            match.movie.tmdbRating shouldBe 81
            // Provider prefers US certification when available.
            match.movie.contentRating shouldBe "R"
        }

        test("search tv show by metadataId returns single match with wire id and seasons") {
            val result = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = null,
                        metadataId = "250307",
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            val match = result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
            match.providerId shouldBe "wire"
            match.remoteId shouldBe "wire:tv:250307"
            match.tvShow.id shouldBe MetadataId(pittShowWireId)
            match.tvShow.name shouldBe "The Pitt"
            match.tvShow.tmdbId shouldBe 250307
            // The Pitt has seasons 1, 2, 3 — none are specials.
            match.seasons.shouldHaveSize(pittExpectedSeasonCount)
            match.seasons.none { it.seasonNumber == 0 } shouldBe true
            match.seasons.first { it.seasonNumber == 1 }.id shouldBe MetadataId(pittSeason1WireId)
            // 15 episodes in season 1 + 15 in season 2 + 0 in season 3 = 30.
            match.episodes.shouldHaveSize(pittExpectedEpisodeCount)
            match.episodes
                .first { it.seasonNumber == 1 && it.number == 1 }
                .id shouldBe MetadataId(pittSeason1Episode1WireId)
        }

        test("search tv show with TvShowExtras filters seasons and episodes") {
            val result = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = null,
                        metadataId = "250307",
                        year = null,
                        extras = QueryMetadata.Extras.TvShowExtras(
                            seasonNumber = 1,
                            episodeNumber = 1,
                        ),
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            val match = result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
            match.seasons.shouldHaveSize(1)
            match.seasons.first().seasonNumber shouldBe 1
            match.episodes.shouldHaveSize(1)
            match.episodes.first().number shouldBe 1
            match.episodes.first().seasonNumber shouldBe 1
            match.episodes.first().id shouldBe MetadataId(pittSeason1Episode1WireId)
        }

        // -- Search by query ----------------------------------------------------------------------------

        test("search movie by query fans out to getMovie for each result") {
            val result = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.MOVIE,
                        query = "Memento",
                        metadataId = null,
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            // 20 search results × one getMovie call each. Mock resolves every /movie/{id} to Memento.
            result.results.shouldHaveSize(mementoSearchPageSize)
            result.results.forEach { match ->
                match
                    .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
                    .asClue { it.movie.title shouldBe "Memento" }
            }
        }

        test("search movie by query honors firstResultOnly") {
            val result = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.MOVIE,
                        query = "Memento",
                        metadataId = null,
                        year = null,
                        extras = null,
                        firstResultOnly = true,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveSize(1)
            result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
                .movie.title shouldBe "Memento"
        }

        test("search tv by query fans out to getTvShow for each result") {
            val result = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = "The Pitt",
                        metadataId = null,
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            // 10 search results × one getTvShow call each. Mock resolves every /tv/{id} to The Pitt.
            result.results.shouldHaveSize(pittSearchPageSize)
            result.results.forEach { match ->
                match
                    .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
                    .asClue { it.tvShow.name shouldBe "The Pitt" }
            }
        }

        test("search tv by query honors firstResultOnly") {
            val result = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = "The Pitt",
                        metadataId = null,
                        year = null,
                        extras = null,
                        firstResultOnly = true,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveSize(1)
            result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
                .tvShow.name shouldBe "The Pitt"
        }

        test("search returns Success with empty results when no shows are returned") {
            val result = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = "NoMatchExists12345Zxq",
                        metadataId = null,
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveSize(0)
        }

        test("search returns ErrorDataProviderException when wire client throws") {
            val throwingProvider = WireMetadataProvider(
                WireFixtures.wireApiThrowing(IOException("network down")),
                queries,
                imageStore(),
            )

            throwingProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.MOVIE,
                        query = null,
                        metadataId = "77",
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.ErrorDataProviderException>()
        }

        // -- Import movie -------------------------------------------------------------------------------

        test("import movie persists with the wire id as the local metadata id") {
            val result = provider
                .importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("77"),
                        providerId = "wire",
                        mediaKind = MediaKind.MOVIE,
                    ),
                )
            result.shouldHaveSize(1)
            val success = result.first().shouldBeInstanceOf<ImportMetadataResult.Success>()
            val match = success.match.shouldBeInstanceOf<MetadataMatch.MovieMatch>()
            match.movie.id shouldBe MetadataId(mementoWireId)
            match.movie.title shouldBe "Memento"
            match.movie.tmdbId shouldBe 77
            match.movie.runtime shouldBe 113.minutes
            match.movie.releaseDate shouldBe Instant.parse("2001-01-20T00:00:00Z")

            // Persisted under the wire id.
            val stored = queries.findMediaById(MetadataId(mementoWireId))
            val storedMovie = stored.movie.shouldBeInstanceOf<anystream.models.Movie>()
            storedMovie.id shouldBe MetadataId(mementoWireId)
            storedMovie.tmdbId shouldBe 77
            storedMovie.title shouldBe "Memento"
        }

        test("import movie twice without refresh returns ErrorMetadataAlreadyExists") {
            provider
                .importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("77"),
                        providerId = "wire",
                        mediaKind = MediaKind.MOVIE,
                    ),
                ).first()
                .shouldBeInstanceOf<ImportMetadataResult.Success>()

            val secondImport = provider.importMetadata(
                ImportMetadata(
                    metadataIds = listOf("77"),
                    providerId = "wire",
                    mediaKind = MediaKind.MOVIE,
                    refresh = false,
                ),
            )
            secondImport.shouldHaveSize(1)
            secondImport.first().shouldBeInstanceOf<ImportMetadataResult.ErrorMetadataAlreadyExists>()
        }

        test("import movie returns ErrorDataProviderException when wire client throws") {
            val throwingProvider = WireMetadataProvider(
                WireFixtures.wireApiThrowing(IOException("network down")),
                queries,
                imageStore(),
            )

            val result = throwingProvider.importMetadata(
                ImportMetadata(
                    metadataIds = listOf("77"),
                    providerId = "wire",
                    mediaKind = MediaKind.MOVIE,
                ),
            )
            result.shouldHaveSize(1)
            result.first().shouldBeInstanceOf<ImportMetadataResult.ErrorDataProviderException>()
        }

        // -- Import TV show -----------------------------------------------------------------------------

        test("import tv show persists show, seasons and episodes with wire ids") {
            val result = provider
                .importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("250307"),
                        providerId = "wire",
                        mediaKind = MediaKind.TV,
                    ),
                )
            result.shouldHaveSize(1)
            val success = result.first().shouldBeInstanceOf<ImportMetadataResult.Success>()
            val match = success.match.shouldBeInstanceOf<MetadataMatch.TvShowMatch>()

            match.tvShow.id shouldBe MetadataId(pittShowWireId)
            match.tvShow.name shouldBe "The Pitt"
            match.tvShow.tmdbId shouldBe 250307
            match.seasons.shouldHaveSize(pittExpectedSeasonCount)
            match.seasons.none { it.seasonNumber == 0 } shouldBe true
            match.episodes.shouldHaveSize(pittExpectedEpisodeCount)

            // Persisted under the wire ids.
            val storedShow = queries.findMediaById(MetadataId(pittShowWireId))
            val storedTvShow = storedShow.tvShow.shouldBeInstanceOf<anystream.models.TvShow>()
            storedTvShow.id shouldBe MetadataId(pittShowWireId)
            storedTvShow.tmdbId shouldBe 250307
            storedTvShow.name shouldBe "The Pitt"

            val storedSeason = metadataDao.find(MetadataId(pittSeason1WireId))
            storedSeason?.mediaType shouldBe MediaType.TV_SEASON
            storedSeason?.parentId shouldBe MetadataId(pittShowWireId)

            val storedEpisode = metadataDao.find(MetadataId(pittSeason1Episode1WireId))
            storedEpisode?.mediaType shouldBe MediaType.TV_EPISODE
            storedEpisode?.parentId shouldBe MetadataId(pittSeason1WireId)
            storedEpisode?.rootId shouldBe MetadataId(pittShowWireId)
        }

        test("import tv show twice without refresh returns ErrorMetadataAlreadyExists") {
            provider
                .importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("250307"),
                        providerId = "wire",
                        mediaKind = MediaKind.TV,
                    ),
                ).first()
                .shouldBeInstanceOf<ImportMetadataResult.Success>()

            val secondImport = provider.importMetadata(
                ImportMetadata(
                    metadataIds = listOf("250307"),
                    providerId = "wire",
                    mediaKind = MediaKind.TV,
                    refresh = false,
                ),
            )
            secondImport.shouldHaveSize(1)
            secondImport.first().shouldBeInstanceOf<ImportMetadataResult.ErrorMetadataAlreadyExists>()
        }

        test("import tv show returns ErrorDataProviderException when wire client throws") {
            val throwingProvider = WireMetadataProvider(
                WireFixtures.wireApiThrowing(IOException("network down")),
                queries,
                imageStore(),
            )

            val result = throwingProvider.importMetadata(
                ImportMetadata(
                    metadataIds = listOf("250307"),
                    providerId = "wire",
                    mediaKind = MediaKind.TV,
                ),
            )
            result.shouldHaveSize(1)
            result.first().shouldBeInstanceOf<ImportMetadataResult.ErrorDataProviderException>()
        }

        // -- exists flag round-trips through tmdb id lookup ----------------------------------------------

        test("search after import flips exists=true and reuses the persisted wire id") {
            provider
                .importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("77"),
                        providerId = "wire",
                        mediaKind = MediaKind.MOVIE,
                    ),
                ).first()
                .shouldBeInstanceOf<ImportMetadataResult.Success>()

            val searchResult = provider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.MOVIE,
                        query = null,
                        metadataId = "77",
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            val match = searchResult.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
            match.exists shouldBe true
            match.movie.id shouldBe MetadataId(mementoWireId)
        }
    })
