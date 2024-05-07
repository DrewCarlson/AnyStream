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

import anystream.db.tables.references.METADATA
import anystream.db.util.fetchIntoType
import anystream.db.util.fetchOptionalIntoType
import anystream.db.util.intoType
import anystream.models.Metadata
import anystream.models.MediaType
import org.jooq.DSLContext
import org.jooq.impl.DSL

class MetadataDao(
    private val db: DSLContext,
) {

    fun findAllByParentGidAndType(parentGid: String, type: MediaType): List<Metadata> {
        return db.fetch(
            METADATA,
            METADATA.PARENT_ID.eq(parentGid),
            METADATA.MEDIA_TYPE.eq(type)
        ).intoType()
    }

    fun findAllByParentIdAndType(parentId: String, type: MediaType): List<Metadata> {
        return db.fetch(
            METADATA,
            METADATA.PARENT_ID.eq(parentId),
            METADATA.MEDIA_TYPE.eq(type)
        ).intoType()
    }

    fun findAllByRootGidAndType(rootGid: String, type: MediaType): List<Metadata> {
        return db.fetch(
            METADATA,
            METADATA.ROOT_ID.eq(rootGid),
            METADATA.MEDIA_TYPE.eq(type)
        ).intoType()
    }

    fun findAllByRootGidAndParentIndexAndType(
        rootGid: String,
        parentIndex: Int,
        type: MediaType
    ): List<Metadata> {
        return db.fetch(
            METADATA,
            METADATA.ROOT_ID.eq(rootGid),
            METADATA.PARENT_INDEX.eq(parentIndex),
            METADATA.MEDIA_TYPE.eq(type)
        ).intoType()
    }

    fun findByIdAndType(id: String, type: MediaType): Metadata? {
        return db.fetchOne(
            METADATA,
            METADATA.ID.eq(id),
            METADATA.MEDIA_TYPE.eq(type)
        )?.intoType()
    }

    fun findAllByIdsAndType(ids: List<String>, type: MediaType): List<Metadata> {
        return db.fetch(METADATA, METADATA.ID.`in`(ids), METADATA.MEDIA_TYPE.eq(type)).intoType()
    }

    fun findByGidAndType(gid: String, type: MediaType): Metadata? {
        return db.fetchOne(
            METADATA,
            METADATA.ID.eq(gid),
            METADATA.MEDIA_TYPE.eq(type)
        )?.intoType()
    }

    fun findAllByGidsAndType(gids: List<String>, type: MediaType): List<Metadata> {
        if (gids.isEmpty()) return emptyList()
        return db.fetch(
            METADATA,
            METADATA.ID.`in`(gids),
            METADATA.MEDIA_TYPE.eq(type)
        ).intoType()
    }

    fun findByGid(gid: String): Metadata? {
        return db.fetchOne(METADATA, METADATA.ID.eq(gid))?.intoType()
    }

    fun findTypeByGid(gid: String): MediaType? {
        return db.select(METADATA.MEDIA_TYPE, METADATA.ID.eq(gid))
            .fetchOptionalIntoType()
    }

    fun findTypeById(id: String): MediaType? {
        return db.select(METADATA.MEDIA_TYPE, METADATA.ID.eq(id))
            .fetchOptionalIntoType()
    }

    fun findIdForGid(gid: String): Int? {
        return db.select(METADATA.ID, METADATA.ID.eq(gid))
            .fetchOptionalIntoType()
    }

    fun findByType(type: MediaType, limit: Int): List<Metadata> {
        return db.select()
            .from(
                DSL.selectDistinct(METADATA.ID)
                    .from(METADATA)
                    .where(METADATA.MEDIA_TYPE.eq(type))
                    .orderBy(METADATA.CREATED_AT.desc())
                    .limit(limit)
            )
            .naturalJoin(METADATA)
            .orderBy(METADATA.CREATED_AT.desc())
            .fetchIntoType()
    }

    fun findAllByTypeSortedByTitle(type: MediaType): List<Metadata> {
        return db.select()
            .from(
                DSL.selectDistinct(METADATA.ID)
                    .from(METADATA)
                    .where(METADATA.MEDIA_TYPE.eq(type))
            )
            .naturalJoin(METADATA)
            .orderBy(DSL.lower(METADATA.TITLE))
            .fetchIntoType()
    }

    fun findByTypeSortedByTitle(
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
            .fetchIntoType()
    }

    fun findByTmdbIdAndType(tmdbId: Int, type: MediaType): Metadata? {
        return db.selectFrom(METADATA)
            .where(METADATA.TMDB_ID.eq(tmdbId))
            .and(METADATA.MEDIA_TYPE.eq(type))
            .fetchOptionalIntoType()
    }

    fun findAllByTmdbIdsAndType(tmdbId: List<Int>, type: MediaType): List<Metadata> {
        return db.selectFrom(METADATA)
            .where(METADATA.TMDB_ID.`in`(tmdbId))
            .and(METADATA.MEDIA_TYPE.eq(type))
            .fetchIntoType()
    }

    fun count(): Long {
        return db.fetchCount(METADATA).toLong()
    }

    fun countByType(type: MediaType): Long {
        return db.fetchCount(METADATA, METADATA.MEDIA_TYPE.eq(type)).toLong()
    }

    fun countSeasonsForTvShow(showId: String): Int {
        return db.fetchCount(
            METADATA,
            METADATA.PARENT_ID.eq(showId),
            METADATA.INDEX.gt(0)
        )
    }

    fun insertMetadata(metadata: Metadata): String {
        return db.newRecord(METADATA, metadata)
            .apply { store() }
            .id
    }

    fun deleteById(metadataId: String) {
        db.delete(METADATA)
            .where(METADATA.ID.eq(metadataId))
            .execute()
    }

    fun deleteByGid(metadataId: String) {
        db.delete(METADATA)
            .where(METADATA.ID.eq(metadataId))
            .execute()
    }

    fun deleteByRootGid(rootGid: String) {
        db.delete(METADATA)
            .where(METADATA.ROOT_ID.eq(rootGid))
            .execute()
    }
}
