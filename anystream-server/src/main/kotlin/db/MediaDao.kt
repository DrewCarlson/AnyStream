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

import anystream.db.model.MediaDb
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface MediaDao {

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createTable()

    @SqlQuery("SELECT * FROM media")
    fun all(): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE id IN (<ids>)")
    fun findByIds(@BindList("ids") ids: List<Int>): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE id = ?")
    fun findById(mediaId: Int): MediaDb?

    @SqlQuery("SELECT * FROM media WHERE parentGid = ?")
    fun findAllByParentGid(parentGid: String): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE parentGid = ? AND mediaType = ?")
    fun findAllByParentGidAndType(parentGid: String, type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE rootGid = ?")
    fun findAllByRootGid(rootGid: String): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE rootGid = ? AND mediaType = ?")
    fun findAllByRootGidAndType(rootGid: String, type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE rootGid = ? AND parentIndex = ? AND mediaType = ?")
    fun findAllByRootGidAndParentIndexAndType(rootGid: String, parentIndex: Int, type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE gid = ? AND mediaType = ?")
    fun findByGidAndType(gid: String, type: MediaDb.Type): MediaDb?

    @SqlQuery("SELECT * FROM media WHERE gid = ? AND mediaType = ?")
    fun findAllByGidAndType(gid: String, type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE gid IN (<gids>) AND mediaType = :type")
    fun findAllByGidsAndType(@BindList("gids") gids: List<String>, type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE gid = ?")
    fun findByGid(gid: String): MediaDb?

    @SqlQuery("SELECT mediaType FROM media WHERE gid = ?")
    fun findTypeByGid(gid: String): MediaDb.Type?

    @SqlQuery("SELECT * FROM media WHERE mediaType = ?")
    fun findAllByType(type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE mediaType = ? ORDER BY createdAt DESC LIMIT ?")
    fun findByType(type: MediaDb.Type, limit: Int): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE mediaType = ? ORDER BY title")
    fun findAllByTypeSortedByTitle(type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE tmdbId = ? AND mediaType = ?")
    fun findByTmdbIdAndType(tmdbId: Int, type: MediaDb.Type): MediaDb?

    @SqlQuery("SELECT * FROM media WHERE tmdbId IN (<ids>) AND mediaType = :type")
    fun findAllByTmdbIdsAndType(@BindList("ids") tmdbId: List<Int>, type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE lower(title) LIKE lower(?) AND mediaType = ? LIMIT ?")
    fun searchByTitleAndType(title: String, type: MediaDb.Type, limit: Int): List<MediaDb>

    @SqlQuery("SELECT COUNT(id) FROM media")
    fun count(): Long

    @SqlQuery("SELECT COUNT(id) FROM media WHERE parentGid = ? AND 'index' > 0")
    fun countSeasonsForTvShow(showId: String): Int

    @SqlUpdate
    @GetGeneratedKeys("id")
    @UseClasspathSqlLocator
    fun insertMedia(media: MediaDb): Int

    @SqlUpdate("DELETE FROM media WHERE id = ?")
    fun deleteById(mediaId: Int)

    @SqlUpdate("DELETE FROM media WHERE gid = ?")
    fun deleteByGid(mediaId: String)

    @SqlUpdate("DELETE FROM media WHERE rootGid = ?")
    fun deleteByRootGid(rootMediaId: String)
}
