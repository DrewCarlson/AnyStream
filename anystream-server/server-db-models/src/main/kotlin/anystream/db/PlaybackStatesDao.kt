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

import anystream.db.model.PlaybackStateDb
import kotlinx.datetime.Instant
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface PlaybackStatesDao {

    @SqlUpdate
    @UseClasspathSqlLocator
    fun insertState(state: PlaybackStateDb, createdAt: Instant): Int

    @SqlQuery("SELECT * FROM playbackStates WHERE id = ?")
    fun findById(id: Int): PlaybackStateDb?

    @SqlQuery("SELECT * FROM playbackStates WHERE id IN (<ids>)")
    fun findByIds(@BindList("ids") ids: List<Int>): List<PlaybackStateDb>

    @SqlQuery("SELECT * FROM playbackStates WHERE gid IN (<ids>)")
    fun findByGids(@BindList("ids") ids: List<String>): List<PlaybackStateDb>

    @SqlQuery("SELECT * FROM playbackStates WHERE userId = ? ORDER BY updatedAt DESC LIMIT ?")
    fun findByUserId(userId: Int, limit: Int): List<PlaybackStateDb>

    @SqlQuery("SELECT * FROM playbackStates WHERE userId = ? AND mediaGid = ?")
    fun findByUserIdAndMediaGid(userId: Int, mediaGid: String): PlaybackStateDb?

    @SqlQuery("SELECT * FROM playbackStates WHERE userId = ? AND mediaReferenceId = ?")
    fun findByUserIdAndMediaRefGid(userId: Int, mediaRefGid: String): PlaybackStateDb?

    @SqlQuery("SELECT * FROM playbackStates WHERE userId = :userId AND mediaGid IN (<mediaGids>)")
    fun findByUserIdAndMediaGids(userId: Int, @BindList("mediaGids") mediaGids: List<String>): List<PlaybackStateDb>

    @SqlUpdate("UPDATE playbackStates SET position = :position, updatedAt = :updatedAt WHERE gid = :gid")
    fun updatePosition(gid: String, position: Double, updatedAt: Instant)

    @SqlUpdate("DELETE FROM playbackStates WHERE gid = ?")
    fun deleteByGid(gid: String)
}
