/**
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
import anystream.models.MediaKind
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
import io.ktor.client.plugins.logging.*

class TmdbMetadataProviderTest : FunSpec({
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

    val tmdbProvider by bindForTest({
        TmdbMetadataProvider(tmdb, queries)
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
                    )
                )
            }.asClue { error ->
                error.message shouldBe "Unsupported MediaKind: $kind"
            }
        }
    }

    test("search for tv show") {
        val result = tmdbProvider.search(
            QueryMetadata(
                providerId = null,
                mediaKind = MediaKind.TV,
                query = "Ninja Turtles",
                metadataId = null,
                year = 2012,
                extras = null,
            )
        ).shouldBeInstanceOf<QueryMetadataResult.Success>()

        result.results.shouldHaveAtLeastSize(1)

        val match = result.results
            .first()
            .shouldBeInstanceOf<MetadataMatch.TvShowMatch>()

        match.tvShow.asClue { tvShow ->
            tvShow.name shouldBe "Teenage Mutant Ninja Turtles"
            tvShow.firstAirDate shouldBe "2012-9-28"
        }
    }

    test("search for tv season") {
        val result = tmdbProvider.search(
            QueryMetadata(
                providerId = null,
                mediaKind = MediaKind.TV,
                query = "Ninja Turtles",
                metadataId = null,
                year = 2012,
                extras = QueryMetadata.Extras.TvShowExtras(
                    seasonNumber = 1
                ),
            )
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
        val result = tmdbProvider.search(
            QueryMetadata(
                providerId = null,
                mediaKind = MediaKind.TV,
                query = "Ninja Turtles",
                metadataId = null,
                year = 2012,
                extras = QueryMetadata.Extras.TvShowExtras(
                    seasonNumber = 1,
                    episodeNumber = 1
                ),
            )
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

        /*tmdbProvider.importMetadata(
            ImportMetadata(
                metadataIds = listOf(match.remoteId),
                mediaKind = MediaKind.TV,
                providerId = match.providerId,
            )
        )*/
    }

    test("search for movie") {
        val result = tmdbProvider.search(
            QueryMetadata(
                providerId = null,
                mediaKind = MediaKind.MOVIE,
                query = "Ninja Turtles",
                metadataId = null,
                year = 2023,
                extras = null,
            )
        ).shouldBeInstanceOf<QueryMetadataResult.Success>()

        result.results.shouldHaveAtLeastSize(1)

        result.results
            .first()
            .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
            .asClue { (movie) ->
                movie.title shouldBe "Teenage Mutant Ninja Turtles: Mutant Mayhem"
                movie.releaseDate shouldBe "2023-7-31"
            }
    }
})