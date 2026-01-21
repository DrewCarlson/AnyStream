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
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext

class MetadataDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val dao: MetadataDao by bindForTest({ MetadataDao(db) })

    test("insert and fetch movie metadata") {
        val movie = createTestMovie(title = "Test Movie", tmdbId = 12345)

        val insertedId = dao.insertMetadata(movie)

        insertedId shouldBeEqual movie.id

        val fetched = dao.find(movie.id)
        fetched.shouldNotBeNull()
        fetched.id shouldBeEqual movie.id
        fetched.title shouldBe "Test Movie"
        fetched.tmdbId shouldBe 12345
        fetched.mediaType shouldBeEqual MediaType.MOVIE
    }

    test("insert and fetch tv show with hierarchy") {
        val show = createTestTvShow(name = "Test Show", tmdbId = 54321)
        val season = createTestSeason(showId = show.id, seasonNumber = 1)
        val episode1 = createTestEpisode(
            showId = show.id,
            seasonId = season.id,
            seasonNumber = 1,
            episodeNumber = 1,
            name = "Pilot"
        )
        val episode2 = createTestEpisode(
            showId = show.id,
            seasonId = season.id,
            seasonNumber = 1,
            episodeNumber = 2,
            name = "Second Episode"
        )

        dao.insertMetadata(listOf(show, season, episode1, episode2))

        // Fetch show
        val fetchedShow = dao.findByIdAndType(show.id, MediaType.TV_SHOW)
        fetchedShow.shouldNotBeNull()
        fetchedShow.title shouldBe "Test Show"

        // Fetch season
        val fetchedSeason = dao.findByIdAndType(season.id, MediaType.TV_SEASON)
        fetchedSeason.shouldNotBeNull()
        fetchedSeason.rootId shouldBe show.id
        fetchedSeason.parentId shouldBe show.id
        fetchedSeason.index shouldBe 1

        // Fetch episodes
        val episodes = dao.findAllByParentIdAndType(season.id, MediaType.TV_EPISODE)
        episodes shouldHaveSize 2
        episodes.map { it.title } shouldContainExactlyInAnyOrder listOf("Pilot", "Second Episode")
    }

    test("find by tmdb id and type") {
        val movie = createTestMovie(title = "TMDB Test Movie", tmdbId = 99999)
        dao.insertMetadata(movie)

        val found = dao.findByTmdbIdAndType(99999, MediaType.MOVIE)
        found.shouldNotBeNull()
        found.title shouldBe "TMDB Test Movie"

        // Should not find with wrong type
        val notFound = dao.findByTmdbIdAndType(99999, MediaType.TV_SHOW)
        notFound.shouldBeNull()
    }

    test("find all by root id returns all descendants") {
        val show = createTestTvShow(name = "Multi-Season Show")
        val season1 = createTestSeason(showId = show.id, seasonNumber = 1)
        val season2 = createTestSeason(showId = show.id, seasonNumber = 2)
        val ep1s1 = createTestEpisode(show.id, season1.id, 1, 1)
        val ep2s1 = createTestEpisode(show.id, season1.id, 1, 2)
        val ep1s2 = createTestEpisode(show.id, season2.id, 2, 1)

        dao.insertMetadata(listOf(show, season1, season2, ep1s1, ep2s1, ep1s2))

        val seasons = dao.findAllByRootIdAndType(show.id, MediaType.TV_SEASON)
        seasons shouldHaveSize 2

        val episodes = dao.findAllByRootIdAndType(show.id, MediaType.TV_EPISODE)
        episodes shouldHaveSize 3
    }

    test("find all by parent id returns direct children") {
        val show = createTestTvShow(name = "Parent Test Show")
        val season = createTestSeason(showId = show.id, seasonNumber = 1)
        val ep1 = createTestEpisode(show.id, season.id, 1, 1)
        val ep2 = createTestEpisode(show.id, season.id, 1, 2)

        dao.insertMetadata(listOf(show, season, ep1, ep2))

        // Seasons are direct children of show
        val seasons = dao.findAllByParentIdAndType(show.id, MediaType.TV_SEASON)
        seasons shouldHaveSize 1
        seasons.first().id shouldBeEqual season.id

        // Episodes are direct children of season, not show
        val showEpisodes = dao.findAllByParentIdAndType(show.id, MediaType.TV_EPISODE)
        showEpisodes.shouldBeEmpty()

        val seasonEpisodes = dao.findAllByParentIdAndType(season.id, MediaType.TV_EPISODE)
        seasonEpisodes shouldHaveSize 2
    }

    test("delete by id removes single record") {
        val movie1 = createTestMovie(title = "Movie 1")
        val movie2 = createTestMovie(title = "Movie 2")
        dao.insertMetadata(listOf(movie1, movie2))

        dao.deleteById(movie1.id)

        dao.find(movie1.id).shouldBeNull()
        dao.find(movie2.id).shouldNotBeNull()
    }

    test("delete by root id removes all descendants") {
        val show = createTestTvShow(name = "Delete Test Show")
        val season = createTestSeason(showId = show.id, seasonNumber = 1)
        val ep1 = createTestEpisode(show.id, season.id, 1, 1)
        val ep2 = createTestEpisode(show.id, season.id, 1, 2)

        dao.insertMetadata(listOf(show, season, ep1, ep2))

        // Delete by root id should remove seasons and episodes
        dao.deleteByRootId(show.id)

        // Episodes and seasons should be deleted
        dao.find(season.id).shouldBeNull()
        dao.find(ep1.id).shouldBeNull()
        dao.find(ep2.id).shouldBeNull()

        // Show itself is NOT deleted by deleteByRootId (only descendants)
        dao.find(show.id).shouldNotBeNull()
    }

    test("count by type returns correct counts") {
        val movie1 = createTestMovie(title = "Movie 1")
        val movie2 = createTestMovie(title = "Movie 2")
        val show = createTestTvShow(name = "Show 1")

        dao.insertMetadata(listOf(movie1, movie2, show))

        dao.countByType(MediaType.MOVIE) shouldBeEqual 2
        dao.countByType(MediaType.TV_SHOW) shouldBeEqual 1
        dao.countByType(MediaType.TV_EPISODE) shouldBeEqual 0
    }

    test("find root id or self returns correct id") {
        val show = createTestTvShow(name = "Root Test Show")
        val season = createTestSeason(showId = show.id, seasonNumber = 1)
        val episode = createTestEpisode(show.id, season.id, 1, 1)

        dao.insertMetadata(listOf(show, season, episode))

        // For show (root), returns its own id
        dao.findRootIdOrSelf(show.id) shouldBe show.id

        // For season and episode, returns the show id
        dao.findRootIdOrSelf(season.id) shouldBe show.id
        dao.findRootIdOrSelf(episode.id) shouldBe show.id
    }

    test("find type returns correct media type") {
        val movie = createTestMovie(title = "Type Test Movie")
        val show = createTestTvShow(name = "Type Test Show")

        dao.insertMetadata(listOf(movie, show))

        dao.findType(movie.id) shouldBe MediaType.MOVIE
        dao.findType(show.id) shouldBe MediaType.TV_SHOW
        dao.findType("nonexistent").shouldBeNull()
    }

    test("find by type with limit") {
        val movies = (1..5).map { i -> createTestMovie(title = "Movie $i") }
        dao.insertMetadata(movies)

        val limited = dao.findByType(MediaType.MOVIE, limit = 3)
        limited shouldHaveSize 3
    }

    test("find all by type sorted by title") {
        val movieC = createTestMovie(title = "Charlie Movie")
        val movieA = createTestMovie(title = "Alpha Movie")
        val movieB = createTestMovie(title = "Bravo Movie")

        dao.insertMetadata(listOf(movieC, movieA, movieB))

        val sorted = dao.findAllByTypeSortedByTitle(MediaType.MOVIE)
        sorted shouldHaveSize 3
        sorted[0].title shouldBe "Alpha Movie"
        sorted[1].title shouldBe "Bravo Movie"
        sorted[2].title shouldBe "Charlie Movie"
    }

    test("find by type sorted by title with pagination") {
        val movies = listOf(
            createTestMovie(title = "Alpha"),
            createTestMovie(title = "Bravo"),
            createTestMovie(title = "Charlie"),
            createTestMovie(title = "Delta"),
            createTestMovie(title = "Echo"),
        )
        dao.insertMetadata(movies)

        val page1 = dao.findByTypeSortedByTitle(MediaType.MOVIE, limit = 2, offset = 0)
        page1 shouldHaveSize 2
        page1[0].title shouldBe "Alpha"
        page1[1].title shouldBe "Bravo"

        val page2 = dao.findByTypeSortedByTitle(MediaType.MOVIE, limit = 2, offset = 2)
        page2 shouldHaveSize 2
        page2[0].title shouldBe "Charlie"
        page2[1].title shouldBe "Delta"
    }

    test("find all by tmdb ids and type") {
        val movie1 = createTestMovie(title = "Movie 1", tmdbId = 1001)
        val movie2 = createTestMovie(title = "Movie 2", tmdbId = 1002)
        val movie3 = createTestMovie(title = "Movie 3", tmdbId = 1003)

        dao.insertMetadata(listOf(movie1, movie2, movie3))

        val found = dao.findAllByTmdbIdsAndType(listOf(1001, 1003), MediaType.MOVIE)
        found shouldHaveSize 2
        found.map { it.title } shouldContainExactlyInAnyOrder listOf("Movie 1", "Movie 3")
    }

    test("find all by ids and type") {
        val movie1 = createTestMovie(title = "Movie 1")
        val movie2 = createTestMovie(title = "Movie 2")
        val movie3 = createTestMovie(title = "Movie 3")

        dao.insertMetadata(listOf(movie1, movie2, movie3))

        val found = dao.findAllByIdsAndType(listOf(movie1.id, movie3.id), MediaType.MOVIE)
        found shouldHaveSize 2
        found.map { it.title } shouldContainExactlyInAnyOrder listOf("Movie 1", "Movie 3")
    }

    test("find all by root id and parent index and type") {
        val show = createTestTvShow(name = "Index Test Show")
        val season1 = createTestSeason(showId = show.id, seasonNumber = 1)
        val season2 = createTestSeason(showId = show.id, seasonNumber = 2)
        val ep1s1 = createTestEpisode(show.id, season1.id, 1, 1)
        val ep2s1 = createTestEpisode(show.id, season1.id, 1, 2)
        val ep1s2 = createTestEpisode(show.id, season2.id, 2, 1)

        dao.insertMetadata(listOf(show, season1, season2, ep1s1, ep2s1, ep1s2))

        val season1Episodes = dao.findAllByRootIdAndParentIndexAndType(
            show.id,
            parentIndex = 1,
            MediaType.TV_EPISODE
        )
        season1Episodes shouldHaveSize 2

        val season2Episodes = dao.findAllByRootIdAndParentIndexAndType(
            show.id,
            parentIndex = 2,
            MediaType.TV_EPISODE
        )
        season2Episodes shouldHaveSize 1
    }

    test("count seasons for tv show") {
        val show = createTestTvShow(name = "Season Count Show")
        val season1 = createTestSeason(showId = show.id, seasonNumber = 1)
        val season2 = createTestSeason(showId = show.id, seasonNumber = 2)
        val season3 = createTestSeason(showId = show.id, seasonNumber = 3)

        dao.insertMetadata(listOf(show, season1, season2, season3))

        dao.countSeasonsForTvShow(show.id) shouldBeEqual 3
    }

    test("batch insert metadata") {
        val movies = (1..10).map { i -> createTestMovie(title = "Batch Movie $i") }

        dao.insertMetadata(movies)

        dao.countByType(MediaType.MOVIE) shouldBeEqual 10
    }

    test("count total metadata") {
        val movie = createTestMovie(title = "Count Movie")
        val show = createTestTvShow(name = "Count Show")
        val season = createTestSeason(showId = show.id, seasonNumber = 1)

        dao.insertMetadata(listOf(movie, show, season))

        dao.count() shouldBeEqual 3
    }

    test("find nonexistent metadata returns null") {
        dao.find("nonexistent-id").shouldBeNull()
        dao.findByIdAndType("nonexistent-id", MediaType.MOVIE).shouldBeNull()
        dao.findByTmdbIdAndType(999999, MediaType.MOVIE).shouldBeNull()
    }

    test("update metadata changes fields and timestamp") {
        val movie = createTestMovie(title = "Original Title", tmdbId = 11111)
        dao.insertMetadata(movie)

        val original = dao.find(movie.id).shouldNotBeNull()
        val originalUpdatedAt = original.updatedAt

        // Update the movie
        val updated = original.copy(
            title = "Updated Title",
            overview = "New overview",
            tmdbRating = 85,
        )
        val rowsUpdated = dao.updateMetadata(updated)
        rowsUpdated shouldBe 1

        // Verify changes
        val fetched = dao.find(movie.id).shouldNotBeNull()
        fetched.title shouldBe "Updated Title"
        fetched.overview shouldBe "New overview"
        fetched.tmdbRating shouldBe 85
        fetched.updatedAt shouldNotBeEqual originalUpdatedAt
    }

    test("update nonexistent metadata returns 0") {
        val movie = createTestMovie(title = "Nonexistent")
        val rowsUpdated = dao.updateMetadata(movie)
        rowsUpdated shouldBe 0
    }

    test("upsert metadata inserts new record") {
        val movie = createTestMovie(title = "Upsert New Movie", tmdbId = 22222)

        val id = dao.upsertMetadata(movie)

        id shouldBe movie.id
        val fetched = dao.find(movie.id).shouldNotBeNull()
        fetched.title shouldBe "Upsert New Movie"
        fetched.tmdbId shouldBe 22222
    }

    test("upsert metadata updates existing record") {
        val movie = createTestMovie(title = "Original Upsert Title", tmdbId = 33333)
        dao.insertMetadata(movie)

        val original = dao.find(movie.id).shouldNotBeNull()
        val originalCreatedAt = original.createdAt

        // Upsert with updated fields
        val updated = movie.copy(
            title = "Updated Upsert Title",
            overview = "Updated overview",
            tmdbRating = 90,
        )
        val id = dao.upsertMetadata(updated)

        id shouldBe movie.id
        val fetched = dao.find(movie.id).shouldNotBeNull()
        fetched.title shouldBe "Updated Upsert Title"
        fetched.overview shouldBe "Updated overview"
        fetched.tmdbRating shouldBe 90
        // Created at should NOT change on upsert
        fetched.createdAt shouldBe originalCreatedAt
    }

    test("batch update metadata updates multiple records") {
        val movie1 = createTestMovie(title = "Batch Update 1")
        val movie2 = createTestMovie(title = "Batch Update 2")
        val movie3 = createTestMovie(title = "Batch Update 3")
        dao.insertMetadata(listOf(movie1, movie2, movie3))

        val updates = listOf(
            dao.find(movie1.id)!!.copy(title = "Updated Batch 1"),
            dao.find(movie2.id)!!.copy(title = "Updated Batch 2"),
        )
        val rowsUpdated = dao.updateMetadataBatch(updates)

        rowsUpdated shouldBe 2

        dao.find(movie1.id)!!.title shouldBe "Updated Batch 1"
        dao.find(movie2.id)!!.title shouldBe "Updated Batch 2"
        dao.find(movie3.id)!!.title shouldBe "Batch Update 3" // Unchanged
    }

    test("batch update empty list returns 0") {
        val rowsUpdated = dao.updateMetadataBatch(emptyList())
        rowsUpdated shouldBe 0
    }
})
