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
package anystream.service.search

import anystream.db.MediaLinkDao
import anystream.db.MetadataDao
import anystream.db.SearchableContentDao
import anystream.db.bindForTest
import anystream.db.bindTestDatabase
import anystream.models.MediaKind
import anystream.models.MediaType
import anystream.models.Metadata
import anystream.models.MetadataId
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class SearchServiceTest :
    FunSpec({

        val db by bindTestDatabase()
        val metadataDao by bindForTest({ MetadataDao(db) })
        val searchDao by bindForTest({ SearchableContentDao(db) })
        val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
        val searchService by bindForTest({
            SearchService(searchDao, metadataDao, mediaLinkDao)
        })

        fun createMovie(
            title: String,
            tmdbId: Int = 100,
        ): Metadata {
            val now = Clock.System.now()
            return Metadata(
                id = MetadataId(ObjectId.next()),
                title = title,
                overview = "A test movie",
                tmdbId = tmdbId,
                imdbId = "tt$tmdbId",
                runtime = 120.minutes,
                createdAt = now,
                updatedAt = now,
                mediaKind = MediaKind.MOVIE,
                mediaType = MediaType.MOVIE,
            )
        }

        fun createTvShow(
            title: String,
            tmdbId: Int = 200,
        ): Metadata {
            val now = Clock.System.now()
            return Metadata(
                id = MetadataId(ObjectId.next()),
                title = title,
                overview = "A test show",
                tmdbId = tmdbId,
                createdAt = now,
                updatedAt = now,
                mediaKind = MediaKind.TV,
                mediaType = MediaType.TV_SHOW,
            )
        }

        test("search returns empty response for no matches") {
            val result = searchService.search("nonexistent", limit = 10)

            result.movies.shouldBeEmpty()
            result.tvShows.shouldBeEmpty()
            result.episodes.shouldBeEmpty()
            result.mediaLink.shouldBeEmpty()
        }

        test("search finds movies") {
            val movie = createMovie("The Shawshank Redemption")
            metadataDao.insertMetadata(movie)

            val result = searchService.search("Shawshank", limit = 10)

            result.movies shouldHaveSize 1
            result.movies.first().title shouldBe "The Shawshank Redemption"
        }

        test("search finds tv shows") {
            val show = createTvShow("Breaking Bad")
            metadataDao.insertMetadata(show)

            val result = searchService.search("Breaking Bad", limit = 10)

            result.tvShows shouldHaveSize 1
            result.tvShows
                .first()
                .tvShow.name shouldBe "Breaking Bad"
        }

        test("search sanitizes quotes in input") {
            val movie = createMovie("Test Movie")
            metadataDao.insertMetadata(movie)

            // Input with embedded quotes should not cause SQL errors
            val result = searchService.search("\"Test\" \"Movie\"", limit = 10)

            result.movies shouldHaveSize 1
        }

        test("search handles leading and trailing whitespace") {
            val movie = createMovie("Whitespace Test")
            metadataDao.insertMetadata(movie)

            val result = searchService.search("  Whitespace Test  ", limit = 10)

            result.movies shouldHaveSize 1
        }

        test("search handles multi-word queries") {
            val movie = createMovie("Lord of the Rings")
            metadataDao.insertMetadata(movie)

            val result = searchService.search("Lord Rings", limit = 10)

            result.movies shouldHaveSize 1
        }

        test("search returns movies and shows simultaneously") {
            val movie = createMovie("Batman Movie", tmdbId = 101)
            val show = createTvShow("Batman Series", tmdbId = 201)
            metadataDao.insertMetadata(movie)
            metadataDao.insertMetadata(show)

            val result = searchService.search("Batman", limit = 10)

            result.movies shouldHaveSize 1
            result.tvShows shouldHaveSize 1
        }

        test("search respects limit") {
            (1..5).forEach { i ->
                metadataDao.insertMetadata(createMovie("Action Film $i", tmdbId = 100 + i))
            }

            val result = searchService.search("Action Film", limit = 2)

            result.movies shouldHaveSize 2
        }

        test("search with empty query returns empty results") {
            val movie = createMovie("Some Movie")
            metadataDao.insertMetadata(movie)

            val result = searchService.search("", limit = 10)

            result.movies.shouldBeEmpty()
            result.tvShows.shouldBeEmpty()
            result.episodes.shouldBeEmpty()
        }
    })
