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

import anystream.db.tables.records.MetadataCompanyRecord
import anystream.db.tables.records.MetadataGenreRecord
import anystream.db.tables.records.TagRecord
import anystream.db.tables.references.METADATA_COMPANY
import anystream.db.tables.references.METADATA_GENRE
import anystream.db.tables.references.TAG
import anystream.db.util.*
import anystream.models.Genre
import anystream.models.ProductionCompany
import anystream.models.Tag
import anystream.util.ObjectId
import kotlinx.coroutines.reactive.awaitFirstOrNull
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

    suspend fun insertTag(name: String, tmdbId: Int?): String {
        val id = ObjectId.next()
        val record = TagRecord(id, name, tmdbId)
        val newTag: Tag = db.newRecordAsync(TAG, record)
        return newTag.id
    }

    suspend fun insertMetadataGenreLink(mediaId: String, genreId: String) {
        db.insertInto(METADATA_GENRE)
            .set(MetadataGenreRecord(mediaId, genreId))
            .awaitFirstOrNull()
    }

    suspend fun insertMetadataCompanyLink(mediaId: String, companyId: String) {
        db.insertInto(METADATA_COMPANY)
            .set(MetadataCompanyRecord(mediaId, companyId))
            .awaitFirstOrNull()
    }

    suspend fun findGenreByTmdbId(tmdbId: Int): Genre? = findByTmdbId(tmdbId)

    suspend fun findCompanyByTmdbId(tmdbId: Int): ProductionCompany? = findByTmdbId(tmdbId)

    private suspend inline fun <reified T> findByTmdbId(tmdbId: Int): T? {
        return db.selectFrom(TAG)
            .where(TAG.TMDB_ID.eq(tmdbId))
            .awaitFirstOrNullInto()
    }
}
