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

import anystream.db.tables.references.METADATA_COMPANY
import anystream.db.tables.references.METADATA_GENRE
import anystream.db.tables.references.TAG
import anystream.db.util.fetchIntoType
import anystream.db.util.fetchSingleIntoType
import anystream.db.util.intoType
import anystream.models.Genre
import anystream.models.ProductionCompany
import anystream.util.ObjectId
import org.jooq.DSLContext

data class TagData(
    val name: String? = null,
    val mediaId: Int? = null,
    val tmdbId: Int? = null,
    val genreId: Int? = null,
)

class TagsDao(
    private val db: DSLContext
) {

    fun insertTag(name: String, tmdbId: Int?): String {
        val id = ObjectId.get().toString()
        db.insertInto(TAG)
            .set(TAG.ID,id )
            .set(TAG.NAME, name)
            .set(TAG.TMDB_ID, tmdbId)
            .execute()
        return id
    }

    fun insertMetadataGenreLink(mediaId: String, genreId: String) {
        db.insertInto(METADATA_GENRE)
            .set(METADATA_GENRE.METADATA_ID, mediaId)
            .set(METADATA_GENRE.GENRE_ID, genreId)
            .execute()
    }

    fun insertMetadataCompanyLink(mediaId: String, companyId: String) {
        db.insertInto(METADATA_COMPANY)
            .set(METADATA_COMPANY.METADATA_ID, mediaId)
            .set(METADATA_COMPANY.COMPANY_ID, companyId)
            .execute()
    }

    fun findGenreByTmdbId(tmdbId: Int): Genre? {
        return db.fetchOne(TAG, TAG.TMDB_ID.eq(tmdbId))
            ?.intoType()
    }

    fun findCompanyByTmdbId(tmdbId: Int): ProductionCompany? {
        return db.fetchOne(TAG, TAG.TMDB_ID.eq(tmdbId))?.intoType()
    }
}
