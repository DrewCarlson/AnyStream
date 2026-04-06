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

import anystream.data.MetadataDbQueries
import anystream.db.pojos.fromTvEpisode
import anystream.db.pojos.fromTvSeason
import anystream.db.pojos.toMetadataDb
import anystream.models.*
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import io.kotest.matchers.maps.shouldBeEmpty as shouldBeEmptyMap
import io.kotest.matchers.maps.shouldHaveSize as shouldHaveMapSize

class MetadataDbQueriesTest :
    FunSpec({

        val db: DSLContext by bindTestDatabase()
        val libraryDao by bindForTest({ LibraryDao(db) })
        val metadataDao by bindForTest({ MetadataDao(db) })
        val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
        val tagsDao by bindForTest({ TagsDao(db) })
        val playbackStatesDao by bindForTest({ PlaybackStatesDao(db) })
        val userDao by bindForTest({ UserDao(db) })
        val queries by bindForTest({
            MetadataDbQueries(db, metadataDao, tagsDao, mediaLinkDao, playbackStatesDao)
        })

        fun createMovie(
            title: String = "Test Movie",
            tmdbId: Int = 100,
        ): Movie {
            val now = Clock.System.now()
            return Movie(
                id = MetadataId(ObjectId.next()),
                title = title,
                overview = "Overview",
                tmdbId = tmdbId,
                imdbId = "tt$tmdbId",
                runtime = 120.minutes,
                releaseDate = now,
                createdAt = now,
                contentRating = "PG-13",
            )
        }

        fun createTvShow(
            name: String = "Test Show",
            tmdbId: Int = 200,
        ): TvShow {
            val now = Clock.System.now()
            return TvShow(
                id = MetadataId(ObjectId.next()),
                name = name,
                overview = "A show",
                tmdbId = tmdbId,
                firstAirDate = now,
                createdAt = now,
            )
        }

        suspend fun setupLibraryAndDirectory(): DirectoryId {
            libraryDao.insertDefaultLibraries()
            val library = libraryDao.all().first { it.mediaKind == MediaKind.MOVIE }
            return libraryDao.insertDirectory(null, library.id, "/movies").id
        }

        suspend fun setupTvLibraryAndDirectory(): DirectoryId {
            if (libraryDao.all().isEmpty()) libraryDao.insertDefaultLibraries()
            val library = libraryDao.all().first { it.mediaKind == MediaKind.TV }
            return libraryDao.insertDirectory(null, library.id, "/tv").id
        }

        fun createMediaLink(
            directoryId: DirectoryId,
            metadataId: MetadataId? = null,
            rootMetadataId: MetadataId? = null,
            filePath: String = "/movies/${ObjectId.next()}.mp4",
            mediaKind: MediaKind = MediaKind.MOVIE,
            descriptor: Descriptor = Descriptor.VIDEO,
        ): MediaLink {
            val now = Clock.System.now()
            return MediaLink(
                id = MediaLinkId(ObjectId.next()),
                directoryId = directoryId,
                filePath = filePath,
                descriptor = descriptor,
                mediaKind = mediaKind,
                type = MediaLinkType.LOCAL,
                createdAt = now,
                updatedAt = now,
                metadataId = metadataId,
                rootMetadataId = rootMetadataId,
            )
        }

        suspend fun createUser(name: String = "testuser"): User {
            val user = User(
                id = UserId(ObjectId.next()),
                displayName = name,
                passwordHash = "hash",
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                username = name,
                authSource = AuthSource.INTERNAL,
            )
            userDao.insertUser(user, emptySet()).shouldNotBeNull()
            return user
        }

        // -- insertMovie / findMovieById --

        test("insertMovie and findMovieById") {
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val response = queries.findMovieById(movie.id).shouldNotBeNull()
            response.movie.title shouldBe movie.title
            response.movie.id shouldBe movie.id
            response.mediaLinks.shouldBeEmpty()
            response.genres.shouldBeEmpty()
            response.companies.shouldBeEmpty()
        }

        test("insertMovie with genres and companies") {
            val movie = createMovie()
            val genres = listOf(
                Genre(id = TagId(""), name = "Action", tmdbId = 28),
                Genre(id = TagId(""), name = "Comedy", tmdbId = 35),
            )
            val companies = listOf(
                ProductionCompany(id = TagId(""), name = "StudioA", tmdbId = 1),
            )
            queries.insertMovie(movie, genres, companies)

            val response = queries.findMovieById(movie.id).shouldNotBeNull()
            response.genres.shouldHaveSize(2)
            response.genres.map { it.name }.toSet() shouldBe setOf("Action", "Comedy")
            response.companies.shouldHaveSize(1)
            response.companies.first().name shouldBe "StudioA"
        }

        test("findMovieById returns null for nonexistent") {
            queries.findMovieById(MetadataId("nonexistent")).shouldBeNull()
        }

        test("findMovieById with includeLinks") {
            val dirId = setupLibraryAndDirectory()
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val link = createMediaLink(dirId, metadataId = movie.id)
            mediaLinkDao.insertLink(link)

            val withLinks = queries.findMovieById(movie.id, includeLinks = true).shouldNotBeNull()
            withLinks.mediaLinks.shouldHaveSize(1)

            val withoutLinks = queries.findMovieById(movie.id, includeLinks = false).shouldNotBeNull()
            withoutLinks.mediaLinks.shouldBeEmpty()
        }

        test("findMovieById with playback state") {
            val dirId = setupLibraryAndDirectory()
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val link = createMediaLink(dirId, metadataId = movie.id)
            mediaLinkDao.insertLink(link)

            val user = createUser()
            val now = Clock.System.now()
            playbackStatesDao.insert(
                PlaybackState(
                    id = PlaybackStateId(ObjectId.next()),
                    mediaLinkId = link.id,
                    metadataId = movie.id,
                    userId = user.id,
                    position = 30.seconds,
                    runtime = 120.minutes,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val response = queries.findMovieById(movie.id, includePlaybackStateForUser = user.id).shouldNotBeNull()
            response.playbackState.shouldNotBeNull()
        }

        // -- findMovies --

        test("findMovies returns all movies sorted by title") {
            queries.insertMovie(createMovie(title = "Zebra", tmdbId = 10), emptyList(), emptyList())
            queries.insertMovie(createMovie(title = "Apple", tmdbId = 11), emptyList(), emptyList())

            val response = queries.findMovies()
            response.movies.shouldHaveSize(2)
            response.movies.first().title shouldBe "Apple"
            response.movies.last().title shouldBe "Zebra"
            response.total shouldBe 2
        }

        test("findMovies with limit and offset") {
            (1..5).forEach { i ->
                queries.insertMovie(createMovie(title = "Movie $i", tmdbId = 50 + i), emptyList(), emptyList())
            }

            val page = queries.findMovies(limit = 2, offset = 1)
            page.movies.shouldHaveSize(2)
            page.limit shouldBe 2
            page.offset shouldBe 1
            page.total shouldBe 5
        }

        // -- findMovieByTmdbId / findMoviesByTmdbId --

        test("findMovieByTmdbId") {
            val movie = createMovie(tmdbId = 777)
            queries.insertMovie(movie, emptyList(), emptyList())

            queries.findMovieByTmdbId(777).shouldNotBeNull().title shouldBe movie.title
            queries.findMovieByTmdbId(999).shouldBeNull()
        }

        test("findMoviesByTmdbId") {
            queries.insertMovie(createMovie(title = "M1", tmdbId = 801), emptyList(), emptyList())
            queries.insertMovie(createMovie(title = "M2", tmdbId = 802), emptyList(), emptyList())

            queries.findMoviesByTmdbId(listOf(801, 802, 999)).shouldHaveSize(2)
        }

        // -- insertTvShow / findShowById --

        test("insertTvShow and findShowById") {
            val show = createTvShow()
            val showMeta = show.toMetadataDb()
            val season = TvSeason(
                id = MetadataId(ObjectId.next()),
                name = "Season 1",
                overview = "",
                seasonNumber = 1,
                airDate = null,
                tmdbId = null,
            )
            val seasonMeta = season.fromTvSeason(showMeta)
            val episode = Episode(
                id = MetadataId(ObjectId.next()),
                showId = show.id,
                seasonId = season.id,
                name = "Pilot",
                tmdbId = 1001,
                overview = "First episode",
                airDate = null,
                number = 1,
                seasonNumber = 1,
                tmdbRating = null,
            )
            val episodeMeta = episode.fromTvEpisode(showMeta, seasonMeta)

            queries.insertTvShow(showMeta, listOf(seasonMeta), listOf(episodeMeta))

            val response = queries.findShowById(show.id).shouldNotBeNull()
            response.tvShow.name shouldBe show.name
            response.seasons.shouldHaveSize(1)
            response.seasons.first().seasonNumber shouldBe 1
        }

        test("findShowById returns null for nonexistent") {
            queries.findShowById(MetadataId("nonexistent")).shouldBeNull()
        }

        // -- findShows --

        test("findShows") {
            val show1 = createTvShow(name = "Beta Show", tmdbId = 901)
            val show2 = createTvShow(name = "Alpha Show", tmdbId = 902)
            queries.insertTvShow(show1.toMetadataDb(), emptyList(), emptyList())
            queries.insertTvShow(show2.toMetadataDb(), emptyList(), emptyList())

            val response = queries.findShows()
            response.tvShows.shouldHaveSize(2)
            response.tvShows.first().name shouldBe "Alpha Show"
        }

        // -- findTvShowByTmdbId --

        test("findTvShowByTmdbId") {
            val show = createTvShow(tmdbId = 555)
            queries.insertTvShow(show.toMetadataDb(), emptyList(), emptyList())

            queries.findTvShowByTmdbId(555).shouldNotBeNull().name shouldBe show.name
            queries.findTvShowByTmdbId(999).shouldBeNull()
        }

        test("findTvShowsByTmdbId with empty list returns empty") {
            queries.findTvShowsByTmdbId(emptyList()).shouldBeEmpty()
        }

        // -- findMediaById (polymorphic lookup) --

        test("findMediaById dispatches to movie") {
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val response = queries.findMediaById(movie.id, includeLinks = false)
            response.shouldNotBeNull()
        }

        test("findMediaById dispatches to TV show") {
            val show = createTvShow()
            queries.insertTvShow(show.toMetadataDb(), emptyList(), emptyList())

            val response = queries.findMediaById(show.id, includeLinks = false)
            response.shouldNotBeNull()
        }

        test("findMediaById throws for nonexistent id") {
            // The MediaLookupResponse overload calls findType which returns null,
            // then hits the else branch which calls error()
            io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
                queries.findMediaById(MetadataId("nonexistent"), includeLinks = false)
            }
        }

        // -- findMediaById (FindMediaResult overload) --

        test("findMediaById FindMediaResult - movie") {
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val result = queries.findMediaById(movie.id)
            result.hasResult().shouldBeTrue()
            result.movie.shouldNotBeNull().title shouldBe movie.title
            result.tvShow.shouldBeNull()
        }

        test("findMediaById FindMediaResult - tv show") {
            val show = createTvShow()
            queries.insertTvShow(show.toMetadataDb(), emptyList(), emptyList())

            val result = queries.findMediaById(show.id)
            result.hasResult().shouldBeTrue()
            result.tvShow.shouldNotBeNull().name shouldBe show.name
            result.movie.shouldBeNull()
        }

        test("findMediaById FindMediaResult - episode") {
            val show = createTvShow()
            val showMeta = show.toMetadataDb()
            val season = TvSeason(
                id = MetadataId(ObjectId.next()),
                name = "Season 1",
                overview = "",
                seasonNumber = 1,
                airDate = null,
                tmdbId = null,
            )
            val seasonMeta = season.fromTvSeason(showMeta)
            val episode = Episode(
                id = MetadataId(ObjectId.next()),
                showId = show.id,
                seasonId = season.id,
                name = "Pilot",
                tmdbId = 1001,
                overview = "",
                airDate = null,
                number = 1,
                seasonNumber = 1,
                tmdbRating = null,
            )
            val episodeMeta = episode.fromTvEpisode(showMeta, seasonMeta)
            queries.insertTvShow(showMeta, listOf(seasonMeta), listOf(episodeMeta))

            val result = queries.findMediaById(episode.id)
            result.hasResult().shouldBeTrue()
            result.episode.shouldNotBeNull().name shouldBe "Pilot"
        }

        test("findMediaById FindMediaResult - not found") {
            val result = queries.findMediaById(MetadataId("nonexistent"))
            result.hasResult() shouldBe false
        }

        // -- findRecentlyAddedMovies --

        test("findRecentlyAddedMovies") {
            val dirId = setupLibraryAndDirectory()
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val link = createMediaLink(dirId, metadataId = movie.id, descriptor = Descriptor.VIDEO)
            mediaLinkDao.insertLink(link)

            val recent = queries.findRecentlyAddedMovies(limit = 10)
            recent.shouldHaveMapSize(1)
            recent.keys.first().title shouldBe movie.title
        }

        test("findRecentlyAddedMovies empty when no movies") {
            queries.findRecentlyAddedMovies(limit = 10).shouldBeEmptyMap()
        }

        // -- findRecentlyAddedTv --

        test("findRecentlyAddedTv") {
            val show = createTvShow()
            queries.insertTvShow(show.toMetadataDb(), emptyList(), emptyList())

            val recent = queries.findRecentlyAddedTv(limit = 10)
            recent.shouldHaveSize(1)
            recent.first().name shouldBe show.name
        }

        // -- findEpisodesByShow --

        test("findEpisodesByShow") {
            val show = createTvShow()
            val showMeta = show.toMetadataDb()
            val season1 = TvSeason(MetadataId(ObjectId.next()), "S1", "", 1, null, null)
            val season2 = TvSeason(MetadataId(ObjectId.next()), "S2", "", 2, null, null)
            val s1Meta = season1.fromTvSeason(showMeta)
            val s2Meta = season2.fromTvSeason(showMeta)
            val ep1 = Episode(MetadataId(ObjectId.next()), show.id, season1.id, "Ep1", 1, "", null, 1, 1, null)
            val ep2 = Episode(MetadataId(ObjectId.next()), show.id, season2.id, "Ep2", 2, "", null, 1, 2, null)
            queries.insertTvShow(
                showMeta,
                listOf(s1Meta, s2Meta),
                listOf(ep1.fromTvEpisode(showMeta, s1Meta), ep2.fromTvEpisode(showMeta, s2Meta)),
            )

            queries.findEpisodesByShow(show.id).shouldHaveSize(2)
            queries.findEpisodesByShow(show.id, seasonNumber = 1).shouldHaveSize(1)
            queries.findEpisodesByShow(show.id, seasonNumber = 2).shouldHaveSize(1)
            queries.findEpisodesByShow(show.id, seasonNumber = 3).shouldBeEmpty()
        }

        // -- findSeasonById --

        test("findSeasonById") {
            val show = createTvShow()
            val showMeta = show.toMetadataDb()
            val season = TvSeason(MetadataId(ObjectId.next()), "Season 1", "", 1, null, null)
            val seasonMeta = season.fromTvSeason(showMeta)
            val ep = Episode(MetadataId(ObjectId.next()), show.id, season.id, "Pilot", 1, "", null, 1, 1, null)
            val epMeta = ep.fromTvEpisode(showMeta, seasonMeta)
            queries.insertTvShow(showMeta, listOf(seasonMeta), listOf(epMeta))

            val response = queries.findSeasonById(season.id).shouldNotBeNull()
            response.season.seasonNumber shouldBe 1
            response.show.name shouldBe show.name
            response.episodes.shouldHaveSize(1)
        }

        test("findSeasonById returns null for nonexistent") {
            queries.findSeasonById(MetadataId("nonexistent")).shouldBeNull()
        }

        // -- findEpisodeById --

        test("findEpisodeById") {
            val show = createTvShow()
            val showMeta = show.toMetadataDb()
            val season = TvSeason(MetadataId(ObjectId.next()), "Season 1", "", 1, null, null)
            val seasonMeta = season.fromTvSeason(showMeta)
            val ep = Episode(MetadataId(ObjectId.next()), show.id, season.id, "Pilot", 1, "", null, 1, 1, null)
            val epMeta = ep.fromTvEpisode(showMeta, seasonMeta)
            queries.insertTvShow(showMeta, listOf(seasonMeta), listOf(epMeta))

            val response = queries.findEpisodeById(ep.id).shouldNotBeNull()
            response.episode.name shouldBe "Pilot"
            response.show.name shouldBe show.name
        }

        test("findEpisodeById returns null for nonexistent") {
            queries.findEpisodeById(MetadataId("nonexistent")).shouldBeNull()
        }

        // -- findCurrentlyWatching --

        test("findCurrentlyWatching with movie") {
            val dirId = setupLibraryAndDirectory()
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val link = createMediaLink(dirId, metadataId = movie.id)
            mediaLinkDao.insertLink(link)

            val user = createUser()
            val now = Clock.System.now()
            playbackStatesDao.insert(
                PlaybackState(
                    id = PlaybackStateId(ObjectId.next()),
                    mediaLinkId = link.id,
                    metadataId = movie.id,
                    userId = user.id,
                    position = 30.seconds,
                    runtime = 120.minutes,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val result = queries.findCurrentlyWatching(user.id, limit = 10)
            result.playbackStates.shouldHaveSize(1)
            result.currentlyWatchingMovies.shouldHaveMapSize(1)
            result.currentlyWatchingTv.shouldBeEmptyMap()
        }

        test("findCurrentlyWatching empty for user with no playback") {
            val user = createUser("nowatch")
            val result = queries.findCurrentlyWatching(user.id, limit = 10)
            result.playbackStates.shouldBeEmpty()
            result.currentlyWatchingMovies.shouldBeEmptyMap()
            result.currentlyWatchingTv.shouldBeEmptyMap()
        }

        // -- insertCredits --

        test("insertCredits and retrieve via findMovieById") {
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val person = Person(id = TagId(""), name = "Actor", tmdbId = 5000)
            val credits = mapOf(
                person to listOf(
                    MetadataCredit(
                        personId = TagId(""),
                        metadataId = movie.id,
                        type = CreditType.CAST,
                        character = "Hero",
                        order = 0,
                    ),
                ),
            )
            queries.insertCredits(movie.id, credits)

            val response = queries.findMovieById(movie.id).shouldNotBeNull()
            response.cast.shouldHaveSize(1)
            response.cast.first().character shouldBe "Hero"
            response.cast
                .first()
                .person.name shouldBe "Actor"
        }

        // -- deleteMovie / deleteTvShow --

        test("deleteMovie") {
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            queries.deleteMovie(movie.id).shouldBeTrue()
            queries.findMovieById(movie.id).shouldBeNull()
        }

        test("deleteTvShow cascades to children and media links") {
            val dirId = setupTvLibraryAndDirectory()
            val show = createTvShow()
            val showMeta = show.toMetadataDb()
            val season = TvSeason(MetadataId(ObjectId.next()), "S1", "", 1, null, null)
            val seasonMeta = season.fromTvSeason(showMeta)
            val ep = Episode(MetadataId(ObjectId.next()), show.id, season.id, "Pilot", 1, "", null, 1, 1, null)
            val epMeta = ep.fromTvEpisode(showMeta, seasonMeta)
            queries.insertTvShow(showMeta, listOf(seasonMeta), listOf(epMeta))

            val link = createMediaLink(
                dirId,
                metadataId = ep.id,
                rootMetadataId = show.id,
                mediaKind = MediaKind.TV,
            )
            mediaLinkDao.insertLink(link)

            queries.deleteTvShow(show.id).shouldBeTrue()

            // Show, season, episode metadata should all be gone
            metadataDao.find(show.id).shouldBeNull()
            metadataDao.find(season.id).shouldBeNull()
            metadataDao.find(ep.id).shouldBeNull()

            // Media links with rootMetadataId = show.id should be gone
            mediaLinkDao.findById(link.id).shouldBeNull()
        }

        // -- findMediaRefByFilePath --

        test("findMediaRefByFilePath") {
            val dirId = setupLibraryAndDirectory()
            val link = createMediaLink(dirId, filePath = "/movies/findme.mp4")
            mediaLinkDao.insertLink(link)

            queries.findMediaRefByFilePath("/movies/findme.mp4").shouldNotBeNull().id shouldBe link.id
            queries.findMediaRefByFilePath("/movies/nope.mp4").shouldBeNull()
        }

        // -- deleteLinksByContentId / deleteLinksByRootContentId --

        test("deleteLinksByContentId") {
            val dirId = setupLibraryAndDirectory()
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val link = createMediaLink(dirId, metadataId = movie.id)
            mediaLinkDao.insertLink(link)

            queries.deleteLinksByContentId(movie.id)
            mediaLinkDao.findByMetadataId(movie.id).shouldBeEmpty()
        }

        test("deleteLinksByRootContentId") {
            val dirId = setupLibraryAndDirectory()
            val movie = createMovie()
            queries.insertMovie(movie, emptyList(), emptyList())

            val link = createMediaLink(dirId, rootMetadataId = movie.id, metadataId = movie.id)
            mediaLinkDao.insertLink(link)

            queries.deleteLinksByRootContentId(movie.id)
            mediaLinkDao.findByRootMetadataIds(listOf(movie.id)).shouldBeEmpty()
        }
    })
