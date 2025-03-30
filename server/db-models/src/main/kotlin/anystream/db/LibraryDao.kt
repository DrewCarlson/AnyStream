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

import anystream.db.tables.records.DirectoryRecord
import anystream.db.tables.records.LibraryRecord
import anystream.db.tables.references.DIRECTORY
import anystream.db.tables.references.LIBRARY
import anystream.db.util.*
import anystream.models.Directory
import anystream.models.Library
import anystream.models.MediaKind
import anystream.util.ObjectId
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.impl.DSL.select

/** The default MediaKind's to create Libraries for in a new AnyStream instance */
private val DEFAULT_LIBRARIES = listOf(MediaKind.MOVIE, MediaKind.TV, MediaKind.MUSIC)

class LibraryDao(
    private val db: DSLContext
) {

    /**
     * Insert a new Library for the MediaKind [kind].
     */
    suspend fun insertLibrary(kind: MediaKind, name: String = kind.libraryName): Library {
        val record = LibraryRecord(
            id = ObjectId.next(),
            mediaKind = kind,
            name = name,
        )
        return db.newRecordAsync(LIBRARY, record)
    }

    /**
     * Insert a new Directory for the [path] in the Library by [libraryId].
     */
    suspend fun insertDirectory(parentId: String?, libraryId: String, path: String): Directory {
        val record = DirectoryRecord(
            id = ObjectId.next(),
            parentId = parentId,
            libraryId = libraryId,
            filePath = path,
        )
        return db.newRecordAsync(DIRECTORY, record)
    }

    /**
     * Create the default libraries for a new AnyStream instance or do nothing
     * if any libraries already exist.
     *
     * @see DEFAULT_LIBRARIES for the actual default libraries to create.
     * @return true when the default libraries are created or false if none were created.
     */
    suspend fun insertDefaultLibraries(): Boolean {
        if (db.fetchCountAsync(LIBRARY) > 0) {
            return false
        }
        val inserts = DEFAULT_LIBRARIES.map { mediaKind ->
            LibraryRecord(
                id = ObjectId.next(),
                mediaKind = mediaKind,
                name = mediaKind.libraryName,
            )
        }
        return db.batchInsert(inserts)
            .executeAsync()
            .await()
            .isNotEmpty()
    }

    /**
     * Fetch all available [Library]s.
     */
    suspend fun all(): List<Library> {
        return db.selectFrom(LIBRARY).awaitInto()
    }

    /**
     * Fetch the [Library] by [libraryId] or null if not found.
     */
    suspend fun fetchLibrary(libraryId: String): Library? {
        return db.selectFrom(LIBRARY)
            .where(LIBRARY.ID.eq(libraryId))
            .awaitFirstOrNullInto()
    }

    suspend fun fetchLibraryForDirectory(directoryId: String): Library? {
        return db.select()
            .from(LIBRARY)
            .join(DIRECTORY)
            .on(LIBRARY.ID.eq(DIRECTORY.LIBRARY_ID))
            .where(DIRECTORY.ID.eq(directoryId))
            .awaitFirstOrNullInto()
    }

    suspend fun fetchChildDirectories(directoryId: String): List<Directory> {
        return db.select(DIRECTORY)
            .from(DIRECTORY)
            .where(DIRECTORY.PARENT_ID.eq(directoryId))
            .awaitInto()
    }

    /**
     * Fetch the [Directory] by the [path] or null if not found.
     */
    suspend fun fetchDirectoryByPath(path: String): Directory? {
        return db.selectFrom(DIRECTORY)
            .where(DIRECTORY.FILE_PATH.eq(path))
            .awaitFirstOrNullInto()
    }

    /**
     * Fetch all [Directory]s by the [libraryId].
     */
    suspend fun fetchDirectories(libraryId: String): List<Directory> {
        return db.selectFrom(DIRECTORY)
            .where(DIRECTORY.LIBRARY_ID.eq(libraryId))
            .awaitInto()
    }

    suspend fun fetchDirectory(directoryId: String): Directory? {
        return db.selectFrom(DIRECTORY)
            .where(DIRECTORY.ID.eq(directoryId))
            .awaitFirstOrNullInto()
    }

    suspend fun deleteDirectory(directoryId: String): Boolean {
        return db.deleteFrom(DIRECTORY)
            .where(DIRECTORY.ID.eq(directoryId))
            .awaitFirstOrNull() == 1
    }

    suspend fun deleteDirectoriesByParent(directoryId: String): List<String> {
        val ids: List<String> = db
            .select(DIRECTORY.ID)
            .from(DIRECTORY)
            .where(DIRECTORY.PARENT_ID.eq(directoryId))
            .awaitInto()

        // TODO: verify list of removed ids and filter the results
        val deleted = db.deleteFrom(DIRECTORY)
            .where(DIRECTORY.PARENT_ID.eq(directoryId))
            .awaitFirst() == ids.size

        return if (deleted) ids else emptyList()
    }

    suspend fun fetchLibraries(): List<Library> {
        return db.selectFrom(LIBRARY).awaitInto()
    }

    suspend fun fetchLibrariesAndRootDirectories(): Map<Library, Directory?> {
        return db.select()
            .from(LIBRARY)
            .join(DIRECTORY)
            .on(
                DIRECTORY.LIBRARY_ID.eq(LIBRARY.ID)
                    .and(DIRECTORY.PARENT_ID.isNull)
            ).awaitIntoMap()
    }

    suspend fun fetchLibraryByPath(path: String): Library? {
        return db.selectFrom(LIBRARY)
            .where(
                LIBRARY.ID.eq(
                    select(DIRECTORY.LIBRARY_ID)
                        .from(DIRECTORY)
                        .where(DIRECTORY.FILE_PATH.eq(path))
                )
            )
            .awaitFirstOrNullInto()
    }

    suspend fun fetchLibraryRootDirectories(libraryId: String? = null): List<Directory> {
        return db.selectFrom(DIRECTORY)
            .where(DIRECTORY.PARENT_ID.isNull)
            .run {
                if (libraryId == null) {
                    this
                } else {
                    and(DIRECTORY.LIBRARY_ID.eq(libraryId))
                }
            }
            .awaitInto()
    }
}