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

import anystream.db.tables.references.SEARCHABLE_CONTENT
import anystream.db.util.awaitFirstOrNullInto
import anystream.db.util.awaitInto
import anystream.models.MediaType
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.DSL.condition

class SearchableContentDao(
    private val db: DSLContext
) {
    @Suppress("PrivatePropertyName")
    private val RANK = DSL.field("rank")

    suspend fun search(query: String): List<String> {
        return db.select(SEARCHABLE_CONTENT.ID)
            .from(SEARCHABLE_CONTENT)
            .where(SEARCHABLE_CONTENT.CONTENT.match(query))
            .orderBy(RANK)
            .awaitInto()
    }

    suspend fun search(query: String, type: MediaType): List<String> {
        return db.select(SEARCHABLE_CONTENT.ID)
            .from(SEARCHABLE_CONTENT)
            .where(
                SEARCHABLE_CONTENT.CONTENT.match(query),
                SEARCHABLE_CONTENT.MEDIA_TYPE.eq(type)
            )
            .orderBy(RANK)
            .awaitInto()
    }

    suspend fun search(query: String, type: MediaType, limit: Int): List<String> {
        return db.select(SEARCHABLE_CONTENT.ID)
            .from(SEARCHABLE_CONTENT)
            .where(
                SEARCHABLE_CONTENT.CONTENT.match(query),
                SEARCHABLE_CONTENT.MEDIA_TYPE.eq(type)
            )
            .orderBy(RANK)
            .limit(limit)
            .awaitInto()
    }
}

private fun <X : Record> TableField<X, String?>.match(text: String): Condition {
    return condition("{0} MATCH {1}", this, text)
}
