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
package anystream.service.user

import anystream.db.InvitesDao
import anystream.db.PermissionsDao
import anystream.db.UsersDao
import anystream.db.model.PermissionDb
import anystream.db.model.UserDb
import anystream.models.*
import kotlinx.datetime.Clock
import org.jdbi.v3.core.JdbiException
import org.slf4j.LoggerFactory

class UserServiceQueriesJdbi(
    private val usersDao: UsersDao,
    private val permissionsDao: PermissionsDao,
    private val invitesDao: InvitesDao,
) : UserServiceQueries {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun countUsers(): Long {
        return try {
            usersDao.count()
        } catch (e: JdbiException) {
            logger.error("Failed to count user documents", e)
            -1L
        }
    }

    override suspend fun fetchUser(userId: Int): UserDb? {
        return try {
            usersDao.findById(userId)
        } catch (e: JdbiException) {
            logger.error("Failed to fetch user '$userId'", e)
            null
        }
    }

    override suspend fun fetchUserByUsername(username: String): UserDb? {
        return try {
            usersDao.findByUsername(username.lowercase())
        } catch (e: JdbiException) {
            logger.error("Failed to fetch user '$username'", e)
            null
        }
    }

    override suspend fun fetchPermissions(userId: Int): Set<PermissionDb> {
        return try {
            permissionsDao.allForUser(userId).toSet()
        } catch (e: JdbiException) {
            logger.error("Failed to fetch permissions for '$userId'", e)
            emptySet()
        }
    }

    override suspend fun fetchUsers(): List<UserDb> {
        return try {
            usersDao.all()
        } catch (e: JdbiException) {
            logger.error("Failed to fetch users", e)
            emptyList()
        }
    }

    override suspend fun createUser(
        user: User,
        passwordHash: String,
        permissions: Set<Permission>,
    ): UserDb? {
        return try {
            val id = usersDao.insertUser(user, passwordHash, Clock.System.now())
            permissions.forEach { permission ->
                permissionsDao.insertPermission(id, permission)
            }
            usersDao.findById(id)
        } catch (e: JdbiException) {
            logger.error("Failed to insert user", e)
            null
        }
    }

    override suspend fun updateUser(
        userId: Int,
        user: User?,
        passwordHash: String?,
    ): Boolean {
        return try {
            TODO("Not yet implemented")
        } catch (e: JdbiException) {
            logger.error("Failed to update user '$userId'", e)
            false
        }
    }

    override suspend fun deleteUser(userId: Int): Boolean {
        return try {
            usersDao.deleteById(userId)
            true
        } catch (e: JdbiException) {
            logger.error("Failed to delete user '$userId'", e)
            false
        }
    }

    override suspend fun fetchInviteCode(secret: String, byUserId: Int?): InviteCode? {
        return try {
            if (byUserId == null) {
                invitesDao.findBySecret(secret)
            } else {
                invitesDao.findBySecretForUser(secret, byUserId)
            }
        } catch (e: JdbiException) {
            logger.error("Failed to fetch InviteCode", e)
            null
        }
    }

    override suspend fun fetchInviteCodes(byUserId: Int?): List<InviteCode> {
        return try {
            if (byUserId == null) {
                invitesDao.all()
            } else {
                invitesDao.allForUser(byUserId)
            }
        } catch (e: JdbiException) {
            logger.error("Failed to fetch InviteCodes byUserId=$byUserId", e)
            emptyList()
        }
    }

    override suspend fun createInviteCode(
        secret: String,
        permissions: Set<Permission>,
        userId: Int,
    ): InviteCode? {
        return try {
            val id = invitesDao.createInviteCode(secret, permissions, userId)
            invitesDao.findById(id)
        } catch (e: JdbiException) {
            logger.error("Failed to insert InviteCode", e)
            null
        }
    }

    override suspend fun deleteInviteCode(secret: String, byUserId: Int?): Boolean {
        return try {
            if (byUserId == null) {
                invitesDao.deleteBySecret(secret)
            } else {
                invitesDao.deleteBySecretForUser(secret, byUserId)
            }
            true
        } catch (e: JdbiException) {
            logger.error("Failed to delete InviteCode: $secret", e)
            false
        }
    }
}
