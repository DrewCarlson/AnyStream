/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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

import anystream.db.tables.records.MetadataRecord
import anystream.db.tables.references.METADATA
import anystream.db.util.*
import anystream.db.util.awaitFirstOrNullInto
import anystream.models.Metadata
import anystream.models.MediaType
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.impl.DSL

class MetadataDao(
    private val db: DSLContext,
) {

    suspend fun findAllByParentIdAndType(parentId: String, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.PARENT_ID.eq(parentId),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitInto()
    }

    suspend fun findAllByRootIdAndType(rootId: String, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ROOT_ID.eq(rootId),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitInto()
    }

    suspend fun findAllByRootIdAndParentIndexAndType(
        rootId: String,
        parentIndex: Int,
        type: MediaType
    ): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ROOT_ID.eq(rootId),
                METADATA.PARENT_INDEX.eq(parentIndex),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitInto()
    }

    suspend fun findByIdAndType(id: String, type: MediaType): Metadata? {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ID.eq(id),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitFirstOrNullInto()
    }

    suspend fun findAllByIdsAndType(ids: List<String>, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ID.`in`(ids),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitInto()
    }

    suspend fun find(metadataId: String): Metadata? {
        return db.selectFrom(METADATA)
            .where(METADATA.ID.eq(metadataId))
            .awaitFirstOrNullInto()
    }

    suspend fun findType(metadataId: String): MediaType? {
        return db.select(METADATA.MEDIA_TYPE)
            .from(METADATA)
            .where(METADATA.ID.eq(metadataId))
            .awaitFirstOrNullInto()
    }

    suspend fun findByType(type: MediaType, limit: Int): List<Metadata> {
        return db
            .select(METADATA)
            .from(METADATA)
            .where(METADATA.MEDIA_TYPE.eq(type))
            .orderBy(METADATA.CREATED_AT.desc())
            .limit(limit)
            .awaitInto()
    }

    suspend fun findAllByTypeSortedByTitle(type: MediaType): List<Metadata> {
        return db.select(METADATA)
            .from(METADATA)
            .where(METADATA.MEDIA_TYPE.eq(type))
            .orderBy(DSL.lower(METADATA.TITLE))
            .awaitInto()
    }

    suspend fun findByTypeSortedByTitle(
        type: MediaType,
        limit: Int,
        offset: Int,
    ): List<Metadata> {
        return db.select(METADATA)
            .from(METADATA)
            .where(METADATA.MEDIA_TYPE.eq(type))
            .orderBy(DSL.lower(METADATA.TITLE))
            .limit(limit)
            .offset(offset)
            .awaitInto()
    }

    suspend fun findByTmdbIdAndType(tmdbId: Int, type: MediaType): Metadata? {
        return db.selectFrom(METADATA)
            .where(METADATA.TMDB_ID.eq(tmdbId))
            .and(METADATA.MEDIA_TYPE.eq(type))
            .awaitFirstOrNullInto()
    }

    suspend fun findAllByTmdbIdsAndType(tmdbId: List<Int>, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(METADATA.TMDB_ID.`in`(tmdbId))
            .and(METADATA.MEDIA_TYPE.eq(type))
            .awaitInto()
    }

    suspend fun count(): Long {
        return db.fetchCountAsync(METADATA).toLong()
    }

    suspend fun countByType(type: MediaType): Long {
        return db.fetchCountAsync(METADATA, METADATA.MEDIA_TYPE.eq(type)).toLong()
    }

    suspend fun countSeasonsForTvShow(showId: String): Int {
        return db.fetchCountAsync(
            METADATA,
            METADATA.PARENT_ID.eq(showId),
            METADATA.INDEX.gt(0)
        )
    }

    suspend fun insertMetadata(metadata: Metadata): String {
        val newMetadata: Metadata = db.newRecordAsync(METADATA, MetadataRecord(metadata))
        return newMetadata.id
    }

    suspend fun insertMetadata(metadata: List<Metadata>) {
        db.batchInsert(metadata.map { MetadataRecord(it) })
            .executeAsync()
            .await()
    }

    suspend fun deleteById(metadataId: String) {
        db.delete(METADATA)
            .where(METADATA.ID.eq(metadataId))
            .awaitFirstOrNull()
    }

    suspend fun deleteByRootId(rootId: String) {
        db.delete(METADATA)
            .where(METADATA.ROOT_ID.eq(rootId))
            .awaitFirstOrNull()
    }
}
