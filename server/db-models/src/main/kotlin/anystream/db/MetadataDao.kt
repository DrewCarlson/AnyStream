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
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlin.time.Clock
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.coalesce

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

    suspend fun findRootIdOrSelf(metadataId: String): String? {
        return db.select(coalesce(METADATA.ROOT_ID, METADATA.ID))
            .from(METADATA)
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

    suspend fun deleteById(metadataId: String): Int {
        return db.delete(METADATA)
            .where(METADATA.ID.eq(metadataId))
            .awaitFirst()
    }

    suspend fun deleteByRootId(rootId: String): Int {
        return db.delete(METADATA)
            .where(METADATA.ROOT_ID.eq(rootId))
            .awaitFirst()
    }

    /**
     * Find all metadata IDs that belong to a root (e.g., all seasons and episodes for a TV show).
     * Does NOT include the root ID itself.
     */
    suspend fun findAllIdsByRootId(rootId: String): List<String> {
        return db.select(METADATA.ID)
            .from(METADATA)
            .where(METADATA.ROOT_ID.eq(rootId))
            .awaitInto()
    }

    /**
     * Update an existing metadata record.
     * @param metadata The metadata to update (must have an existing ID)
     * @return The number of rows updated (0 or 1)
     */
    suspend fun updateMetadata(metadata: Metadata): Int {
        val updatedMetadata = metadata.copy(updatedAt = Clock.System.now())
        return db.update(METADATA)
            .set(MetadataRecord(updatedMetadata))
            .where(METADATA.ID.eq(metadata.id))
            .awaitFirst()
    }

    /**
     * Insert or update metadata.
     * If a record with the same ID exists, it will be updated. Otherwise, a new record is inserted.
     * @param metadata The metadata to upsert
     * @return The ID of the metadata record
     */
    suspend fun upsertMetadata(metadata: Metadata): String {
        val now = Clock.System.now()
        val record = MetadataRecord(metadata.copy(updatedAt = now))

        db.insertInto(METADATA)
            .set(record)
            .onDuplicateKeyUpdate()
            .set(METADATA.TITLE, metadata.title)
            .set(METADATA.OVERVIEW, metadata.overview)
            .set(METADATA.TMDB_ID, metadata.tmdbId)
            .set(METADATA.IMDB_ID, metadata.imdbId)
            .set(METADATA.RUNTIME, metadata.runtime)
            .set(METADATA.INDEX, metadata.index)
            .set(METADATA.CONTENT_RATING, metadata.contentRating)
            .set(METADATA.FIRST_AVAILABLE_AT, metadata.firstAvailableAt)
            .set(METADATA.TMDB_RATING, metadata.tmdbRating)
            .set(METADATA.UPDATED_AT, now)
            .awaitFirstOrNull()

        return metadata.id
    }

    /**
     * Batch update multiple metadata records.
     * @param metadataList List of metadata to update
     * @return Number of records updated
     */
    suspend fun updateMetadataBatch(metadataList: List<Metadata>): Int {
        if (metadataList.isEmpty()) return 0

        val now = Clock.System.now()
        val updates = metadataList.map { metadata ->
            db.update(METADATA)
                .set(MetadataRecord(metadata.copy(updatedAt = now)))
                .where(METADATA.ID.eq(metadata.id))
        }
        return db.batch(updates).executeAsync().await().sum()
    }
}
