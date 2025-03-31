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

import anystream.db.tables.references.SESSION
import anystream.db.util.awaitFirstOrNullInto
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext

class SessionsDao(
    private val db: DSLContext
) {

    suspend fun find(id: String): String? {
        return db.select(SESSION.DATA)
            .from(SESSION)
            .where(SESSION.ID.eq(id))
            .awaitFirstOrNullInto()
    }

    suspend fun insertOrUpdate(id: String, userId: String, data: String) {
        db.insertInto(SESSION, SESSION.ID, SESSION.USER_ID, SESSION.DATA)
            .values(id, userId, data)
            .onDuplicateKeyUpdate()
            .set(SESSION.DATA, data)
            .where(SESSION.ID.eq(id))
            .awaitFirstOrNull()
    }

    suspend fun delete(id: String) {
        db.deleteFrom(SESSION)
            .where(SESSION.ID.eq(id))
            .awaitFirstOrNull()
    }
}
