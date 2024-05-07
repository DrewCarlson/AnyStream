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

import anystream.db.tables.records.LibraryRecord
import anystream.db.tables.references.DIRECTORY
import anystream.db.tables.references.LIBRARY
import anystream.db.util.fetchIntoType
import anystream.db.util.fetchOptionalIntoType
import anystream.db.util.intoType
import anystream.models.Directory
import anystream.models.Library
import anystream.models.MediaKind
import anystream.util.ObjectId
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.impl.DSL.select

class LibraryDao(
    private val db: DSLContext
) {

    fun all(): List<Library> {
        return db.selectFrom(LIBRARY).fetchIntoType()
    }

    suspend fun createDefaultLibraries(): Unit = withContext(IO) {
        if (db.fetchCount(LIBRARY) == 0) {
            db.batchInsert(
                LibraryRecord(ObjectId.get().toString(), MediaKind.MOVIE, "Movies"),
                LibraryRecord(ObjectId.get().toString(), MediaKind.TV, "TV"),
                LibraryRecord(ObjectId.get().toString(), MediaKind.MUSIC, "Music"),
            ).execute()
        }
    }

    suspend fun libraryAndRoots(): Map<Library, Directory?> {
        return withContext(IO) {
            db.select()
                .from(LIBRARY)
                .join(DIRECTORY)
                .on(
                    DIRECTORY.LIBRARY_ID.eq(LIBRARY.ID)
                        .and(DIRECTORY.PARENT_ID.isNull)
                )
                .fetchMap(Library::class.java, Directory::class.java)
        }
    }

    fun insert(kind: MediaKind): Library {
        return db.newRecord(LIBRARY).apply {
            id = ObjectId.get().toString()
            mediaKind = kind
            name = kind.name.lowercase().replaceFirstChar(Char::uppercaseChar)
            store()
        }.intoType()
    }

    fun findByPath(path: String): Library? {
        return db.selectFrom(LIBRARY)
            .where(
                LIBRARY.ID.eq(
                    select(DIRECTORY.LIBRARY_ID)
                        .from(DIRECTORY)
                        .where(DIRECTORY.FILE_PATH.eq(path))
                )
            )
            .fetchOptionalIntoType()
    }

    fun findRootPaths(): List<String> {
        return db.select(DIRECTORY.FILE_PATH)
            .from(DIRECTORY)
            .where(DIRECTORY.PARENT_ID.isNotNull)
            .and(DIRECTORY.LIBRARY_ID.`in`(select(LIBRARY.ID).from(LIBRARY)))
            .fetchIntoType()
    }

    fun insertDirectory(parentId: String?, libraryId: String?, path: String): Directory {
        return db.newRecord(DIRECTORY)
            .apply {
                id = ObjectId.get().toString()
                this.parentId = parentId
                this.libraryId = libraryId
                this.filePath = path
                store()
            }
            .intoType()
    }

    fun findByExactPath(path: String): Directory? {
        return db.fetchOne(DIRECTORY, DIRECTORY.FILE_PATH.eq(path))
            ?.intoType()
    }

    fun findLibraryRoot(libraryId: String): Directory? {
        return db.fetchOne(
            DIRECTORY,
            DIRECTORY.LIBRARY_ID.eq(libraryId),
            DIRECTORY.PARENT_ID.isNull
        )?.intoType()
    }

    fun findLibraryRoots(): List<Directory> {
        return db.selectFrom(DIRECTORY)
            .where(DIRECTORY.PARENT_ID.isNull)
            .fetchIntoType()
    }

    fun libraryExists(id: String): Boolean {
        return db.select(LIBRARY.ID)
            .from(LIBRARY)
            .where(LIBRARY.ID.eq(id))
            .execute() == 1
    }
}