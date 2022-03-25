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

import anystream.models.Genre
import anystream.models.ProductionCompany
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface TagsDao {

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createTable()

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createMediaGenreLinkTable()

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createMediaCompanyLinkTable()

    @SqlUpdate("INSERT OR IGNORE INTO tags (id, name, tmdbId) VALUES (null, ?, ?)")
    @GetGeneratedKeys("id")
    fun insertTag(name: String, tmdbId: Int?): Int

    @SqlUpdate("INSERT OR IGNORE INTO mediaGenres (mediaId, genreId) VALUES (?, ?)")
    fun insertMediaGenreLink(mediaId: Int, genreId: Int)

    @SqlUpdate("INSERT OR IGNORE INTO mediaCompanies (mediaId, companyId) VALUES (?, ?)")
    fun insertMediaCompanyLink(mediaId: Int, companyId: Int)

    @SqlQuery("SELECT * FROM tags WHERE tmdbId = ?")
    fun findGenreByTmdbId(tmdbId: Int): Genre?

    @SqlQuery("SELECT * FROM tags WHERE tmdbId = ?")
    fun findCompanyByTmdbId(tmdbId: Int): ProductionCompany?
}
