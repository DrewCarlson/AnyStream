/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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

import anystream.db.tables.records.InviteCodeRecord
import anystream.db.tables.references.INVITE_CODE
import anystream.db.util.*
import anystream.models.InviteCode
import anystream.models.Permission
import kotlinx.coroutines.reactive.awaitFirst
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

class InviteCodeDao(
    private val db: DSLContext,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun fetchInviteCode(secret: String): InviteCode? {
        return db.selectFrom(INVITE_CODE)
            .where(INVITE_CODE.SECRET.eq(secret))
            .awaitFirstOrNullInto()
    }

    suspend fun fetchInviteCodes(byUserId: String? = null): List<InviteCode> {
        return db.selectFrom(INVITE_CODE)
            .run {
                if (byUserId == null) {
                    this
                } else {
                    where(INVITE_CODE.CREATED_BY_USER_ID.eq(byUserId))
                }
            }
            .awaitInto()
    }

    suspend fun createInviteCode(
        secret: String,
        permissions: Set<Permission>,
        userId: String,
    ): InviteCode? {
        val record = InviteCodeRecord(secret, permissions, userId)
        return db.newRecordAsync(INVITE_CODE, record)
    }

    suspend fun deleteInviteCode(secret: String, byUserId: String?): Boolean {
        return db.deleteFrom(INVITE_CODE)
            .where(INVITE_CODE.SECRET.eq(secret))
            .run {
                if (byUserId == null) {
                    this
                } else {
                    and(INVITE_CODE.CREATED_BY_USER_ID.eq(byUserId))
                }
            }
            .awaitFirst() == 1
    }
}