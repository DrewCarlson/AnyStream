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

import anystream.db.model.MediaLinkDb
import anystream.db.model.StreamEncodingDetailsDb
import anystream.models.MediaLink
import org.jdbi.v3.core.result.LinkedHashMapRowReducer
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMappers
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.statement.UseRowReducer

private const val MEDIAREF_COLUMNS = """
            mediaLink.id medialink_id,
            mediaLink.gid medialink_gid,
            mediaLink.metadataId medialink_metadataId,
            mediaLink.metadataGid medialink_metadataGid,
            mediaLink.rootMetadataId medialink_rootMetadataId,
            mediaLink.rootMetadataGid medialink_rootMetadataGid,
            mediaLink.parentMediaLinkId medialink_parentMediaLinkId,
            mediaLink.parentMediaLinkGid medialink_parentMediaLinkGid,
            mediaLink.addedAt medialink_addedAt,
            mediaLink.addedByUserId medialink_addedByUserId,
            mediaLink.mediaKind medialink_mediaKind,
            mediaLink.type medialink_type,
            mediaLink.updatedAt medialink_updatedAt,
            mediaLink.filePath medialink_filePath,
            mediaLink.directory medialink_directory,
            mediaLink.descriptor medialink_descriptor,
            mediaLink.hash medialink_hash,
            mediaLink.fileIndex medialink_fileIndex"""

private const val STEAM_COLUMNS = """
            s.id s_id,
            s.streamId s_streamId,
            s.codecName s_codecName,
            s.codecLongName s_codecLongName,
            s.'index' s_index,
            s.language s_language,
            s.profile s_profile,
            s.bitRate s_bitRate,
            s.channels s_channels,
            s.channelLayout s_channelLayout,
            s.level s_level,
            s.height s_height,
            s.width s_width,
            s.type s_type,
            s.pixFmt s_pixFmt,
            s.colorSpace s_colorSpace,
            s.colorRange s_colorRange,
            s.colorTransfer s_colorTransfer,
            s.colorPrimaries s_colorPrimaries,
            s.fieldOrder s_fieldOrder,
            s.sampleFmt s_sampleFmt,
            s.sampleRate s_sampleRate,
            s.duration s_duration,
            s.'default' s_default,
            s.mediaLinkId s_mediaLinkId"""

private const val JOIN_STREAMS = """
            LEFT JOIN streamEncoding s on s.mediaLinkId = mediaLink.id"""

@RegisterKotlinMappers(
    RegisterKotlinMapper(MediaLinkDb::class, prefix = "medialink"),
    RegisterKotlinMapper(StreamEncodingDetailsDb::class, prefix = "s"),
)
interface MediaLinkDao {

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS")
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
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE gid = ?")
    fun findByGid(gid: String): MediaLinkDb?

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE gid IN (<gids>)")
    fun findByGids(@BindList("gids") gids: List<String>): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE metadataGid = ?")
    fun findByMetadataGid(metadataGid: String): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE metadataGid IN (<gids>)")
    fun findByMetadataGids(@BindList("gids") gids: List<String>): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE rootMetadataGid = ?")
    fun findByRootMetadataGid(metadataGid: String): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE rootMetadataGid IN (<gids>)")
    fun findByRootMetadataGids(@BindList("gids") gids: List<String>): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE filePath LIKE ? || '%'")
    fun findByBasePath(basePath: String): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE filePath LIKE ? || '%' AND descriptor = ?")
    fun findByBasePathAndDescriptor(basePath: String, descriptor: MediaLink.Descriptor): List<MediaLinkDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE filePath LIKE :basePath || '%' AND descriptor IN (<descriptor>)")
    fun findByBasePathAndDescriptors(
        basePath: String,
        @BindList("descriptor") descriptor: List<MediaLink.Descriptor>,
    ): List<MediaLinkDb>

    @SqlQuery("SELECT count(id) FROM mediaLink WHERE filePath LIKE ? || '%' AND descriptor = ?")
    fun countInBasePath(basePath: String, descriptor: MediaLink.Descriptor): Int

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE filePath = ?")
    fun findByFilePath(filePath: String): MediaLinkDb?

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE filePath IN (<filePaths>)")
    fun findByFilePaths(@BindList("filePaths") filePaths: List<String>): List<MediaLinkDb>

    @SqlQuery("SELECT gid FROM mediaLink WHERE filePath IN (<filePaths>)")
    fun findGidsByFilePaths(@BindList("filePaths") filePaths: List<String>): List<String>

    @SqlQuery("SELECT gid FROM mediaLink WHERE metadataGid = :metadataGid OR rootMetadataGid = :metadataGid")
    fun findGidsByMetadataGid(metadataGid: String): List<String>

    @SqlQuery("SELECT filePath FROM mediaLink WHERE filePath LIKE ? || '%'")
    fun findFilePathsByBasePath(basePath: String): List<String>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaLink $JOIN_STREAMS WHERE descriptor = ?")
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
            val rowId = rowView.getColumn("medialink_id", Integer::class.javaObjectType) as Int
            var mediaLinkDb = container.computeIfAbsent(rowId) { rowView.getRow(MediaLinkDb::class.java) }
            rowView.getColumn("s_mediaLinkId", Integer::class.javaObjectType)?.let { joinId ->
                if (mediaLinkDb.streams.none { it.id == joinId.toInt() }) {
                    val stream = rowView.getRow(StreamEncodingDetailsDb::class.java)
                    mediaLinkDb = mediaLinkDb.copy(streams = mediaLinkDb.streams + stream)
                }
            }

            container[rowId] = mediaLinkDb
        }
    }
}
