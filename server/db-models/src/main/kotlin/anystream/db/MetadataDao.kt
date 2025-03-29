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
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.impl.DSL

class MetadataDao(
    private val db: DSLContext,
) {

    suspend fun findAllByParentGidAndType(parentGid: String, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.PARENT_ID.eq(parentGid),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
    }

    suspend fun findAllByParentIdAndType(parentId: String, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.PARENT_ID.eq(parentId),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
    }

    suspend fun findAllByRootGidAndType(rootGid: String, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ROOT_ID.eq(rootGid),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
    }

    suspend fun findAllByRootGidAndParentIndexAndType(
        rootGid: String,
        parentIndex: Int,
        type: MediaType
    ): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ROOT_ID.eq(rootGid),
                METADATA.PARENT_INDEX.eq(parentIndex),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
    }

    suspend fun findByIdAndType(id: String, type: MediaType): Metadata? {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ID.eq(id),
                METADATA.MEDIA_TYPE.eq(type)
            ).awaitFirstOrNullInto()
    }

    suspend fun findAllByIdsAndType(ids: List<String>, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ID.`in`(ids),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
    }

    suspend fun findByGidAndType(gid: String, type: MediaType): Metadata? {
        return db.selectFrom(METADATA)
            .where(
                METADATA.ID.eq(gid),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitFirstOrNullInto()
    }

    suspend fun findAllByGidsAndType(gids: List<String>, type: MediaType): List<Metadata> {
        if (gids.isEmpty()) return emptyList()
        return db.selectFrom(METADATA)
            .where(
                METADATA.ID.`in`(gids),
                METADATA.MEDIA_TYPE.eq(type)
            )
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
    }

    suspend fun findByGid(gid: String): Metadata? {
        return db.selectFrom(METADATA)
            .where(METADATA.ID.eq(gid))
            .awaitFirstOrNullInto()
    }

    suspend fun findTypeByGid(gid: String): MediaType? {
        return db.select(METADATA.MEDIA_TYPE, METADATA.ID.eq(gid))
            .awaitFirstOrNullInto()
    }

    suspend fun findTypeById(id: String): MediaType? {
        return db.select(METADATA.MEDIA_TYPE, METADATA.ID.eq(id))
            .awaitFirstOrNullInto()
    }

    suspend fun findIdForGid(gid: String): Int? {
        return db.select(METADATA.ID, METADATA.ID.eq(gid))
            .awaitFirstOrNullInto()
    }

    suspend fun findByType(type: MediaType, limit: Int): List<Metadata> {
        val results: List<Metadata>? = db.select()
            .from(
                DSL.selectDistinct(METADATA.ID)
                    .from(METADATA)
                    .where(METADATA.MEDIA_TYPE.eq(type))
                    .orderBy(METADATA.CREATED_AT.desc())
                    .limit(limit)
            )
            .naturalJoin(METADATA)
            .orderBy(METADATA.CREATED_AT.desc())
            .awaitFirstOrNullInto()

        return results.orEmpty()
    }

    suspend fun findAllByTypeSortedByTitle(type: MediaType): List<Metadata> {
        return db.select()
            .from(
                DSL.selectDistinct(METADATA.ID)
                    .from(METADATA)
                    .where(METADATA.MEDIA_TYPE.eq(type))
            )
            .naturalJoin(METADATA)
            .orderBy(DSL.lower(METADATA.TITLE))
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
    }

    suspend fun findByTypeSortedByTitle(
        type: MediaType,
        limit: Int,
        offset: Int,
    ): List<Metadata> {
        return db.select()
            .from(
                DSL.selectDistinct(METADATA.ID)
                    .from(METADATA)
                    .where(METADATA.MEDIA_TYPE.eq(type))
                    .limit(limit)
                    .offset(offset)
            )
            .naturalJoin(METADATA)
            .orderBy(DSL.lower(METADATA.TITLE))
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
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
            .awaitFirstOrNullInto<List<Metadata>>()
            .orEmpty()
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

    suspend fun deleteById(metadataId: String) {
        db.delete(METADATA)
            .where(METADATA.ID.eq(metadataId))
            .awaitFirstOrNull()
    }

    suspend fun deleteByGid(metadataId: String) {
        db.delete(METADATA)
            .where(METADATA.ID.eq(metadataId))
            .awaitFirstOrNull()
    }

    suspend fun deleteByRootGid(rootGid: String) {
        db.delete(METADATA)
            .where(METADATA.ROOT_ID.eq(rootGid))
            .awaitFirstOrNull()
    }
}
