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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class MetadataDaoTest :
    FunSpec({

        val db: DSLContext by bindTestDatabase()
        val dao by bindForTest({ MetadataDao(db) })

        fun createMovieMetadata(
            id: String = ObjectId.next(),
            title: String = "Test Movie",
            tmdbId: Int = 100,
        ): Metadata {
            val now = Clock.System.now()
            return Metadata(
                id = MetadataId(id),
                title = title,
                overview = "A test movie overview",
                tmdbId = tmdbId,
                imdbId = "tt$tmdbId",
                runtime = 120.minutes,
                createdAt = now,
                updatedAt = now,
                mediaKind = MediaKind.MOVIE,
                mediaType = MediaType.MOVIE,
            )
        }

        fun createTvShowMetadata(
            id: String = ObjectId.next(),
            title: String = "Test Show",
            tmdbId: Int = 200,
        ): Metadata {
            val now = Clock.System.now()
            return Metadata(
                id = MetadataId(id),
                title = title,
                overview = "A test show",
                tmdbId = tmdbId,
                createdAt = now,
                updatedAt = now,
                mediaKind = MediaKind.TV,
                mediaType = MediaType.TV_SHOW,
            )
        }

        fun createSeasonMetadata(
            show: Metadata,
            seasonNumber: Int = 1,
            id: String = ObjectId.next(),
        ): Metadata {
            val now = Clock.System.now()
            return Metadata(
                id = MetadataId(id),
                rootId = show.id,
                parentId = show.id,
                title = "Season $seasonNumber",
                index = seasonNumber,
                createdAt = now,
                updatedAt = now,
                mediaKind = MediaKind.TV,
                mediaType = MediaType.TV_SEASON,
            )
        }

        fun createEpisodeMetadata(
            show: Metadata,
            season: Metadata,
            episodeNumber: Int = 1,
            id: String = ObjectId.next(),
        ): Metadata {
            val now = Clock.System.now()
            return Metadata(
                id = MetadataId(id),
                rootId = show.id,
                parentId = season.id,
                parentIndex = season.index,
                title = "Episode $episodeNumber",
                index = episodeNumber,
                createdAt = now,
                updatedAt = now,
                mediaKind = MediaKind.TV,
                mediaType = MediaType.TV_EPISODE,
            )
        }

        test("insertMetadata and find by id") {
            val metadata = createMovieMetadata()
            dao.insertMetadata(metadata)

            val found = dao.find(metadata.id).shouldNotBeNull()
            found.id shouldBe metadata.id
            found.title shouldBe metadata.title
            found.mediaType shouldBe MediaType.MOVIE
        }

        test("find returns null for nonexistent id") {
            dao.find(MetadataId("nonexistent")).shouldBeNull()
        }

        test("findByIdAndType - correct type returns record") {
            val metadata = createMovieMetadata()
            dao.insertMetadata(metadata)

            dao.findByIdAndType(metadata.id, MediaType.MOVIE).shouldNotBeNull()
        }

        test("findByIdAndType - wrong type returns null") {
            val metadata = createMovieMetadata()
            dao.insertMetadata(metadata)

            dao.findByIdAndType(metadata.id, MediaType.TV_SHOW).shouldBeNull()
        }

        test("findAllByIdsAndType") {
            val movie1 = createMovieMetadata(title = "Movie 1", tmdbId = 101)
            val movie2 = createMovieMetadata(title = "Movie 2", tmdbId = 102)
            val show = createTvShowMetadata()
            dao.insertMetadata(movie1)
            dao.insertMetadata(movie2)
            dao.insertMetadata(show)

            val movies = dao.findAllByIdsAndType(
                listOf(movie1.id, movie2.id, show.id),
                MediaType.MOVIE,
            )
            movies.shouldHaveSize(2)
            movies.map { it.id }.shouldContainExactlyInAnyOrder(movie1.id, movie2.id)
        }

        test("findByTmdbIdAndType") {
            val metadata = createMovieMetadata(tmdbId = 999)
            dao.insertMetadata(metadata)

            dao.findByTmdbIdAndType(999, MediaType.MOVIE).shouldNotBeNull().id shouldBe metadata.id
            dao.findByTmdbIdAndType(999, MediaType.TV_SHOW).shouldBeNull()
            dao.findByTmdbIdAndType(888, MediaType.MOVIE).shouldBeNull()
        }

        test("findAllByTmdbIdsAndType") {
            val movie1 = createMovieMetadata(title = "M1", tmdbId = 301)
            val movie2 = createMovieMetadata(title = "M2", tmdbId = 302)
            dao.insertMetadata(movie1)
            dao.insertMetadata(movie2)

            val results = dao.findAllByTmdbIdsAndType(listOf(301, 302, 999), MediaType.MOVIE)
            results.shouldHaveSize(2)
        }

        test("findType") {
            val movie = createMovieMetadata()
            dao.insertMetadata(movie)

            dao.findType(movie.id) shouldBe MediaType.MOVIE
            dao.findType(MetadataId("nonexistent")).shouldBeNull()
        }

        test("findRootIdOrSelf - returns self when no root") {
            val movie = createMovieMetadata()
            dao.insertMetadata(movie)

            dao.findRootIdOrSelf(movie.id) shouldBe movie.id
        }

        test("findRootIdOrSelf - returns rootId when has root") {
            val show = createTvShowMetadata()
            val season = createSeasonMetadata(show)
            dao.insertMetadata(show)
            dao.insertMetadata(season)

            dao.findRootIdOrSelf(season.id) shouldBe show.id
        }

        test("findRootIdOrSelf - nonexistent returns null") {
            dao.findRootIdOrSelf(MetadataId("nonexistent")).shouldBeNull()
        }

        test("findByType with limit") {
            val movies = (1..5).map { i ->
                createMovieMetadata(title = "Movie $i", tmdbId = 400 + i)
            }
            movies.forEach { dao.insertMetadata(it) }

            val results = dao.findByType(MediaType.MOVIE, limit = 3)
            results.shouldHaveSize(3)
        }

        test("findAllByTypeSortedByTitle") {
            val movieC = createMovieMetadata(title = "Charlie", tmdbId = 501)
            val movieA = createMovieMetadata(title = "Alpha", tmdbId = 502)
            val movieB = createMovieMetadata(title = "Bravo", tmdbId = 503)
            dao.insertMetadata(movieC)
            dao.insertMetadata(movieA)
            dao.insertMetadata(movieB)

            val results = dao.findAllByTypeSortedByTitle(MediaType.MOVIE)
            results.shouldHaveSize(3)
            results.map { it.title }.shouldContainExactly("Alpha", "Bravo", "Charlie")
        }

        test("findByTypeSortedByTitle with limit and offset") {
            val movies = ('A'..'E').mapIndexed { i, c ->
                createMovieMetadata(title = "$c Movie", tmdbId = 600 + i)
            }
            movies.forEach { dao.insertMetadata(it) }

            val page = dao.findByTypeSortedByTitle(MediaType.MOVIE, limit = 2, offset = 1)
            page.shouldHaveSize(2)
            page.map { it.title }.shouldContainExactly("B Movie", "C Movie")
        }

        test("findAllByParentIdAndType") {
            val show = createTvShowMetadata()
            val season1 = createSeasonMetadata(show, seasonNumber = 1)
            val season2 = createSeasonMetadata(show, seasonNumber = 2)
            dao.insertMetadata(show)
            dao.insertMetadata(season1)
            dao.insertMetadata(season2)

            val seasons = dao.findAllByParentIdAndType(show.id, MediaType.TV_SEASON)
            seasons.shouldHaveSize(2)
            seasons.map { it.id }.shouldContainExactlyInAnyOrder(season1.id, season2.id)
        }

        test("findAllByRootIdAndType") {
            val show = createTvShowMetadata()
            val season = createSeasonMetadata(show)
            val ep1 = createEpisodeMetadata(show, season, episodeNumber = 1)
            val ep2 = createEpisodeMetadata(show, season, episodeNumber = 2)
            dao.insertMetadata(show)
            dao.insertMetadata(season)
            dao.insertMetadata(ep1)
            dao.insertMetadata(ep2)

            val episodes = dao.findAllByRootIdAndType(show.id, MediaType.TV_EPISODE)
            episodes.shouldHaveSize(2)
        }

        test("findAllByRootIdAndParentIndexAndType") {
            val show = createTvShowMetadata()
            val season1 = createSeasonMetadata(show, seasonNumber = 1)
            val season2 = createSeasonMetadata(show, seasonNumber = 2)
            val ep1s1 = createEpisodeMetadata(show, season1, episodeNumber = 1)
            val ep1s2 = createEpisodeMetadata(show, season2, episodeNumber = 1)
            dao.insertMetadata(show)
            dao.insertMetadata(season1)
            dao.insertMetadata(season2)
            dao.insertMetadata(ep1s1)
            dao.insertMetadata(ep1s2)

            val s1Episodes = dao.findAllByRootIdAndParentIndexAndType(show.id, 1, MediaType.TV_EPISODE)
            s1Episodes.shouldHaveSize(1)
            s1Episodes.first().id shouldBe ep1s1.id

            val s2Episodes = dao.findAllByRootIdAndParentIndexAndType(show.id, 2, MediaType.TV_EPISODE)
            s2Episodes.shouldHaveSize(1)
            s2Episodes.first().id shouldBe ep1s2.id
        }

        test("count and countByType") {
            dao.count() shouldBeEqual 0L

            val movie = createMovieMetadata()
            val show = createTvShowMetadata()
            dao.insertMetadata(movie)
            dao.insertMetadata(show)

            dao.count() shouldBeEqual 2L
            dao.countByType(MediaType.MOVIE) shouldBeEqual 1L
            dao.countByType(MediaType.TV_SHOW) shouldBeEqual 1L
            dao.countByType(MediaType.TV_EPISODE) shouldBeEqual 0L
        }

        test("countSeasonsForTvShow") {
            val show = createTvShowMetadata()
            dao.insertMetadata(show)

            dao.countSeasonsForTvShow(show.id) shouldBeEqual 0

            // Season 0 (specials) should not be counted (index > 0 filter)
            val specials = createSeasonMetadata(show, seasonNumber = 0)
            val season1 = createSeasonMetadata(show, seasonNumber = 1)
            val season2 = createSeasonMetadata(show, seasonNumber = 2)
            dao.insertMetadata(specials)
            dao.insertMetadata(season1)
            dao.insertMetadata(season2)

            dao.countSeasonsForTvShow(show.id) shouldBeEqual 2
        }

        test("insertMetadata batch") {
            val items = (1..3).map { i ->
                createMovieMetadata(title = "Batch $i", tmdbId = 700 + i)
            }
            dao.insertMetadata(items)

            dao.count() shouldBeEqual 3L
        }

        test("deleteById") {
            val movie = createMovieMetadata()
            dao.insertMetadata(movie)
            dao.find(movie.id).shouldNotBeNull()

            dao.deleteById(movie.id)
            dao.find(movie.id).shouldBeNull()
        }

        test("deleteByRootId") {
            val show = createTvShowMetadata()
            val season = createSeasonMetadata(show)
            val episode = createEpisodeMetadata(show, season)
            dao.insertMetadata(show)
            dao.insertMetadata(season)
            dao.insertMetadata(episode)

            dao.count() shouldBeEqual 3L

            dao.deleteByRootId(show.id)

            // Should delete season and episode (they have rootId = show.id)
            // but NOT the show itself (it has no rootId)
            dao.find(show.id).shouldNotBeNull()
            dao.find(season.id).shouldBeNull()
            dao.find(episode.id).shouldBeNull()
        }

        test("findAllByParentIdAndType returns empty for no children") {
            val show = createTvShowMetadata()
            dao.insertMetadata(show)

            dao.findAllByParentIdAndType(show.id, MediaType.TV_SEASON).shouldBeEmpty()
        }
    })
