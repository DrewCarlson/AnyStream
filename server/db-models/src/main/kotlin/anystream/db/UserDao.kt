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

import anystream.db.tables.records.UserPermissionRecord
import anystream.db.tables.records.UserRecord
import anystream.db.tables.references.*
import anystream.db.util.*
import anystream.models.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.datetime.Clock
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.slf4j.LoggerFactory


class UserDao(
    private val db: DSLContext,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun countUsers(): Int {
        return db.fetchCountAsync(USER)
    }

    suspend fun fetchUser(userId: String): User? {
        return db.selectFrom(USER)
            .where(USER.ID.eq(userId))
            .awaitFirstOrNullInto()
    }

    suspend fun fetchUserByUsername(username: String): User? {
        return db.selectFrom(USER)
            .where(USER.USERNAME.eq(username.lowercase()))
            .awaitFirstOrNullInto()
    }

    suspend fun fetchPermissions(userId: String): Set<Permission> {
        return db.selectFrom(USER_PERMISSION)
            .where(USER_PERMISSION.USER_ID.eq(userId))
            .awaitInto<UserPermission>()
            .map { it.value }
            .toSet()
    }

    suspend fun fetchUsers(): List<User> {
        return db.selectFrom(USER).awaitInto()
    }

    suspend fun fetchUsers(ids: List<String>): List<User> {
        return db.selectFrom(USER)
            .where(USER.ID.`in`(ids))
            .awaitInto()
    }

    suspend fun insertUser(
        user: User,
        permissions: Set<Permission>,
    ): User {
        return db.transactionCoroutine {
            val newUser: User = db.newRecordAsync(USER, UserRecord(user))
            val inserts = permissions.map { permission ->
                UserPermissionRecord(newUser.id, permission)
            }
            db.batchInsert(inserts)
                .executeAsync()
                .await()
            newUser
        }
    }

    suspend fun updateUser(user: User): Boolean {
        return db.update(USER)
            .set(UserRecord(user.copy(updatedAt = Clock.System.now())))
            .awaitFirstOrNull() == 1
    }

    suspend fun deleteUser(userId: String): Boolean {
        return db.deleteFrom(USER)
            .where(USER.ID.eq(userId))
            .awaitFirstOrNull() == 1
    }
}
