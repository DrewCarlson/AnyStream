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
package anystream.service.user

import anystream.data.UserSession
import anystream.models.*
import anystream.models.api.*
import com.mongodb.MongoQueryException
import org.bouncycastle.crypto.generators.OpenBSDBCrypt
import org.bouncycastle.util.encoders.Hex
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val SALT_BYTES = 16
private const val BCRYPT_COST = 10

class UserService(
    private val queries: UserServiceQueries,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val pairingCodes = ConcurrentHashMap<String, PairingMessage>()

    suspend fun getUser(userId: String): User? {
        return queries.fetchUser(userId)
    }

    suspend fun getUsers(): List<User> {
        return queries.fetchUsers()
    }

    suspend fun deleteUser(userId: String): Boolean {
        return queries.deleteUser(userId)
    }

    suspend fun createInviteCode(userId: String, permissions: Set<String>): InviteCode? {
        val inviteCode = InviteCode(
            value = Hex.toHexString(Random.nextBytes(InviteCode.SIZE)),
            permissions = permissions,
            createdByUserId = userId
        )

        return if (queries.insertInviteCode(inviteCode)) inviteCode else null
    }

    suspend fun getInvites(byUserId: String?): List<InviteCode> {
        return queries.fetchInviteCodes(byUserId)
    }

    suspend fun deleteInvite(inviteCode: String, byUserId: String?): Boolean {
        return queries.deleteInviteCode(inviteCode, byUserId)
    }

    suspend fun createUser(body: CreateUserBody): CreateUserResponse? {
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
        if (queries.fetchUserByUsername(username) != null) {
            return CreateUserResponse.error(CreateUserError.UsernameError.ALREADY_EXISTS, null)
        }

        val inviteCodeString = body.inviteCode
        val inviteCode = if (inviteCodeString.isNullOrBlank()) {
            null
        } else {
            queries.fetchInviteCode(inviteCodeString, null)
        }

        if (inviteCode == null && queries.countUsers() > 0L) {
            return null
        }

        val permissions = inviteCode?.permissions ?: setOf(Permissions.GLOBAL)

        val user = User(
            id = ObjectId.get().toString(),
            username = username,
            displayName = body.username
        )

        val hashString = hashPassword(body.password)
        val credentials = UserCredentials(
            id = user.id,
            password = hashString,
            permissions = permissions
        )
        return try {
            // TODO: Ensure all or clear completed
            queries.insertUser(user)
            queries.insertCredentials(credentials)
            if (inviteCode != null) {
                queries.deleteInviteCode(inviteCode.value, null)
            }

            CreateUserResponse.success(user, credentials.permissions)
        } catch (e: MongoQueryException) {
            logger.error("Failed to insert new user", e)
            CreateUserResponse.error(null, null)
        }
    }

    suspend fun updateUser(userId: String, body: UpdateUserBody): Boolean {
        val user = queries.fetchUser(userId) ?: return false
        val credentials = queries.fetchCredentials(userId) ?: return false

        // Attempt password verification and update first, if this fails
        // any other updates will be ignored.
        val newPassword = body.password
        val currentPassword = body.currentPassword
        if (!newPassword.isNullOrBlank() && !currentPassword.isNullOrBlank()) {
            if (verifyPassword(currentPassword, credentials.password)) {
                val newCredentials = credentials.copy(password = hashPassword(newPassword))
                if (!queries.updateCredentials(newCredentials)) return false
            } else {
                return false
            }
        }

        val updatedUser = user.copy(
            displayName = body.displayName
        )
        return queries.updateUser(updatedUser)
    }

    fun getPairingMessage(pairingCode: String, registerCode: Boolean): PairingMessage? {
        return if (registerCode) {
            pairingCodes.getOrPut(pairingCode) { PairingMessage.Idle }
        } else {
            pairingCodes[pairingCode]
        }
    }

    suspend fun verifyPairingSecret(
        pairingCode: String,
        secret: String
    ): CreateSessionResponse? {
        val pairingMessage = pairingCodes.remove(pairingCode)
        val pairingSecret = (pairingMessage as? PairingMessage.Authorized)?.secret
        return if (pairingSecret == secret) {
            val user = queries.fetchUser(pairingMessage.userId) ?: return null
            val userCredentials = queries.fetchCredentials(pairingMessage.userId) ?: return null
            CreateSessionResponse.success(user, userCredentials.permissions)
        } else null
    }

    suspend fun createSession(
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

            val user = queries.fetchUserByUsername(username) ?: return null
            return if (session.userId == user.id) {
                pairingCodes[body.password] = PairingMessage.Authorized(
                    secret = Hex.toHexString(Random.nextBytes(28)),
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

        val user = queries.fetchUserByUsername(username)
            ?: return CreateSessionResponse.error(CreateSessionError.USERNAME_NOT_FOUND)
        val auth = queries.fetchCredentials(user.id) ?: return null

        return if (verifyPassword(body.password, auth.password)) {
            CreateSessionResponse.success(user, auth.permissions)
        } else {
            CreateSessionResponse.error(CreateSessionError.PASSWORD_INCORRECT)
        }
    }

    fun hashPassword(password: String): String {
        require(password.length in PASSWORD_LENGTH_MIN..PASSWORD_LENGTH_MAX) {
            "Expected password to be in ${PASSWORD_LENGTH_MIN}..${PASSWORD_LENGTH_MAX} but was ${password.length}"
        }
        val salt = Random.nextBytes(SALT_BYTES)
        return OpenBSDBCrypt.generate(password.encodeToByteArray(), salt, BCRYPT_COST)
    }

    fun verifyPassword(checkPassword: String, hashString: String): Boolean {
        return OpenBSDBCrypt.checkPassword(hashString, checkPassword.toCharArray())
    }
}
