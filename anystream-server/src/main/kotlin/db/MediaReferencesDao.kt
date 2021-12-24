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
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface MediaReferencesDao {

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createTable()

    @SqlQuery("SELECT * FROM mediaReferences")
    fun all(): List<MediaReferenceDb>

    @SqlUpdate
    @UseClasspathSqlLocator
    fun insertReference(ref: MediaReferenceDb): Int

    @SqlQuery("SELECT * FROM mediaReferences WHERE gid = ?")
    fun findByGid(gid: String): MediaReferenceDb?

    @SqlQuery("SELECT * FROM mediaReferences WHERE contentGid = ?")
    fun findByContentGid(mediaGid: String): List<MediaReferenceDb>

    @SqlQuery("SELECT * FROM mediaReferences WHERE contentGid IN (<gids>)")
    fun findByContentGids(@BindList("gids") gids: List<String>): List<MediaReferenceDb>

    @SqlQuery("SELECT * FROM mediaReferences WHERE rootContentGid = ?")
    fun findByRootContentGid(mediaGid: String): List<MediaReferenceDb>

    @SqlQuery("SELECT * FROM mediaReferences WHERE rootContentGid IN (<gids>)")
    fun findByRootContentGids(@BindList("gids") gids: List<String>): List<MediaReferenceDb>

    @SqlQuery("SELECT * FROM mediaReferences WHERE filePath = ?")
    fun findByFilePath(filePath: String): MediaReferenceDb?

    @SqlQuery("SELECT filePath FROM mediaReferences WHERE length(filePath) > 0")
    fun findAllFilePaths(): List<String>

    @SqlUpdate("DELETE FROM mediaReferences WHERE contentGid = ?")
    fun deleteByContentGid(contentGid: String)

    @SqlUpdate("DELETE FROM mediaReferences WHERE rootContentGid = ?")
    fun deleteByRootContentGid(rootContentGid: String)

    @SqlUpdate("DELETE FROM mediaReferences WHERE hash = ?")
    fun deleteDownloadByHash(hash: String)
}
