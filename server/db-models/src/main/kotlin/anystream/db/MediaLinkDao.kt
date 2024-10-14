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
import anystream.db.util.fetchIntoType
import anystream.db.util.fetchSingleIntoType
import anystream.db.util.intoType
import anystream.models.MediaLink
import anystream.models.Descriptor
import anystream.models.StreamEncoding
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.length


class MediaLinkDao(
    private val db: DSLContext
) {

    fun all(): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK).fetchIntoType()
    }

    fun insertLink(link: MediaLink): Int {
        return db.insertInto(MEDIA_LINK)
            .set(MediaLinkRecord(link))
            .execute()
    }

    fun insertLinks(link: List<MediaLink>) {
        val records = link.map(::MediaLinkRecord)
        db.batchInsert(records)
            .execute()
    }

    fun insertStreamDetails(streams: List<StreamEncoding>) {
        val records = streams.map { StreamEncodingRecord(it) }
        db.batchInsert(records)
            .execute()
    }

    fun countStreamDetails(mediaLinkId: String): Int {
        return db.fetchCount(STREAM_ENCODING, STREAM_ENCODING.MEDIA_LINK_ID.eq(mediaLinkId))
    }

    fun updateMediaLinkIds(mediaLink: List<MediaLink>) {
        mediaLink.forEach { link ->
            db.update(MEDIA_LINK)
                .set(MEDIA_LINK.METADATA_ID, link.metadataId)
                .set(MEDIA_LINK.ROOT_METADATA_ID, link.rootMetadataId)
                .where(MEDIA_LINK.ID.eq(link.id))
                .execute()
        }
    }

    fun updateMetadataIds(mediaLinkId: String, metadataId: String) {
        db.update(MEDIA_LINK)
            .set(MEDIA_LINK.METADATA_ID, metadataId)
            .where(MEDIA_LINK.ID.eq(mediaLinkId))
            .execute()
    }

    //@SqlUpdate("UPDATE mediaLink SET rootMetadataId = :rootMetadataId, rootMetadataGid = :rootMetadataGid WHERE id = :mediaLinkId")
    fun updateRootMetadataIds(mediaLinkId: String, rootMetadataId: String) {
        // TODO: Implement
    }

    fun descriptorForGid(id: String): Descriptor? {
        return db.select(MEDIA_LINK.DESCRIPTOR)
            .from(MEDIA_LINK)
            .where(MEDIA_LINK.ID.eq(id))
            .fetchOne()
            ?.intoType()
    }

    fun findByGid(id: String): MediaLink? {
        return db.fetchOne(MEDIA_LINK, MEDIA_LINK.ID.eq(id))?.intoType()
    }

    fun findByGids(ids: List<String>): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ID.`in`(ids))
            .fetchIntoType()
    }

    fun findByMetadataId(metadataId: String): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.METADATA_ID.eq(metadataId))
            .fetchIntoType()
    }

    fun findByMetadataIds(ids: List<String>): List<MediaLink> {
        if (ids.isEmpty()) return emptyList()
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.METADATA_ID.`in`(ids))
            .fetchIntoType()
    }

    fun findByRootMetadataIds(ids: List<String>): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ROOT_METADATA_ID.`in`(ids))
            .fetchIntoType()
    }

    fun findByBasePathAndDescriptor(basePath: String, descriptor: Descriptor): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.like("$basePath || '%'"))
            .and(MEDIA_LINK.DESCRIPTOR.eq(descriptor))
            .fetchIntoType()
    }

    fun findByBasePathAndDescriptors(basePath: String, descriptor: List<Descriptor>): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.like("$basePath || '%'"))
            .and(MEDIA_LINK.DESCRIPTOR.`in`(descriptor))
            .fetchIntoType()
    }

    fun findByParentId(parentId: String): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.DIRECTORY_ID.eq(parentId))
            .fetchIntoType()
    }

    //@SqlQuery("SELECT * FROM mediaLinkView WHERE mediaLink_parentGid = ?")
    fun findByParentGid(parentGid: String): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            //TODO .where(MEDIA_LINK.DIRECTORY_ID.eq(parentGid))
            .fetchIntoType()
    }

    fun findByParentIdAndDescriptor(parentId: String, descriptor: Descriptor): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.DIRECTORY_ID.eq(parentId))
            .and(MEDIA_LINK.DESCRIPTOR.eq(descriptor))
            .fetchIntoType()
    }

    fun findByFilePath(filePath: String): MediaLink? {
        return db.fetchOne(MEDIA_LINK, MEDIA_LINK.FILE_PATH.`in`(filePath))?.intoType()
    }

    fun findIdsByFilePaths(filePaths: List<String>): List<String> {
        return db.select(MEDIA_LINK.ID)
            .from(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.`in`(filePaths))
            .fetchIntoType()
    }

    fun findGidsByMediaLinkGidAndDescriptors(
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
            .fetchIntoType()
    }

    fun findFilePathsByBasePath(basePath: String): List<String> {
        // SELECT filePath FROM mediaLink WHERE filePath LIKE ? || '%'
        return db.select(MEDIA_LINK.FILE_PATH)
            .from(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.like("$basePath || '%'"))
            .fetchIntoType()
    }

    fun findFilePathsByGids(gids: List<String>): List<String> {
        return db.select(MEDIA_LINK.FILE_PATH)
            .from(MEDIA_LINK)
            .where(MEDIA_LINK.ID.`in`(gids))
            .fetchIntoType()
    }

    fun findByDescriptor(descriptor: Descriptor): List<MediaLink> {
        return db.selectFrom(MEDIA_LINK)
            .where(MEDIA_LINK.DESCRIPTOR.eq(descriptor))
            .fetchIntoType()
    }

    fun findAllFilePaths(): List<String> {
        return db.select(MEDIA_LINK.FILE_PATH)
            .from(MEDIA_LINK)
            .where(length(MEDIA_LINK.FILE_PATH).greaterThan(0))
            .fetchIntoType()
    }

    fun deleteByContentGid(metadataGid: String) {
        db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.METADATA_ID.eq(metadataGid))
            .execute()
    }

    fun deleteByRootContentGid(rootMetadataGid: String) {
        db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ROOT_METADATA_ID.eq(rootMetadataGid))
            .execute()
    }

    fun deleteByFilePaths(filePaths: List<String>): String {
        return db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.`in`(filePaths))
            .returning(MEDIA_LINK.ID)
            .fetchSingleIntoType()
    }

    fun deleteByBasePath(filePath: String): Int {
        // DELETE FROM mediaLink WHERE filePath LIKE ? || '%'
        return db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.FILE_PATH.like("$filePath || '%'"))
            .execute()
    }

    fun deleteDownloadByHash(hash: String) {
        db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.HASH.eq(hash))
            .execute()
    }

    fun deleteByGid(id: String): Boolean {
        return db.deleteFrom(MEDIA_LINK)
            .where(MEDIA_LINK.ID.eq(id))
            .execute() == 1
    }
}
