/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.service

import anystream.data.UserSession
import anystream.models.*
import anystream.models.api.*
import com.mongodb.MongoException
import com.mongodb.MongoQueryException
import org.bouncycastle.crypto.generators.OpenBSDBCrypt
import org.bouncycastle.util.encoders.Hex
import org.bson.types.ObjectId
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.replaceOne
import org.litote.kmongo.eq
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

typealias PasswordString = String
typealias SaltString = String

private const val SALT_BYTES = 16
private const val BCRYPT_COST = 10

interface UserService {

    companion object {
        fun create(mongodb: CoroutineDatabase): UserService {
            return UserServiceImpl(mongodb)
        }

        fun createForTest(): UserService = TestUserService
    }

    suspend fun getUsers(): List<User>

    suspend fun getUser(userId: String): User?

    suspend fun deleteUser(userId: String): Boolean

    suspend fun createUser(body: CreateUserBody): CreateUserResponse?

    suspend fun updateUser(userId: String, body: UpdateUserBody): Boolean

    suspend fun createSession(
        body: CreateSessionBody,
        parent: UserSession? = null,
    ): CreateSessionResponse?

    fun getPairingMessage(pairingCode: String, registerCode: Boolean): PairingMessage?

    suspend fun verifyPairingSecret(pairingCode: String, secret: String): CreateSessionResponse?

    suspend fun createInviteCode(userId: String, permissions: Set<String>): InviteCode?

    suspend fun getInvites(byUserId: String? = null): List<InviteCode>

    suspend fun deleteInvite(inviteCode: String, byUserId: String? = null): Boolean

    fun hashPassword(password: String): Pair<PasswordString, SaltString> {
        require(password.length in PASSWORD_LENGTH_MIN..PASSWORD_LENGTH_MAX) {
            "Expected password to be in ${PASSWORD_LENGTH_MIN}..${PASSWORD_LENGTH_MAX} but was ${password.length}"
        }
        val salt = Random.nextBytes(SALT_BYTES)
        val hashedBytes = OpenBSDBCrypt.generate(password.encodeToByteArray(), salt, BCRYPT_COST)
        return hashedBytes to Hex.encode(salt).decodeToString()
    }

    fun verifyPassword(checkPassword: String, hashedPassword: String): Boolean {
        return OpenBSDBCrypt.checkPassword(hashedPassword, checkPassword.toCharArray())
    }
}

private class UserServiceImpl(
    mongodb: CoroutineDatabase
) : UserService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val users = mongodb.getCollection<User>()
    private val credentialsDb = mongodb.getCollection<UserCredentials>()
    private val inviteCodeDb = mongodb.getCollection<InviteCode>()
    private val pairingCodes = ConcurrentHashMap<String, PairingMessage>()

    override suspend fun getUser(userId: String): User? {
        return try {
            users.findOneById(userId)
        } catch (e: MongoException) {
            logger.error("Failed to load user with id '$userId'", e)
            null
        }
    }

    override suspend fun getUsers(): List<User> {
        return try {
            users.find().toList()
        } catch (e: MongoException) {
            logger.error("Failed to load users list", e)
            emptyList()
        }
    }

    override suspend fun deleteUser(userId: String): Boolean {
        val result = users.deleteOneById(userId)
        credentialsDb.deleteOneById(userId)
        return result.deletedCount > 0L
    }

    override suspend fun createInviteCode(userId: String, permissions: Set<String>): InviteCode? {
        val inviteCode = InviteCode(
            value = Hex.toHexString(Random.nextBytes(InviteCode.SIZE)),
            permissions = permissions,
            createdByUserId = userId
        )

        return try {
            inviteCodeDb.insertOne(inviteCode)
            inviteCode
        } catch (e: MongoQueryException) {
            logger.error("Failed to save InviteCode userId=$userId", e)
            null
        }
    }

    override suspend fun getInvites(byUserId: String?): List<InviteCode> {
        return try {
            if (byUserId == null) {
                inviteCodeDb.find().toList()
            } else {
                inviteCodeDb
                    .find(InviteCode::createdByUserId eq byUserId)
                    .toList()
            }
        } catch (e: MongoQueryException) {
            logger.error("Failed to load InviteCodes byUserId=$byUserId", e)
            emptyList()
        }
    }

    override suspend fun deleteInvite(inviteCode: String, byUserId: String?): Boolean {
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

    override suspend fun createUser(body: CreateUserBody): CreateUserResponse? {
        val usernameError = when {
            body.username.isBlank() -> CreateUserError.UsernameError.BLANK
            body.username.length < USERNAME_LENGTH_MIN -> CreateUserError.UsernameError.TOO_SHORT
            body.username.length > USERNAME_LENGTH_MAX -> CreateUserError.UsernameError.TOO_LONG
            else -> null
        }
        val passwordError = when {
            body.password.isBlank() -> CreateUserError.PasswordError.BLANK
            body.password.length < PASSWORD_LENGTH_MIN -> CreateUserError.PasswordError.TOO_SHORT
            body.password.length > PASSWORD_LENGTH_MAX -> CreateUserError.PasswordError.TOO_LONG
            else -> null
        }

        if (usernameError != null || passwordError != null) {
            return CreateUserResponse.error(usernameError, passwordError)
        }

        val username = body.username.lowercase()
        if (users.findOne(User::username eq username) != null) {
            return CreateUserResponse.error(
                CreateUserError.UsernameError.ALREADY_EXISTS,
                null
            )
        }

        val id = body.inviteCode
        val inviteCode = if (id.isNullOrBlank()) {
            null
        } else {
            inviteCodeDb.findOneById(id)
        }

        if (inviteCode == null && users.countDocuments() > 0L) {
            return null
        }

        val permissions = inviteCode?.permissions ?: setOf(Permissions.GLOBAL)

        val user = User(
            id = ObjectId.get().toString(),
            username = username,
            displayName = body.username
        )

        val (hashedPassword, salt) = hashPassword(body.password)
        val credentials = UserCredentials(
            id = user.id,
            password = hashedPassword,
            salt = salt,
            permissions = permissions
        )
        return try {
            // TODO: Ensure all or clear completed
            users.insertOne(user)
            credentialsDb.insertOne(credentials)
            if (inviteCode != null) {
                inviteCodeDb.deleteOneById(inviteCode.value)
            }

            CreateUserResponse.success(user, credentials.permissions)
        } catch (e: MongoQueryException) {
            logger.error("Failed to insert new user", e)
            CreateUserResponse.error(null, null)
        }
    }

    override suspend fun updateUser(userId: String, body: UpdateUserBody): Boolean {
        val user = users.findOneById(userId) ?: return false
        val credentials = credentialsDb.findOneById(userId) ?: return false
        val updatedUser = user.copy(
            displayName = body.displayName
        )

        try {
            users.updateOneById(userId, updatedUser)
        } catch (e: MongoQueryException) {
            logger.error("Failed to update user '$userId'", e)
            return false
        }

        val currentPassword = body.currentPassword
        val newPassword = body.password
        if (!newPassword.isNullOrBlank() && !currentPassword.isNullOrBlank()) {
            if (verifyPassword(currentPassword, credentials.password)) {
                val (newHashedPassword, salt) = hashPassword(newPassword)
                val newCredentials = credentials.copy(
                    salt = salt,
                    password = newHashedPassword,
                )
                credentialsDb.replaceOne(newCredentials)
            } else {
                return false
            }
        }
        return true
    }

    override fun getPairingMessage(pairingCode: String, registerCode: Boolean): PairingMessage? {
        return if (registerCode) {
            pairingCodes.getOrPut(pairingCode) { PairingMessage.Idle }
        } else {
            pairingCodes[pairingCode]
        }
    }

    override suspend fun verifyPairingSecret(
        pairingCode: String,
        secret: String
    ): CreateSessionResponse? {
        val pairingMessage = pairingCodes.remove(pairingCode)
        return if (pairingMessage == null || pairingMessage !is PairingMessage.Authorized) {
            null
        } else {
            if (pairingMessage.secret == secret) {
                val user = users.findOneById(pairingMessage.userId)!!
                val userCredentials = credentialsDb.findOneById(pairingMessage.userId)!!
                CreateSessionResponse.success(
                    user,
                    userCredentials.permissions
                )
            } else null
        }
    }

    override suspend fun createSession(
        body: CreateSessionBody,
        parent: UserSession?
    ): CreateSessionResponse? {
        if (body.username.run { isBlank() || length !in USERNAME_LENGTH_MIN..USERNAME_LENGTH_MAX }) {
            return CreateSessionResponse.error(CreateSessionError.USERNAME_INVALID)
        }

        val username = body.username.lowercase()
        if (pairingCodes.containsKey(body.password)) {
            val session =
                parent ?: return CreateSessionResponse.error(CreateSessionError.USERNAME_INVALID)

            val user = users.findOne(User::username eq username) ?: return null
            return if (session.userId == user.id) {
                pairingCodes[body.password] = PairingMessage.Authorized(
                    secret = Random.nextBytes(28).toUtf8Hex(),
                    userId = session.userId
                )
                CreateSessionResponse.success(user, session.permissions)
            } else {
                pairingCodes[body.password] = PairingMessage.Failed
                CreateSessionResponse.error(CreateSessionError.PASSWORD_INCORRECT)
            }
        }

        if (body.password.run { isBlank() || length !in PASSWORD_LENGTH_MIN..PASSWORD_LENGTH_MAX }) {
            return CreateSessionResponse.error(CreateSessionError.PASSWORD_INVALID)
        }

        val user = users.findOne(User::username eq username)
            ?: return CreateSessionResponse.error(CreateSessionError.USERNAME_NOT_FOUND)
        val auth = credentialsDb.findOne(UserCredentials::id eq user.id) ?: return null

        return if (verifyPassword(body.password, auth.password)) {
            CreateSessionResponse.success(user, auth.permissions)
        } else {
            CreateSessionResponse.error(CreateSessionError.PASSWORD_INCORRECT)
        }
    }
}

private fun ByteArray.toUtf8Hex(): String =
    run(Hex::encode).toString(Charsets.UTF_8)

private object TestUserService : UserService {

    override fun getPairingMessage(
        pairingCode: String,
        registerCode: Boolean,
    ): PairingMessage? = null

    override suspend fun verifyPairingSecret(
        pairingCode: String,
        secret: String,
    ): CreateSessionResponse? = null

    override suspend fun createInviteCode(
        userId: String,
        permissions: Set<String>
    ): InviteCode? = null

    override suspend fun getUsers(): List<User> = emptyList()

    override suspend fun getUser(userId: String): User? = null

    override suspend fun deleteUser(userId: String): Boolean = false

    override suspend fun createUser(body: CreateUserBody): CreateUserResponse? = null

    override suspend fun createSession(
        body: CreateSessionBody,
        parent: UserSession?,
    ): CreateSessionResponse? = null

    override suspend fun deleteInvite(inviteCode: String, byUserId: String?): Boolean =
        false

    override suspend fun getInvites(byUserId: String?): List<InviteCode> = emptyList()

    override suspend fun updateUser(userId: String, body: UpdateUserBody): Boolean =
        false
}