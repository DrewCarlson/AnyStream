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
import anystream.db.tables.records.MetadataCreditRecord
import anystream.db.tables.records.MetadataGenreRecord
import anystream.db.tables.records.TagRecord
import anystream.db.tables.references.METADATA_COMPANY
import anystream.db.tables.references.METADATA_CREDIT
import anystream.db.tables.references.METADATA_GENRE
import anystream.db.tables.references.TAG
import anystream.db.util.*
import anystream.models.CastCredit
import anystream.models.CreditType
import anystream.models.CrewCredit
import anystream.models.Genre
import anystream.models.MetadataCredit
import anystream.models.Person
import anystream.models.ProductionCompany
import anystream.models.Tag
import anystream.models.TagType
import anystream.util.ObjectId
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext

class TagsDao(
    private val db: DSLContext
) {

    suspend fun insertTag(name: String, type: TagType, tmdbId: Int?): String {
        val id = ObjectId.next()
        val record = TagRecord(id, name, tmdbId, type)
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

    suspend fun insertCredits(credits: List<MetadataCredit>) {
        db.insertInto(METADATA_CREDIT)
            .set(credits.map(::MetadataCreditRecord))
            .awaitFirstOrNull()
    }

    suspend fun findGenresForMetadata(metadataId: String): List<Genre> {
        return db.select(TAG)
            .from(TAG)
            .join(METADATA_GENRE).on(METADATA_GENRE.GENRE_ID.eq(TAG.ID))
            .where(METADATA_GENRE.METADATA_ID.eq(metadataId))
            .and(TAG.TYPE.eq(TagType.GENRE))
            .awaitInto<Tag>()
            .map {
                Genre(
                    id = it.id,
                    name = it.name,
                    tmdbId = it.tmdbId,
                )
            }
    }

    suspend fun findCompaniesForMetadata(metadataId: String): List<ProductionCompany> {
        return db.select(TAG)
            .from(TAG)
            .join(METADATA_COMPANY).on(METADATA_COMPANY.COMPANY_ID.eq(TAG.ID))
            .where(METADATA_COMPANY.METADATA_ID.eq(metadataId))
            .and(TAG.TYPE.eq(TagType.COMPANY))
            .awaitInto<Tag>()
            .map {
                ProductionCompany(
                    id = it.id,
                    name = it.name,
                    tmdbId = it.tmdbId,
                )
            }
    }

    suspend fun findCastAndCrewForMetadata(
        metadataId: String
    ): Pair<List<CastCredit>, List<CrewCredit>> {
        return findCastAndCrewForMetadata(listOf(metadataId))
    }

    suspend fun findCastAndCrewForMetadata(
        metadataIds: List<String>
    ): Pair<List<CastCredit>, List<CrewCredit>> {
        val cast = mutableListOf<CastCredit>()
        val crew = mutableListOf<CrewCredit>()
        db.select(METADATA_CREDIT, TAG)
            .from(METADATA_CREDIT)
            .join(TAG)
            .on(
                METADATA_CREDIT.PERSON_ID.eq(TAG.ID)
                    .and(TAG.TYPE.eq(TagType.PERSON))
            )
            .run {
                if (metadataIds.size == 1) {
                    where(METADATA_CREDIT.METADATA_ID.eq(metadataIds.first()))
                } else {
                    where(METADATA_CREDIT.METADATA_ID.`in`(metadataIds))
                }
            }
            .orderBy(METADATA_CREDIT.ORDER.asc())
            .fetchAsync()
            .thenApplyAsync { records ->
                records.forEach { (credit, tag) ->
                    val person = Person(
                        id = tag.id,
                        name = tag.name,
                        tmdbId = tag.tmdbId,
                    )
                    when (credit.type) {
                        CreditType.CAST -> CastCredit(
                            person = person,
                            character = credit.character!!,
                            order = credit.order!!,
                        ).run(cast::add)

                        CreditType.CREW -> CrewCredit(
                            person = person,
                            job = credit.job!!,
                        ).run(crew::add)
                    }
                }
            }
            .await()

        return Pair(cast, crew)
    }

    suspend fun findPersonByTmdbId(tmdbId: Int): Person? =
        findByTmdbId(tmdbId, TagType.PERSON)

    suspend fun findGenreByTmdbId(tmdbId: Int): Genre? =
        findByTmdbId(tmdbId, TagType.GENRE)

    suspend fun findCompanyByTmdbId(tmdbId: Int): ProductionCompany? =
        findByTmdbId(tmdbId, TagType.COMPANY)

    private suspend inline fun <reified T> findByTmdbId(tmdbId: Int, type: TagType): T? {
        return db.selectFrom(TAG)
            .where(TAG.TMDB_ID.eq(tmdbId))
            .and(TAG.TYPE.eq(type))
            .awaitFirstOrNullInto()
    }
}
