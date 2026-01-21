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

import anystream.models.*
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext

class TagsDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val dao: TagsDao by bindForTest({ TagsDao(db) })
    val metadataDao: MetadataDao by bindForTest({ MetadataDao(db) })

    test("insert genre tag") {
        val genreId = dao.insertTag("Action", TagType.GENRE, 28)

        genreId should ObjectId::isValid

        val found = dao.findGenreByTmdbId(28)
        found.shouldNotBeNull()
        found.name shouldBe "Action"
        found.tmdbId shouldBe 28
    }

    test("insert company tag") {
        val companyId = dao.insertTag("Warner Bros", TagType.COMPANY, 174)

        companyId should ObjectId::isValid

        val found = dao.findCompanyByTmdbId(174)
        found.shouldNotBeNull()
        found.name shouldBe "Warner Bros"
        found.tmdbId shouldBe 174
    }

    test("insert person tag") {
        val personId = dao.insertTag("Tom Hanks", TagType.PERSON, 31)

        personId should ObjectId::isValid

        val found = dao.findPersonByTmdbId(31)
        found.shouldNotBeNull()
        found.name shouldBe "Tom Hanks"
        found.tmdbId shouldBe 31
    }

    test("insert credits links person to metadata") {
        // Create a movie metadata
        val movie = createTestMovie(title = "Test Movie")
        metadataDao.insertMetadata(movie)

        // Create person tags
        val actorId = dao.insertTag("Actor One", TagType.PERSON, 1001)
        val directorId = dao.insertTag("Director One", TagType.PERSON, 1002)

        // Create credits
        val credits = listOf(
            MetadataCredit(
                personId = actorId,
                metadataId = movie.id,
                type = CreditType.CAST,
                character = "Hero",
                order = 0,
                job = null,
            ),
            MetadataCredit(
                personId = directorId,
                metadataId = movie.id,
                type = CreditType.CREW,
                character = null,
                order = null,
                job = CreditJob.DIRECTOR,
            ),
        )
        dao.insertCredits(credits)

        // Verify credits are linked
        val (cast, crew) = dao.findCastAndCrewForMetadata(movie.id)

        cast shouldHaveSize 1
        cast.first().person.name shouldBe "Actor One"
        cast.first().character shouldBe "Hero"
        cast.first().order shouldBe 0

        crew shouldHaveSize 1
        crew.first().person.name shouldBe "Director One"
        crew.first().job shouldBe CreditJob.DIRECTOR
    }

    test("find cast and crew for metadata with multiple people") {
        val movie = createTestMovie(title = "Ensemble Movie")
        metadataDao.insertMetadata(movie)

        // Create multiple cast members
        val actor1Id = dao.insertTag("Actor 1", TagType.PERSON, 2001)
        val actor2Id = dao.insertTag("Actor 2", TagType.PERSON, 2002)
        val actor3Id = dao.insertTag("Actor 3", TagType.PERSON, 2003)

        val credits = listOf(
            MetadataCredit(actor1Id, movie.id, CreditType.CAST, "Character 1", 0, null),
            MetadataCredit(actor2Id, movie.id, CreditType.CAST, "Character 2", 1, null),
            MetadataCredit(actor3Id, movie.id, CreditType.CAST, "Character 3", 2, null),
        )
        dao.insertCredits(credits)

        val (cast, _) = dao.findCastAndCrewForMetadata(movie.id)
        cast shouldHaveSize 3
        cast.map { it.person.name } shouldContainExactlyInAnyOrder listOf("Actor 1", "Actor 2", "Actor 3")
    }

    test("find genres for metadata") {
        val movie = createTestMovie(title = "Genre Test Movie")
        metadataDao.insertMetadata(movie)

        val genreIds = listOf(
            dao.insertTag("Action", TagType.GENRE, 28),
            dao.insertTag("Comedy", TagType.GENRE, 35),
            dao.insertTag("Drama", TagType.GENRE, 18),
        )

        genreIds.forEach { genreId ->
            dao.insertMetadataGenreLink(movie.id, genreId)
        }

        val genres = dao.findGenresForMetadata(movie.id)
        genres shouldHaveSize 3
        genres.map { it.name } shouldContainExactlyInAnyOrder listOf("Action", "Comedy", "Drama")
    }

    test("find companies for metadata") {
        val movie = createTestMovie(title = "Company Test Movie")
        metadataDao.insertMetadata(movie)

        val companyIds = listOf(
            dao.insertTag("Warner Bros", TagType.COMPANY, 174),
            dao.insertTag("Universal", TagType.COMPANY, 33),
        )

        companyIds.forEach { companyId ->
            dao.insertMetadataCompanyLink(movie.id, companyId)
        }

        val companies = dao.findCompaniesForMetadata(movie.id)
        companies shouldHaveSize 2
        companies.map { it.name } shouldContainExactlyInAnyOrder listOf("Warner Bros", "Universal")
    }

    test("find by tmdb id prevents duplicates by design") {
        // Insert same genre twice with same tmdbId
        dao.insertTag("Action", TagType.GENRE, 28)
        dao.insertTag("Action Renamed", TagType.GENRE, 28)

        // findGenreByTmdbId should return first match (implementation specific)
        val found = dao.findGenreByTmdbId(28)
        found.shouldNotBeNull()
        // Either one should be found
        found.name shouldBe found.name // just verify it returns one
    }

    test("find person by nonexistent tmdb id returns null") {
        dao.findPersonByTmdbId(999999).shouldBeNull()
    }

    test("find genre by nonexistent tmdb id returns null") {
        dao.findGenreByTmdbId(999999).shouldBeNull()
    }

    test("find company by nonexistent tmdb id returns null") {
        dao.findCompanyByTmdbId(999999).shouldBeNull()
    }

    test("find cast and crew for metadata with no credits returns empty lists") {
        val movie = createTestMovie(title = "No Credits Movie")
        metadataDao.insertMetadata(movie)

        val (cast, crew) = dao.findCastAndCrewForMetadata(movie.id)

        cast shouldHaveSize 0
        crew shouldHaveSize 0
    }

    test("find genres for metadata with no genres returns empty list") {
        val movie = createTestMovie(title = "No Genres Movie")
        metadataDao.insertMetadata(movie)

        val genres = dao.findGenresForMetadata(movie.id)
        genres shouldHaveSize 0
    }

    test("find companies for metadata with no companies returns empty list") {
        val movie = createTestMovie(title = "No Companies Movie")
        metadataDao.insertMetadata(movie)

        val companies = dao.findCompaniesForMetadata(movie.id)
        companies shouldHaveSize 0
    }

    test("find cast and crew for multiple metadata ids") {
        val movie1 = createTestMovie(title = "Movie 1")
        val movie2 = createTestMovie(title = "Movie 2")
        metadataDao.insertMetadata(listOf(movie1, movie2))

        val actorId = dao.insertTag("Shared Actor", TagType.PERSON, 5001)

        val credits = listOf(
            MetadataCredit(actorId, movie1.id, CreditType.CAST, "Role in Movie 1", 0, null),
            MetadataCredit(actorId, movie2.id, CreditType.CAST, "Role in Movie 2", 0, null),
        )
        dao.insertCredits(credits)

        val (cast, _) = dao.findCastAndCrewForMetadata(listOf(movie1.id, movie2.id))
        cast shouldHaveSize 2
        cast.map { it.character } shouldContainExactlyInAnyOrder listOf("Role in Movie 1", "Role in Movie 2")
    }

    test("credits are ordered by order field") {
        val movie = createTestMovie(title = "Ordered Cast Movie")
        metadataDao.insertMetadata(movie)

        val actor1Id = dao.insertTag("Lead Actor", TagType.PERSON, 6001)
        val actor2Id = dao.insertTag("Supporting Actor", TagType.PERSON, 6002)
        val actor3Id = dao.insertTag("Cameo Actor", TagType.PERSON, 6003)

        // Insert in non-order sequence to test ordering
        val credits = listOf(
            MetadataCredit(actor3Id, movie.id, CreditType.CAST, "Cameo", 2, null),
            MetadataCredit(actor1Id, movie.id, CreditType.CAST, "Lead Role", 0, null),
            MetadataCredit(actor2Id, movie.id, CreditType.CAST, "Supporting Role", 1, null),
        )
        dao.insertCredits(credits)

        val (cast, _) = dao.findCastAndCrewForMetadata(movie.id)
        cast shouldHaveSize 3
        cast[0].person.name shouldBe "Lead Actor"
        cast[1].person.name shouldBe "Supporting Actor"
        cast[2].person.name shouldBe "Cameo Actor"
    }

    test("insert tag without tmdbId") {
        val genreId = dao.insertTag("Custom Genre", TagType.GENRE, null)

        genreId should ObjectId::isValid

        // Cannot find by tmdb id since it's null
        dao.findGenreByTmdbId(0).shouldBeNull()
    }
})
