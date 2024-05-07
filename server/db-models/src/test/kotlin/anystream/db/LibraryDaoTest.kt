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
package anystream.db

import anystream.models.MediaKind
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import kotlin.test.*

class LibraryDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    lateinit var dao: LibraryDao

    beforeTest {
        dao = LibraryDao(db)
    }

    test("get all") {
        val newLibrary = dao.insert(MediaKind.MOVIE)
        val libraries = dao.all()
        assertEquals(1, libraries.size)
        assertEquals(newLibrary, libraries.first())
    }

    test("get all when empty") {
        assertTrue(dao.all().isEmpty())
    }

    MediaKind.entries.forEach { libraryKind ->
        test("insert $libraryKind") {
            val library = dao.insert(libraryKind)

            assertTrue(ObjectId.isValid(library.id))
            assertEquals(libraryKind, library.mediaKind)
        }
    }

    test("insert all") {
        MediaKind.entries.forEach { libraryKind ->
            val library = dao.insert(libraryKind)

            assertTrue(ObjectId.isValid(library.id))
            assertEquals(libraryKind, library.mediaKind)
        }
    }

    test("insert multiple of same MediaKind") {
        val library1 = dao.insert(MediaKind.MOVIE)
        val library2 = dao.insert(MediaKind.MOVIE)

        library1.id should ObjectId::isValid
        library1.mediaKind shouldBe MediaKind.MOVIE

        library2.id should ObjectId::isValid
        library2.mediaKind shouldBe MediaKind.MOVIE

        library1.id shouldNotBeEqual library2.id
    }
})