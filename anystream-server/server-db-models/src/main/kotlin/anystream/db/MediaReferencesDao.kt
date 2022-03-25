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

import anystream.db.model.MediaReferenceDb
import anystream.db.model.StreamEncodingDetailsDb
import org.jdbi.v3.core.result.LinkedHashMapRowReducer
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMappers
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.statement.UseRowReducer

private const val MEDIAREF_COLUMNS =
    """            mediaReferences.id mediaref_id,
            mediaReferences.gid mediaref_gid,
            mediaReferences.contentGid mediaref_contentGid,
            mediaReferences.rootContentGid mediaref_rootContentGid,
            mediaReferences.addedAt mediaref_addedAt,
            mediaReferences.addedByUserId mediaref_addedByUserId,
            mediaReferences.mediaKind mediaref_mediaKind,
            mediaReferences.type mediaref_type,
            mediaReferences.updatedAt mediaref_updatedAt,
            mediaReferences.filePath mediaref_filePath,
            mediaReferences.directory mediaref_directory,
            mediaReferences.hash mediaref_hash,
            mediaReferences.fileIndex mediaref_fileIndex"""

private const val STEAM_COLUMNS = """
            s.id s_id,
            s.codecName s_codecName,
            s.rawProbeData s_rawProbeData,
            s.'index' s_index,
            s.language s_language,
            s.profile s_profile,
            s.bitRate s_bitRate,
            s.channels s_channels,
            s.level s_level,
            s.height s_height,
            s.width s_width,
            s.type s_type"""

private const val JOIN_STREAMS = """
            LEFT JOIN streamEncodingLinks sl on sl.mediaRefId = mediaReferences.id
            LEFT JOIN streamEncoding s on s.id = sl.streamId"""

@RegisterKotlinMappers(
    RegisterKotlinMapper(MediaReferenceDb::class, prefix = "mediaref"),
    RegisterKotlinMapper(StreamEncodingDetailsDb::class, prefix = "s"),
)
interface MediaReferencesDao {

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createTable()

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createStreamTable()

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createStreamLinkTable()

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaReferences $JOIN_STREAMS")
    fun all(): List<MediaReferenceDb>

    @SqlUpdate
    @GetGeneratedKeys("id")
    @UseClasspathSqlLocator
    fun insertReference(ref: MediaReferenceDb): Int

    @SqlUpdate
    @GetGeneratedKeys("id")
    @UseClasspathSqlLocator
    fun insertStreamDetails(stream: StreamEncodingDetailsDb): Int

    @SqlUpdate("INSERT OR IGNORE INTO streamEncodingLinks (mediaRefId, streamId) VALUES (?, ?)")
    fun insertStreamLink(mediaRefId: Int, streamId: Int)

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaReferences $JOIN_STREAMS WHERE mediaReferences.gid = ?")
    fun findByGid(gid: String): MediaReferenceDb?

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaReferences $JOIN_STREAMS WHERE mediaReferences.gid IN (<gids>)")
    fun findByGids(@BindList("gids") gids: List<String>): List<MediaReferenceDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaReferences $JOIN_STREAMS WHERE mediaReferences.contentGid = ?")
    fun findByContentGid(mediaGid: String): List<MediaReferenceDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaReferences $JOIN_STREAMS WHERE mediaReferences.contentGid IN (<gids>)")
    fun findByContentGids(@BindList("gids") gids: List<String>): List<MediaReferenceDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaReferences $JOIN_STREAMS WHERE mediaReferences.rootContentGid = ?")
    fun findByRootContentGid(mediaGid: String): List<MediaReferenceDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaReferences $JOIN_STREAMS WHERE mediaReferences.rootContentGid IN (<gids>)")
    fun findByRootContentGids(@BindList("gids") gids: List<String>): List<MediaReferenceDb>

    @UseRowReducer(MediaReferenceReducer::class)
    @SqlQuery("SELECT $MEDIAREF_COLUMNS, $STEAM_COLUMNS FROM mediaReferences $JOIN_STREAMS WHERE mediaReferences.filePath = ?")
    fun findByFilePath(filePath: String): MediaReferenceDb?

    @SqlQuery("SELECT filePath FROM mediaReferences WHERE length(mediaReferences.filePath) > 0")
    fun findAllFilePaths(): List<String>

    @SqlUpdate("DELETE FROM mediaReferences WHERE mediaReferences.contentGid = ?")
    fun deleteByContentGid(contentGid: String)

    @SqlUpdate("DELETE FROM mediaReferences WHERE mediaReferences.rootContentGid = ?")
    fun deleteByRootContentGid(rootContentGid: String)

    @SqlUpdate("DELETE FROM mediaReferences WHERE mediaReferences.hash = ?")
    fun deleteDownloadByHash(hash: String)

    class MediaReferenceReducer : LinkedHashMapRowReducer<Int, MediaReferenceDb> {
        override fun accumulate(container: MutableMap<Int, MediaReferenceDb>?, rowView: RowView?) {
            checkNotNull(container)
            checkNotNull(rowView)
            val rowId = rowView.getColumn("mediaref_id", Integer::class.javaObjectType) as Int
            var mediaRefDb = container.computeIfAbsent(rowId) { rowView.getRow(MediaReferenceDb::class.java) }
            rowView.getColumn("s_id", Integer::class.javaObjectType)?.let { joinId ->
                if (mediaRefDb.streams.none { it.id == joinId.toInt() }) {
                    val stream = rowView.getRow(StreamEncodingDetailsDb::class.java)
                    mediaRefDb = mediaRefDb.copy(streams = mediaRefDb.streams + stream)
                }
            }

            container[rowId] = mediaRefDb
        }
    }
}
