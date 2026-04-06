/*
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
import anystream.models.MediaKind
import anystream.models.api.ImportMetadata
import anystream.models.api.ImportMetadataResult
import anystream.models.api.MetadataMatch
import anystream.models.api.QueryMetadata
import anystream.models.api.QueryMetadataResult
import app.moviebase.tmdb.Tmdb3
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.*
import kotlin.time.Instant

class TmdbMetadataProviderTest :
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
        val tmdbProvider by bindForTest({
            val imageStore = ImageStore(fs.getPath("/test"), HttpClient())
            TmdbMetadataProvider(tmdb, queries, imageStore)
        })

        test("search for unsupported media") {
            val kinds = MediaKind.entries - MediaKind.TV - MediaKind.MOVIE
            kinds.forEach { kind ->
                shouldThrowExactly<IllegalStateException> {
                    tmdbProvider.search(
                        QueryMetadata(
                            providerId = null,
                            mediaKind = kind,
                            query = "Ninja Turtles",
                            metadataId = null,
                            year = null,
                            extras = null,
                        ),
                    )
                }.asClue { error ->
                    error.message shouldBe "Unsupported MediaKind: $kind"
                }
            }
        }

        test("search for tv show") {
            val result = tmdbProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = "Ninja Turtles",
                        metadataId = null,
                        year = 2012,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveAtLeastSize(1)

            val match = result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()

            match.tvShow.asClue { tvShow ->
                tvShow.name shouldBe "Teenage Mutant Ninja Turtles"
                tvShow.firstAirDate shouldBe Instant.parse("2012-09-28T00:00:00Z")
            }
        }

        test("search for tv season") {
            val result = tmdbProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = "Ninja Turtles",
                        metadataId = null,
                        year = 2012,
                        extras = QueryMetadata.Extras.TvShowExtras(
                            seasonNumber = 1,
                        ),
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveSize(1)

            val match = result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()

            match.seasons
                .shouldHaveSize(1)
                .first()
                .asClue { season ->
                    season.seasonNumber shouldBe 1
                    season.name shouldBe "Season 1"
                }

            match.episodes.shouldHaveAtLeastSize(1)
        }

        test("search for tv episode") {
            val result = tmdbProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = "Ninja Turtles",
                        metadataId = null,
                        year = 2012,
                        extras = QueryMetadata.Extras.TvShowExtras(
                            seasonNumber = 1,
                            episodeNumber = 1,
                        ),
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveAtLeastSize(1)

            val match = result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()

            match.episodes
                .shouldHaveSize(1)
                .first()
                .asClue { episode ->
                    episode.name shouldBe "Rise of the Turtles (1)"
                    episode.number shouldBe 1
                }
        }

        test("search for movie") {
            val result = tmdbProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.MOVIE,
                        query = "Ninja Turtles",
                        metadataId = null,
                        year = 2023,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveAtLeastSize(1)

            result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
                .asClue { match ->
                    match.movie.title shouldBe "Teenage Mutant Ninja Turtles: Mutant Mayhem"
                    match.movie.releaseDate shouldBe Instant.parse("2023-07-31T00:00:00Z")
                }
        }

        test("search movie by metadataId returns specific movie") {
            val result = tmdbProvider
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

            result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
                .asClue { match ->
                    match.movie.title shouldBe "Memento"
                    match.remoteMetadataId shouldBe "77"
                    match.exists shouldBe false
                }
        }

        test("search tv show by metadataId returns specific show") {
            val result = tmdbProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = null,
                        metadataId = "456",
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveSize(1)

            result.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
                .asClue { match ->
                    match.tvShow.name shouldBe "The Simpsons"
                    match.remoteMetadataId shouldBe "456"
                }
        }

        test("search movie with nonexistent metadataId returns provider error") {
            val result = tmdbProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.MOVIE,
                        query = null,
                        metadataId = "999999999",
                        year = null,
                        extras = null,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.ErrorDataProviderException>()
        }

        test("search movie with firstResultOnly limits results") {
            val result = tmdbProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.MOVIE,
                        query = "Star Wars",
                        metadataId = null,
                        year = null,
                        extras = null,
                        firstResultOnly = true,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveSize(1)
        }

        test("search tv show with firstResultOnly limits results") {
            val result = tmdbProvider
                .search(
                    QueryMetadata(
                        providerId = null,
                        mediaKind = MediaKind.TV,
                        query = "Star Trek",
                        metadataId = null,
                        year = null,
                        extras = null,
                        firstResultOnly = true,
                    ),
                ).shouldBeInstanceOf<QueryMetadataResult.Success>()

            result.results.shouldHaveSize(1)
        }

        test("import movie then search returns exists=true") {
            val importResult = tmdbProvider
                .importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("77"),
                        providerId = "tmdb",
                        mediaKind = MediaKind.MOVIE,
                    ),
                )
            importResult.shouldHaveSize(1)
            importResult.first().shouldBeInstanceOf<ImportMetadataResult.Success>()

            val searchResult = tmdbProvider
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

            searchResult.results.shouldHaveSize(1)
            searchResult.results
                .first()
                .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
                .asClue { match ->
                    match.exists shouldBe true
                    match.movie.title shouldBe "Memento"
                }
        }

        test("import movie twice without refresh returns ErrorMetadataAlreadyExists") {
            tmdbProvider
                .importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("77"),
                        providerId = "tmdb",
                        mediaKind = MediaKind.MOVIE,
                    ),
                ).first()
                .shouldBeInstanceOf<ImportMetadataResult.Success>()

            val secondImport = tmdbProvider.importMetadata(
                ImportMetadata(
                    metadataIds = listOf("77"),
                    providerId = "tmdb",
                    mediaKind = MediaKind.MOVIE,
                    refresh = false,
                ),
            )
            secondImport.shouldHaveSize(1)
            secondImport.first().shouldBeInstanceOf<ImportMetadataResult.ErrorMetadataAlreadyExists>()
        }

        test("import tv show twice without refresh returns ErrorMetadataAlreadyExists") {
            tmdbProvider
                .importMetadata(
                    ImportMetadata(
                        metadataIds = listOf("63333"),
                        providerId = "tmdb",
                        mediaKind = MediaKind.TV,
                    ),
                ).first()
                .shouldBeInstanceOf<ImportMetadataResult.Success>()

            val secondImport = tmdbProvider.importMetadata(
                ImportMetadata(
                    metadataIds = listOf("63333"),
                    providerId = "tmdb",
                    mediaKind = MediaKind.TV,
                    refresh = false,
                ),
            )
            secondImport.shouldHaveSize(1)
            secondImport.first().shouldBeInstanceOf<ImportMetadataResult.ErrorMetadataAlreadyExists>()
        }
    })
