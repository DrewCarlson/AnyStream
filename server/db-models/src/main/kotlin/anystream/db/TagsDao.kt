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
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

data class TagData(
    val name: String? = null,
    val mediaId: Int? = null,
    val tmdbId: Int? = null,
    val genreId: Int? = null,
)

interface TagsDao {

    @SqlUpdate("INSERT OR IGNORE INTO tags (id, name, tmdbId) VALUES (null, ?, ?)")
    @GetGeneratedKeys("id")
    fun insertTag(name: String, tmdbId: Int?): Int

    @SqlBatch("INSERT OR IGNORE INTO tags (id, name, tmdbId) VALUES (null, ?, ?)")
    @GetGeneratedKeys("id")
    fun insertTag(@BindKotlin tags: List<TagData>): IntArray

    @SqlUpdate("INSERT OR IGNORE INTO metadataGenres (metadataId, genreId) VALUES (?, ?)")
    fun insertMetadataGenreLink(mediaId: Int, genreId: Int)

    @SqlBatch("INSERT OR IGNORE INTO metadataGenres (metadataId, genreId) VALUES (?, ?)")
    @GetGeneratedKeys("id")
    fun insertMetadataGenreLink(@BindKotlin tags: List<TagData>): IntArray

    @SqlUpdate("INSERT OR IGNORE INTO metadataCompanies (metadataId, companyId) VALUES (?, ?)")
    fun insertMetadataCompanyLink(mediaId: Int, companyId: Int)

    @SqlUpdate("INSERT OR IGNORE INTO metadataCompanies (metadataId, companyId) VALUES (?, ?)")
    @GetGeneratedKeys("id")
    fun insertMetadataCompanyLink(@BindKotlin tags: List<TagData>): IntArray

    @SqlQuery("SELECT * FROM tags WHERE tmdbId = ?")
    fun findGenreByTmdbId(tmdbId: Int): Genre?

    @SqlQuery("SELECT * FROM tags WHERE tmdbId = ?")
    fun findCompanyByTmdbId(tmdbId: Int): ProductionCompany?
}
