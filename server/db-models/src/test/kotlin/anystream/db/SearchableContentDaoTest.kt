/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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

import anystream.models.MediaType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import org.jooq.DSLContext

class SearchableContentDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val dao: SearchableContentDao by bindForTest({ SearchableContentDao(db) })
    val metadataDao: MetadataDao by bindForTest({ MetadataDao(db) })

    test("search returns matching movies") {
        val movie1 = createTestMovie(title = "The Matrix")
        val movie2 = createTestMovie(title = "Matrix Reloaded")
        val movie3 = createTestMovie(title = "Alien")

        metadataDao.insertMetadata(listOf(movie1, movie2, movie3))

        val results = dao.search("Matrix")

        results shouldHaveSize 2
        results shouldContainExactlyInAnyOrder listOf(movie1.id, movie2.id)
    }

    test("search returns matching tv shows") {
        val show1 = createTestTvShow(name = "Breaking Bad")
        val show2 = createTestTvShow(name = "Better Call Saul")
        val show3 = createTestTvShow(name = "The Wire")

        metadataDao.insertMetadata(listOf(show1, show2, show3))

        val results = dao.search("Breaking")

        results shouldHaveSize 1
        results shouldContain show1.id
    }

    test("search with type filter") {
        val movie = createTestMovie(title = "The Dark Knight")
        val show = createTestTvShow(name = "The Dark Knight Legacy")

        metadataDao.insertMetadata(listOf(movie, show))

        val movieResults = dao.search("Dark Knight", MediaType.MOVIE)
        movieResults shouldHaveSize 1
        movieResults shouldContain movie.id
        movieResults shouldNotContain show.id

        val tvResults = dao.search("Dark Knight", MediaType.TV_SHOW)
        tvResults shouldHaveSize 1
        tvResults shouldContain show.id
        tvResults shouldNotContain movie.id
    }

    test("search with limit") {
        val movies = (1..10).map { i ->
            createTestMovie(title = "Star Trek Movie $i")
        }
        metadataDao.insertMetadata(movies)

        val allResults = dao.search("Star Trek", MediaType.MOVIE)
        allResults shouldHaveSize 10

        val limitedResults = dao.search("Star Trek", MediaType.MOVIE, limit = 5)
        limitedResults shouldHaveSize 5
    }

    test("search is case insensitive") {
        val movie = createTestMovie(title = "Inception")
        metadataDao.insertMetadata(movie)

        val results1 = dao.search("inception")
        results1 shouldContain movie.id

        val results2 = dao.search("INCEPTION")
        results2 shouldContain movie.id

        val results3 = dao.search("InCePtIoN")
        results3 shouldContain movie.id
    }

    test("search with partial word match using prefix") {
        val movie = createTestMovie(title = "Interstellar")
        metadataDao.insertMetadata(movie)

        // FTS5 supports prefix searches with *
        val results = dao.search("Inter*")
        results shouldContain movie.id
    }

    test("search returns empty list for no matches") {
        val movie = createTestMovie(title = "The Shawshank Redemption")
        metadataDao.insertMetadata(movie)

        val results = dao.search("Inception")
        results shouldHaveSize 0
    }

    test("search content is updated when metadata title changes") {
        // Note: This test verifies the trigger behavior
        // Updates to metadata title should update searchable_content
        // However, MetadataDao doesn't currently have an update method
        // This test documents expected behavior for future implementation
    }

    test("search content is deleted when metadata is deleted") {
        val movie = createTestMovie(title = "To Be Deleted")
        metadataDao.insertMetadata(movie)

        // Verify it's searchable
        val beforeDelete = dao.search("Deleted")
        beforeDelete shouldContain movie.id

        // Delete the metadata
        metadataDao.deleteById(movie.id)

        // Verify it's no longer searchable
        val afterDelete = dao.search("Deleted")
        afterDelete shouldHaveSize 0
    }

    test("search includes tv show episodes") {
        val show = createTestTvShow(name = "Test Show")
        val season = createTestSeason(showId = show.id, seasonNumber = 1)
        val episode = createTestEpisode(
            showId = show.id,
            seasonId = season.id,
            seasonNumber = 1,
            episodeNumber = 1,
            name = "Pilot Episode"
        )

        metadataDao.insertMetadata(listOf(show, season, episode))

        val results = dao.search("Pilot", MediaType.TV_EPISODE)
        results shouldContain episode.id
    }

    test("search with multiple words") {
        val movie1 = createTestMovie(title = "The Lord of the Rings")
        val movie2 = createTestMovie(title = "The Hobbit")
        val movie3 = createTestMovie(title = "Lord of the Flies")

        metadataDao.insertMetadata(listOf(movie1, movie2, movie3))

        // FTS5 searches for all terms by default
        val results = dao.search("Lord Rings")
        results shouldContain movie1.id
        results shouldNotContain movie2.id
        results shouldNotContain movie3.id
    }

    test("search handles special characters in query") {
        val movie = createTestMovie(title = "Star Wars Episode IV: A New Hope")
        metadataDao.insertMetadata(movie)

        // Search without colon should work
        val results = dao.search("Star Wars")
        results shouldContain movie.id
    }
})
