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
import anystream.db.tables.references.*
import anystream.db.util.fetchIntoType
import anystream.db.util.fetchOptionalIntoType
import anystream.db.util.intoType
import anystream.models.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.slf4j.LoggerFactory


class UserDao(
    private val db: DSLContext,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun countUsers(): Long = withContext(IO) {
        db.fetchCount(USER).toLong()
    }

    suspend fun fetchUser(userId: String): User? = withContext(IO) {
        try {
            db.fetchSingle(USER, USER.ID.eq(userId)).intoType()
        } catch (e: Throwable) {
            logger.error("Failed to fetch user '$userId'", e)
            null
        }
    }

    suspend fun fetchUserByUsername(username: String): User? = withContext(IO) {
        try {
            db.selectFrom(USER)
                .where(USER.USERNAME.eq(username.lowercase()))
                .fetchOptionalIntoType()
        } catch (e: Throwable) {
            logger.error("Failed to fetch user '$username'", e)
            null
        }
    }

    suspend fun fetchPermissions(userId: String): Set<Permission> = withContext(IO) {
        try {
            db.fetch(USER_PERMISSION, USER_PERMISSION.USER_ID.eq(userId))
                .into(UserPermission::class.java)
                .map { it.value }
                .toSet()
        } catch (e: Throwable) {
            logger.error("Failed to fetch permissions for '$userId'", e)
            emptySet()
        }
    }

    suspend fun fetchUsers(): List<User> = withContext(IO) {
        try {
            db.selectFrom(USER).fetchIntoType()
        } catch (e: Throwable) {
            logger.error("Failed to fetch users", e)
            emptyList()
        }
    }

    suspend fun insertUser(
        user: User,
        permissions: Set<Permission>,
    ): User? = withContext(IO) {
        try {
            val userId = db.newRecord(USER, user)
                .apply { store() }
                .id

            val inserts = permissions.map { permission ->
                UserPermissionRecord(userId, permission)
            }
            db.batchInsert(inserts).execute()
            db.selectFrom(USER)
                .where(USER.ID.eq(userId))
                .fetchOptionalIntoType()
        } catch (e: Throwable) {
            logger.error("Failed to insert user", e)
            null
        }
    }

    suspend fun updateUser(
        userId: String,
        user: User?,
        passwordHash: String?,
    ): Boolean = withContext(IO) {
        try {
            TODO("Not yet implemented")
        } catch (e: Throwable) {
            logger.error("Failed to update user '$userId'", e)
            false
        }
    }

    suspend fun deleteUser(userId: String): Boolean = withContext(IO) {
        try {
            db.deleteFrom(USER)
                .where(USER.ID.eq(userId))
                .execute()
            true
        } catch (e: Throwable) {
            logger.error("Failed to delete user '$userId'", e)
            false
        }
    }
}
