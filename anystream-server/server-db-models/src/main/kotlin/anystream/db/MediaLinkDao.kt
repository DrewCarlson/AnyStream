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

import anystream.db.model.*
import anystream.models.MediaLink
import org.jdbi.v3.core.result.LinkedHashMapRowReducer
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.sqlobject.config.RegisterJoinRowMapper
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMappers
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.statement.UseRowReducer

@RegisterKotlinMappers(
    RegisterKotlinMapper(MediaLinkDb::class, prefix = "mediaLink"),
    RegisterKotlinMapper(StreamEncodingDetailsDb::class, prefix = "streamEncoding"),
)
interface MediaLinkDao {

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery(MEDIALINK_SELECT)
    fun all(): List<MediaLinkDb>

    @SqlUpdate
    @GetGeneratedKeys("id")
    @UseClasspathSqlLocator
    fun insertLink(link: MediaLinkDb): Int

    @SqlBatch
    @UseClasspathSqlLocator
    fun insertLink(link: List<MediaLinkDb>)

    @SqlBatch
    @UseClasspathSqlLocator
    fun insertStreamDetails(stream: List<StreamEncodingDetailsDb>)

    @SqlQuery("SELECT COUNT(id) FROM streamEncoding WHERE mediaLinkId = :mediaLinkId")
    fun countStreamDetails(mediaLinkId: Int): Int

    @SqlBatch(
        """
            UPDATE mediaLink
            SET
             metadataId = :mediaLink.metadataId,
             metadataGid = :mediaLink.metadataGid,
             rootMetadataId = :mediaLink.rootMetadataId,
             rootMetadataGid = :mediaLink.rootMetadataGid,
             parentMediaLinkId = :mediaLink.parentMediaLinkId,
             parentMediaLinkGid = :mediaLink.parentMediaLinkGid
            WHERE id = :mediaLink.id
        """,
    )
    fun updateMediaLinkIds(mediaLink: List<MediaLinkDb>)

    @SqlUpdate("UPDATE mediaLink SET metadataId = :metadataId, metadataGid = :metadataGid WHERE id = :mediaLinkId")
    fun updateMetadataIds(mediaLinkId: Int, metadataId: Int, metadataGid: String)

    @SqlUpdate("UPDATE mediaLink SET rootMetadataId = :rootMetadataId, rootMetadataGid = :rootMetadataGid WHERE id = :mediaLinkId")
    fun updateRootMetadataIds(mediaLinkId: Int, rootMetadataId: Int, rootMetadataGid: String)

    @SqlUpdate("UPDATE mediaLink SET metadataId = :metadataId, metadataGid = :metadataGid WHERE gid = :mediaLinkGid")
    fun updateMetadataIds(mediaLinkGid: String, metadataId: Int, metadataGid: String)

    @SqlUpdate("UPDATE mediaLink SET rootMetadataId = :rootMetadataId, rootMetadataGid = :rootMetadataGid WHERE gid = :mediaLinkGid")
    fun updateRootMetadataIds(mediaLinkGid: String, rootMetadataId: Int, rootMetadataGid: String)

    @SqlUpdate("UPDATE mediaLink SET parentMediaLinkId = :parentMediaLinkId, parentMediaLinkGid = :parentMediaLinkGid WHERE gid = :mediaLinkGid")
    fun updateParentMediaLinkId(mediaLinkGid: String, parentMediaLinkId: Int, parentMediaLinkGid: String)

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE gid = ?")
    fun findByGid(gid: String): MediaLinkDb?

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE gid IN (<gids>)")
    fun findByGids(@BindList("gids") gids: List<String>): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE metadataGid = ?")
    fun findByMetadataGid(metadataGid: String): List<MediaLinkDb>

    @RegisterJoinRowMapper(StreamEncodingDetailsDb::class)
    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE metadataGid IN (<gids>)")
    fun findByMetadataGids(@BindList("gids") gids: List<String>): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE rootMetadataGid = ?")
    fun findByRootMetadataGid(metadataGid: String): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE rootMetadataGid IN (<gids>)")
    fun findByRootMetadataGids(@BindList("gids") gids: List<String>): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE filePath LIKE ? || '%'")
    fun findByBasePath(basePath: String): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE filePath LIKE ? || '%' AND descriptor = ?")
    fun findByBasePathAndDescriptor(basePath: String, descriptor: MediaLink.Descriptor): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE filePath LIKE :basePath || '%' AND descriptor IN (<descriptor>)")
    fun findByBasePathAndDescriptors(
        basePath: String,
        @BindList("descriptor") descriptor: List<MediaLink.Descriptor>,
    ): List<MediaLinkDb>

    @SqlQuery("SELECT count(id) FROM mediaLink WHERE filePath LIKE ? || '%' AND descriptor = ?")
    fun countInBasePath(basePath: String, descriptor: MediaLink.Descriptor): Int

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE filePath = ?")
    fun findByFilePath(filePath: String): MediaLinkDb?

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE filePath IN (<filePaths>)")
    fun findByFilePaths(@BindList("filePaths") filePaths: List<String>): List<MediaLinkDb>

    @SqlQuery("SELECT gid FROM mediaLink WHERE filePath LIKE ?")
    fun findGidsByBasePath(filePath: String): List<String>

    @SqlQuery("SELECT gid FROM mediaLink WHERE filePath IN (<filePaths>)")
    fun findGidsByFilePaths(@BindList("filePaths") filePaths: List<String>): List<String>

    @SqlQuery("SELECT gid FROM mediaLink WHERE metadataGid = :metadataGid OR rootMetadataGid = :metadataGid")
    fun findGidsByMetadataGid(metadataGid: String): List<String>

    @SqlQuery("SELECT gid FROM mediaLink WHERE (metadataGid = :metadataGid OR rootMetadataGid = :metadataGid) AND descriptor = :descriptor")
    fun findGidsByMetadataGidAndDescriptor(metadataGid: String, descriptor: MediaLink.Descriptor): List<String>

    @SqlQuery("SELECT gid FROM mediaLink WHERE (metadataGid = :metadataGid OR rootMetadataGid = :metadataGid) AND descriptor IN (<descriptors>)")
    fun findGidsByMetadataGidAndDescriptors(
        metadataGid: String,
        @BindList("descriptors") descriptors: List<MediaLink.Descriptor>,
    ): List<String>

    @SqlQuery("SELECT filePath FROM mediaLink WHERE filePath LIKE ? || '%'")
    fun findFilePathsByBasePath(basePath: String): List<String>

    @SqlQuery("SELECT filePath FROM mediaLink WHERE gid IN (<mediaLinkGids>)")
    fun findFilePathsByGids(@BindList("mediaLinkGids") gids: List<String>): List<String>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("$MEDIALINK_SELECT WHERE descriptor = ?")
    fun findByDescriptor(descriptor: MediaLink.Descriptor): List<MediaLinkDb>

    @SqlQuery("SELECT filePath FROM mediaLink WHERE length(mediaLink.filePath) > 0")
    fun findAllFilePaths(): List<String>

    @SqlUpdate("DELETE FROM mediaLink WHERE metadataGid = ?")
    fun deleteByContentGid(metadataGid: String)

    @SqlUpdate("DELETE FROM mediaLink WHERE rootMetadataGid = ?")
    fun deleteByRootContentGid(rootMetadataGid: String)

    @SqlUpdate("DELETE FROM mediaLink WHERE filePath IN (<filePaths>)")
    fun deleteByFilePaths(@BindList("filePaths") filePaths: List<String>): Int

    @SqlUpdate("DELETE FROM mediaLink WHERE filePath LIKE ? || '%'")
    fun deleteByBasePath(filePath: String): Int

    @SqlUpdate("DELETE FROM mediaLink WHERE hash = ?")
    fun deleteDownloadByHash(hash: String)

    @SqlUpdate("DELETE FROM mediaLink WHERE gid = ?")
    fun deleteByGid(gid: String): Boolean

    class MediaReferenceReducer : LinkedHashMapRowReducer<Int, MediaLinkDb> {
        override fun accumulate(container: MutableMap<Int, MediaLinkDb>?, rowView: RowView?) {
            checkNotNull(container)
            checkNotNull(rowView)
            val rowId = rowView.getColumn("mediaLink_id", Integer::class.javaObjectType).toInt()
            var mediaLinkDb = container.computeIfAbsent(rowId) { rowView.getRow(MediaLinkDb::class.java) }
            rowView.getColumn("streamEncoding_mediaLinkId", Integer::class.javaObjectType)?.let { joinId ->
                if (mediaLinkDb.streams.none { it.id == joinId.toInt() }) {
                    val stream = rowView.getRow(StreamEncodingDetailsDb::class.java)
                    mediaLinkDb = mediaLinkDb.copy(streams = mediaLinkDb.streams + stream)
                }
            }

            container[rowId] = mediaLinkDb
        }
    }
}
