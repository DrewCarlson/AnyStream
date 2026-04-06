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
package anystream.db

import anystream.models.*
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import kotlin.time.Clock

class SearchableContentDaoTest :
    FunSpec({

        val db: DSLContext by bindTestDatabase()
        val metadataDao by bindForTest({ MetadataDao(db) })
        val searchDao by bindForTest({ SearchableContentDao(db) })

        fun createMetadata(
            title: String,
            mediaType: MediaType = MediaType.MOVIE,
            mediaKind: MediaKind = MediaKind.MOVIE,
            id: String = ObjectId.next(),
        ): Metadata {
            val now = Clock.System.now()
            return Metadata(
                id = MetadataId(id),
                title = title,
                mediaType = mediaType,
                mediaKind = mediaKind,
                createdAt = now,
                updatedAt = now,
            )
        }

        test("search returns empty for no matches") {
            searchDao.search("nonexistent").shouldBeEmpty()
        }

        test("search finds metadata by title") {
            val movie = createMetadata("The Matrix")
            metadataDao.insertMetadata(movie)

            val results = searchDao.search("Matrix")
            results.shouldHaveSize(1)
            results.first() shouldBe movie.id
        }

        test("search finds multiple matching records") {
            val movie1 = createMetadata("Star Wars A New Hope")
            val movie2 = createMetadata("Star Wars Empire Strikes Back")
            val movie3 = createMetadata("The Matrix")
            metadataDao.insertMetadata(movie1)
            metadataDao.insertMetadata(movie2)
            metadataDao.insertMetadata(movie3)

            val results = searchDao.search("Star Wars")
            results.shouldHaveSize(2)
            results.shouldContainExactlyInAnyOrder(movie1.id, movie2.id)
        }

        test("search with MediaType filter") {
            val movie = createMetadata("Breaking Bad Movie", mediaType = MediaType.MOVIE, mediaKind = MediaKind.MOVIE)
            val show = createMetadata("Breaking Bad", mediaType = MediaType.TV_SHOW, mediaKind = MediaKind.TV)
            metadataDao.insertMetadata(movie)
            metadataDao.insertMetadata(show)

            val movieResults = searchDao.search("Breaking Bad", MediaType.MOVIE)
            movieResults.shouldHaveSize(1)
            movieResults.first() shouldBe movie.id

            val tvResults = searchDao.search("Breaking Bad", MediaType.TV_SHOW)
            tvResults.shouldHaveSize(1)
            tvResults.first() shouldBe show.id
        }

        test("search with MediaType filter returns empty for no matches") {
            val movie = createMetadata("Some Movie")
            metadataDao.insertMetadata(movie)

            searchDao.search("Some Movie", MediaType.TV_SHOW).shouldBeEmpty()
        }

        test("search with MediaType and limit") {
            val movies = (1..5).map { i ->
                createMetadata("Action Movie $i", mediaType = MediaType.MOVIE)
            }
            movies.forEach { metadataDao.insertMetadata(it) }

            val results = searchDao.search("Action Movie", MediaType.MOVIE, limit = 3)
            results.shouldHaveSize(3)
        }

        test("search with limit returns all when fewer than limit") {
            val movie = createMetadata("Unique Title")
            metadataDao.insertMetadata(movie)

            val results = searchDao.search("Unique Title", MediaType.MOVIE, limit = 10)
            results.shouldHaveSize(1)
            results.first() shouldBe movie.id
        }

        test("search reflects metadata deletion via trigger") {
            val movie = createMetadata("Deletable Movie")
            metadataDao.insertMetadata(movie)

            searchDao.search("Deletable Movie").shouldHaveSize(1)

            metadataDao.deleteById(movie.id)

            searchDao.search("Deletable Movie").shouldBeEmpty()
        }

        test("search with prefix matching") {
            val movie = createMetadata("Interstellar")
            metadataDao.insertMetadata(movie)

            // FTS5 supports prefix queries with *
            val results = searchDao.search("Inter*")
            results.shouldHaveSize(1)
            results.first() shouldBe movie.id
        }

        test("search across different media types without filter") {
            val movie = createMetadata("Adventure Time Movie", mediaType = MediaType.MOVIE, mediaKind = MediaKind.MOVIE)
            val show = createMetadata("Adventure Time", mediaType = MediaType.TV_SHOW, mediaKind = MediaKind.TV)
            metadataDao.insertMetadata(movie)
            metadataDao.insertMetadata(show)

            val results = searchDao.search("Adventure Time")
            results.shouldHaveSize(2)
            results.shouldContain(movie.id)
            results.shouldContain(show.id)
        }
    })
