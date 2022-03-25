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
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface SearchableContentDao {

    // NOTE: IntelliJ complains about this syntax, but it is valid since SQLite ~3.7.11
    @SqlUpdate("CREATE VIRTUAL TABLE IF NOT EXISTS searchableContent USING fts5(content, type, gid)")
    fun createTable()

    @SqlUpdate("INSERT INTO searchableContent(gid, type, content) VALUES (:gid, :type, :content)")
    fun insert(gid: String, type: MediaDb.Type, content: String)

    @SqlQuery("SELECT gid FROM searchableContent WHERE content MATCH ? ORDER BY rank")
    fun search(query: String): List<String>

    @SqlQuery("SELECT gid FROM searchableContent WHERE content MATCH ? AND type = ? ORDER BY rank")
    fun search(query: String, type: MediaDb.Type): List<String>

    @SqlQuery("SELECT gid FROM searchableContent WHERE content MATCH ? AND type = ? ORDER BY rank LIMIT ?")
    fun search(query: String, type: MediaDb.Type, limit: Int): List<String>
}
