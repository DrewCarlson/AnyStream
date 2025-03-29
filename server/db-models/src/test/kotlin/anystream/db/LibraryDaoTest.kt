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
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import org.jooq.DSLContext
import kotlin.test.*

class LibraryDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val dao: LibraryDao by bindForTest({ LibraryDao(db) })

    test("fetch all libraries") {
        val newLibrary = dao.insertLibrary(MediaKind.MOVIE)
        val libraries = dao.all()
        libraries.size shouldBeEqual 1
        newLibrary shouldBeEqual libraries.first()
    }

    test("fetch all libraries when table empty") {
        dao.all().isEmpty().shouldBeTrue()
    }

    MediaKind.entries.forEach { libraryKind ->
        test("insert library for MediaKind.$libraryKind") {
            val library = dao.insertLibrary(libraryKind)

            assertTrue(ObjectId.isValid(library.id))
            assertEquals(libraryKind, library.mediaKind)
        }
    }

    test("insert library for all MediaKinds") {
        MediaKind.entries.forEach { libraryKind ->
            val library = dao.insertLibrary(libraryKind)

            assertTrue(ObjectId.isValid(library.id))
            assertEquals(libraryKind, library.mediaKind)
        }
    }

    test("insert default libraries") {
        dao.insertDefaultLibraries()

        val libraries = dao.all()
        libraries.size shouldBeEqual 3

        libraries[0].id should ObjectId::isValid
        libraries[0].name shouldBeEqual "Movies"
        libraries[0].mediaKind shouldBeEqual MediaKind.MOVIE

        libraries[1].id should ObjectId::isValid
        libraries[1].name shouldBeEqual "TV"
        libraries[1].mediaKind shouldBeEqual MediaKind.TV

        libraries[2].id should ObjectId::isValid
        libraries[2].name shouldBeEqual "Music"
        libraries[2].mediaKind shouldBeEqual MediaKind.MUSIC
    }

    test("insert multiple of same MediaKind") {
        val library1 = dao.insertLibrary(MediaKind.MOVIE)
        val library2 = dao.insertLibrary(MediaKind.MOVIE)
        val library3 = dao.insertLibrary(MediaKind.MOVIE, "movies-3")

        library1.id should ObjectId::isValid
        library1.name shouldBeEqual "Movies"
        library1.mediaKind shouldBeEqual MediaKind.MOVIE

        library2.id should ObjectId::isValid
        library2.name shouldBeEqual "Movies"
        library2.mediaKind shouldBeEqual MediaKind.MOVIE

        library3.id should ObjectId::isValid
        library3.name shouldBeEqual "movies-3"
        library3.mediaKind shouldBeEqual MediaKind.MOVIE
    }

    test("insert directory") {
        val library = dao.insertLibrary(MediaKind.MOVIE)

        val directory = dao.insertDirectory(parentId = null, libraryId = library.id, "directory")

        directory.id should ObjectId::isValid
        directory.libraryId.shouldNotBeNull() shouldBeEqual library.id
        directory.parentId.shouldBeNull()
        directory.filePath shouldBeEqual "directory"
    }

    test("fetch all directories") {
        val library = dao.insertLibrary(MediaKind.MOVIE)

        val directory1 = dao.insertDirectory(parentId = null, libraryId = library.id, "directory")
        val directory2 = dao.insertDirectory(parentId = null, libraryId = library.id, "directory2")

        val directories = dao.fetchDirectories(library.id)
        directories.size shouldBeEqual 2

        directories.first() shouldBeEqual directory1
        directories.last() shouldBeEqual directory2
    }

    test("fetch all directories when table empty") {
        val library = dao.insertLibrary(MediaKind.MOVIE)

        dao.fetchDirectories(library.id).size shouldBeEqual 0
    }

    test("fetch directory by path") {
        val library = dao.insertLibrary(MediaKind.MOVIE)

        val directory1 = dao.insertDirectory(parentId = null, libraryId = library.id, "directory")
        val directory2 = dao.insertDirectory(parentId = null, libraryId = library.id, "directory2")

        directory1 shouldBeEqual dao.fetchDirectoryByPath("directory").shouldNotBeNull()
        directory2 shouldBeEqual dao.fetchDirectoryByPath("directory2").shouldNotBeNull()
    }


    test("fetch library by path") {
        val library1 = dao.insertLibrary(MediaKind.MOVIE)
        val library2 = dao.insertLibrary(MediaKind.TV)

        dao.insertDirectory(parentId = null, libraryId = library1.id, "directory")
        dao.insertDirectory(parentId = null, libraryId = library2.id, "directory2")

        library1 shouldBeEqual dao.fetchLibraryByPath("directory").shouldNotBeNull()
        library2 shouldBeEqual dao.fetchLibraryByPath("directory2").shouldNotBeNull()
    }
})