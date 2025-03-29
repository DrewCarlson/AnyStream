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

import anystream.db.tables.records.MediaLinkRecord
import anystream.db.tables.records.StreamEncodingRecord
import anystream.db.tables.references.MEDIA_LINK
import anystream.db.tables.references.STREAM_ENCODING
import anystream.db.util.*
import anystream.models.MediaLink
import anystream.models.Descriptor
import anystream.models.StreamEncoding
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.length
import org.jooq.kotlin.coroutines.transactionCoroutine


class MediaLinkDao(
    private val db: DSLContext
) {

    suspend fun all(): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK).awaitInto()
    }

    suspend fun insertLink(link: MediaLink): Int {
        return db.insertInto(MEDIA_LINK)
            .set(MediaLinkRecord(link))
            .awaitFirst()
    }

    suspend fun insertLinks(link: List<MediaLink>) {
        val records = link.map(::MediaLinkRecord)
        db.batchInsert(records).awaitFirst()
    }

    suspend fun insertStreamDetails(streams: List<StreamEncoding>) {
        val records = streams.map { StreamEncodingRecord(it) }
        db.batchInsert(records).awaitFirst()
    }

    suspend fun countStreamDetails(mediaLinkId: String): Int {
        return db.fetchCountAsync(STREAM_ENCODING, STREAM_ENCODING.MEDIA_LINK_ID.eq(mediaLinkId))
    }

    suspend fun updateMediaLinkIds(mediaLink: List<MediaLink>) {
        db.transactionCoroutine {
            mediaLink.forEach { link ->
                db.update(MEDIA_LINK)
                    .set(MEDIA_LINK.METADATA_ID, link.metadataId)
                    .set(MEDIA_LINK.ROOT_METADATA_ID, link.rootMetadataId)
                    .where(MEDIA_LINK.ID.eq(link.id))
                    .execute()
            }
        }
    }

    suspend fun updateMetadataIds(mediaLinkId: String, metadataId: String): Boolean {
        return db.update(MEDIA_LINK)
            .set(MEDIA_LINK.METADATA_ID, metadataId)
            .where(MEDIA_LINK.ID.eq(mediaLinkId))
            .awaitFirstOrNull() == 1
    }

    suspend fun updateRootMetadataIds(mediaLinkId: String, rootMetadataId: String): Boolean {
        return db.update(MEDIA_LINK)
            .set(MEDIA_LINK.ROOT_METADATA_ID, rootMetadataId)
            .where(MEDIA_LINK.ID.eq(mediaLinkId))
            .awaitFirstOrNull() == 1
    }

    suspend fun descriptorForGid(id: String): Descriptor? {
        return db.select(MEDIA_LINK.DESCRIPTOR)
            .from(MEDIA_LINK)
            .where(MEDIA_LINK.ID.eq(id))
            .awaitFirstOrNullInto()
    }

    suspend fun findById(id: String): MediaLink? {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ID.eq(id))
            .awaitFirstOrNullInto()
    }

    suspend fun findByGids(ids: List<String>): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ID.`in`(ids))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByMetadataId(metadataId: String): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.METADATA_ID.eq(metadataId))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByMetadataIds(ids: List<String>): List<MediaLink> {
        if (ids.isEmpty()) return emptyList()
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.METADATA_ID.`in`(ids))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByRootMetadataIds(ids: List<String>): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ROOT_METADATA_ID.`in`(ids))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByBasePathAndDescriptor(basePath: String, descriptor: Descriptor): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.like("$basePath%"))
            .and(MEDIA_LINK.DESCRIPTOR.eq(descriptor))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByBasePathAndDescriptors(basePath: String, descriptor: List<Descriptor>): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.like("$basePath%"))
            .and(MEDIA_LINK.DESCRIPTOR.`in`(descriptor))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByParentId(parentId: String): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.DIRECTORY_ID.eq(parentId))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByParentGid(parentGid: String): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            // todo: is this still correct?
            .where(MEDIA_LINK.DIRECTORY_ID.eq(parentGid))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByParentIdAndDescriptor(parentId: String, descriptor: Descriptor): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.DIRECTORY_ID.eq(parentId))
            .and(MEDIA_LINK.DESCRIPTOR.eq(descriptor))
            .awaitFirstOrNullInto<List<MediaLink>>()
            .orEmpty()
    }

    suspend fun findByFilePath(filePath: String): MediaLink? {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.eq(filePath))
            .awaitFirstOrNullInto()
    }

    suspend fun findIdsByMediaLinkIdAndDescriptors(
        mediaLinkGid: String,
        descriptors: List<Descriptor>,
    ): List<String> {
        return db.select(MEDIA_LINK.ID)
            .from(MEDIA_LINK)
            .where(
                DSL.or(
                    MEDIA_LINK.ID.eq(mediaLinkGid),
                    //TODO: MEDIA_LINK.PARENT_GID.eq(mediaLinkGid)
                )
            )
            .and(MEDIA_LINK.DESCRIPTOR.`in`(descriptors))
            .awaitFirstOrNullInto<List<String>>()
            .orEmpty()
    }

    suspend fun findByBasePath(basePath: String): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.like("$basePath%"))
            .awaitInto()
    }

    suspend fun findAllFilePaths(): List<String> {
        return db.select(MEDIA_LINK.FILE_PATH)
            .from(MEDIA_LINK)
            .where(length(MEDIA_LINK.FILE_PATH).greaterThan(0))
            .awaitFirstOrNullInto<List<String>>()
            .orEmpty()
    }

    suspend fun deleteByContentGid(metadataGid: String) {
        db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.METADATA_ID.eq(metadataGid))
            .awaitFirstOrNull()
    }

    suspend fun deleteByRootContentGid(rootMetadataGid: String) {
        db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ROOT_METADATA_ID.eq(rootMetadataGid))
            .awaitFirstOrNull()
    }

    suspend fun deleteByBasePath(filePath: String): List<String> {
        val filterCondition = MEDIA_LINK.FILE_PATH.like("$filePath%")
        val ids: List<String> = db
            .select(MEDIA_LINK.ID)
            .from(MEDIA_LINK)
            .where(filterCondition)
            .awaitInto()
        // TODO: verify list of removed ids and filter the results
        val deleted = db.deleteFrom(MEDIA_LINK)
            .where(filterCondition)
            .awaitFirstOrNull() == ids.size

        return if (deleted) ids else emptyList()
    }

    suspend fun deleteDownloadByHash(hash: String): Boolean {
        return db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.HASH.eq(hash))
            .awaitFirstOrNull() == 1
    }

    suspend fun deleteByGid(id: String): Boolean {
        return db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ID.eq(id))
            .awaitFirstOrNull() == 1
    }
}
