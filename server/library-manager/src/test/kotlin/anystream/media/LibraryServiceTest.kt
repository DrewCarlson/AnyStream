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
package anystream.media

import anystream.db.LibraryDao
import anystream.db.MediaLinkDao
import anystream.db.bindForTest
import anystream.db.bindTestDatabase
import anystream.media.analyzer.MediaFileAnalyzer
import anystream.models.MediaKind
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext

class LibraryServiceTest : FunSpec({
    val db: DSLContext by bindTestDatabase()
    val libraryDao by bindForTest({ LibraryDao(db) })
    val libraryService by bindForTest({
        val mediaLinkDao = MediaLinkDao(db)
        LibraryService(
            mediaFileAnalyzer = MediaFileAnalyzer({ error("not implemented!") }, mediaLinkDao),
            processors = emptyList(),
            mediaLinkDao = mediaLinkDao,
            libraryDao = libraryDao,
        )
    })

    test("get empty library list") {
        libraryService.getLibraryFolders().shouldBeEmpty()
    }

    test("get library list without directory") {
        libraryDao.insertLibrary(MediaKind.MOVIE)

        libraryService.getLibraryFolders().shouldBeEmpty()
    }

    test("get library") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "test")

        val libFolders = libraryService.getLibraryFolders()
            .shouldHaveSize(1)

        directory.parentId.shouldBeNull()
        libFolders.first().mediaKind shouldBe library.mediaKind
    }
})