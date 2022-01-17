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

import anystream.models.*
import com.mongodb.MongoException
import com.mongodb.MongoQueryException
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.eq
import org.slf4j.LoggerFactory

class UserServiceQueriesMongo(
    mongodb: CoroutineDatabase
) : UserServiceQueries {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val users = mongodb.getCollection<User>()
    private val credentialsDb = mongodb.getCollection<UserCredentials>()
    private val inviteCodeDb = mongodb.getCollection<InviteCode>()

    override suspend fun countUsers(): Long {
        return try {
            users.countDocuments()
        } catch (e: MongoException) {
            logger.error("Failed to count user documents", e)
            -1L
        }
    }

    override suspend fun fetchUser(userId: String): User? {
        return try {
            users.findOneById(userId)
        } catch (e: MongoException) {
            logger.error("Failed to fetch user '$userId'", e)
            null
        }
    }

    override suspend fun fetchUserByUsername(username: String): User? {
        return try {
            users.findOne(User::username eq username.lowercase())
        } catch (e: MongoException) {
            logger.error("Failed to fetch user '$username'", e)
            null
        }
    }

    override suspend fun fetchUsers(): List<User> {
        return try {
            users.find().toList()
        } catch (e: MongoException) {
            logger.error("Failed to fetch users", e)
            emptyList()
        }
    }

    override suspend fun insertUser(user: User): Boolean {
        return try {
            users.insertOne(user)
            true
        } catch (e: MongoException) {
            logger.error("Failed to insert user", e)
            false
        }
    }

    override suspend fun updateUser(user: User): Boolean {
        return try {
            users.updateOne(user)
            true
        } catch (e: MongoQueryException) {
            logger.error("Failed to update user '${user.id}'", e)
            false
        }
    }

    override suspend fun deleteUser(userId: String): Boolean {
        return try {
            val result = users.deleteOneById(userId)
            credentialsDb.deleteOneById(userId)
            result.deletedCount > 0L
        } catch (e: MongoQueryException) {
            logger.error("Failed to delete user '$userId'", e)
            false
        }
    }

    override suspend fun fetchCredentials(userId: String): UserCredentials? {
        return try {
            credentialsDb.findOneById(userId)
        } catch (e: MongoException) {
            logger.error("Failed to fetch UserCredentials for '$userId'", e)
            null
        }
    }

    override suspend fun insertCredentials(credentials: UserCredentials): Boolean {
        return try {
            credentialsDb.insertOne(credentials)
            true
        } catch (e: MongoException) {
            logger.error("Failed to insert UserCredentials", e)
            false
        }
    }

    override suspend fun updateCredentials(credentials: UserCredentials): Boolean {
        return try {
            credentialsDb.updateOne(credentials).modifiedCount > 0
        } catch (e: MongoException) {
            logger.error("Failed up update UserCredentials", e)
            false
        }
    }

    override suspend fun fetchInviteCode(inviteCode: String, byUserId: String?): InviteCode? {
        return try {
            if (byUserId == null) {
                inviteCodeDb.findOneById(inviteCode)
            } else {
                inviteCodeDb.findOne(
                    InviteCode::value eq inviteCode,
                    InviteCode::createdByUserId eq byUserId,
                )
            }
        } catch (e: MongoException) {
            logger.error("Failed to fetch InviteCode", e)
            null
        }
    }

    override suspend fun fetchInviteCodes(byUserId: String?): List<InviteCode> {
        return try {
            if (byUserId == null) {
                inviteCodeDb.find().toList()
            } else {
                inviteCodeDb.find(InviteCode::createdByUserId eq byUserId).toList()
            }
        } catch (e: MongoException) {
            logger.error("Failed to fetch InviteCodes byUserId=$byUserId", e)
            emptyList()
        }
    }

    override suspend fun insertInviteCode(inviteCode: InviteCode): Boolean {
        return try {
            inviteCodeDb.insertOne(inviteCode)
            true
        } catch (e: MongoException) {
            logger.error("Failed to insert InviteCode", e)
            false
        }
    }

    override suspend fun deleteInviteCode(inviteCode: String, byUserId: String?): Boolean {
        val result = try {
            if (byUserId == null) {
                inviteCodeDb.deleteOneById(inviteCode)
            } else {
                inviteCodeDb.deleteOne(
                    InviteCode::value eq inviteCode,
                    InviteCode::createdByUserId eq byUserId
                )
            }
        } catch (e: MongoQueryException) {
            logger.error("Failed to delete InviteCode: $inviteCode", e)
            return false
        }
        return result.deletedCount > 0
    }
}
