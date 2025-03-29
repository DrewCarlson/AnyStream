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

import anystream.data.UserSession
import anystream.db.InviteCodeDao
import anystream.db.UserDao
import anystream.models.*
import anystream.models.api.*
import anystream.util.ObjectId
import kotlinx.datetime.Clock
import org.bouncycastle.crypto.generators.OpenBSDBCrypt
import org.bouncycastle.util.encoders.Hex
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val SALT_BYTES = 16
private const val BCRYPT_COST = 10
private const val INVITE_CODE_SIZE = 32

class UserService(
    private val queries: UserDao,
    private val inviteCodeDao: InviteCodeDao,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val pairingCodes = ConcurrentHashMap<String, PairingMessage>()

    suspend fun getUser(userId: String): User? {
        return queries.fetchUser(userId)
    }

    suspend fun getUsers(): List<User> {
        return queries.fetchUsers().toMutableList()
    }

    suspend fun deleteUser(userId: String): Boolean {
        return queries.deleteUser(userId)
    }

    suspend fun createInviteCode(userId: String, permissions: Set<Permission>): InviteCode? {
        return inviteCodeDao.createInviteCode(
            secret = Hex.toHexString(Random.nextBytes(INVITE_CODE_SIZE)),
            permissions = permissions,
            userId = userId,
        )
    }

    suspend fun getInvites(byUserId: String?): List<InviteCode> {
        return inviteCodeDao.fetchInviteCodes(byUserId)
    }

    suspend fun deleteInvite(inviteCode: String, byUserId: String?): Boolean {
        return inviteCodeDao.deleteInviteCode(inviteCode, byUserId)
    }

    suspend fun createUser(body: CreateUserBody): CreateUserResponse {
        val usernameError = when {
            body.username.isBlank() -> CreateUserResponse.UsernameError.BLANK
            body.username.length < USERNAME_LENGTH_MIN -> CreateUserResponse.UsernameError.TOO_SHORT
            body.username.length > USERNAME_LENGTH_MAX -> CreateUserResponse.UsernameError.TOO_LONG
            else -> null
        }
        val passwordError = when {
            body.password.isBlank() -> CreateUserResponse.PasswordError.BLANK
            body.password.length < PASSWORD_LENGTH_MIN -> CreateUserResponse.PasswordError.TOO_SHORT
            body.password.length > PASSWORD_LENGTH_MAX -> CreateUserResponse.PasswordError.TOO_LONG
            else -> null
        }

        if (usernameError != null || passwordError != null) {
            return CreateUserResponse.Error(usernameError, passwordError)
        }

        val username = body.username.lowercase()
        if (queries.fetchUserByUsername(username) != null) {
            return CreateUserResponse.Error(CreateUserResponse.UsernameError.ALREADY_EXISTS, null)
        }

        val inviteCode = body.inviteCode?.let { inviteCodeDao.fetchInviteCode(it) }

        // Use permissions from provided invite code if it exists
        val permissions = inviteCode?.permissions
        // If no users exist, create user 1 with GLOBAL permission
            ?: setOf(Permission.Global).takeIf { queries.countUsers() == 0 }
            // otherwise ignore creation request
            ?: return CreateUserResponse.Error(signupDisabled = true)

        val now = Clock.System.now()
        val newUser = queries.insertUser(
            User(
                id = ObjectId.next(),
                username = username,
                displayName = body.username,
                createdAt = now,
                updatedAt = now,
                passwordHash = hashPassword(body.password),
            ),
            permissions = permissions,
        ) ?: return CreateUserResponse.Error(null, null)
        if (inviteCode != null) {
            inviteCodeDao.deleteInviteCode(inviteCode.secret, null)
        }

        return CreateUserResponse.Success(newUser.toPublic(), permissions)
    }

    suspend fun updateUser(userId: String, body: UpdateUserBody): Boolean {
        val user = queries.fetchUser(userId) ?: return false

        // Attempt password verification and update first, if this fails
        // any other updates will be ignored.
        val newPassword = body.password
        val currentPassword = body.currentPassword
        val newPasswordHash =
            if (!newPassword.isNullOrBlank() && !currentPassword.isNullOrBlank()) {
                if (verifyPassword(currentPassword, user.passwordHash)) {
                    hashPassword(newPassword)
                } else {
                    return false
                }
            } else {
                null
            }

        val updatedUser = user.copy(
            displayName = body.displayName,
            passwordHash = newPasswordHash ?: user.passwordHash,
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
        secret: String,
    ): CreateSessionResponse? {
        val pairingMessage = pairingCodes.remove(pairingCode)
        val pairingSecret = (pairingMessage as? PairingMessage.Authorized)?.secret
        return if (pairingSecret == secret) {
            val user = queries.fetchUser(pairingMessage.userId) ?: return null
            val permissions = queries.fetchPermissions(checkNotNull(user.id))
            CreateSessionResponse.Success(user.toPublic(), permissions)
        } else {
            null
        }
    }

    suspend fun createSession(
        body: CreateSessionBody,
        parent: UserSession?,
    ): CreateSessionResponse? {
        if (body.username.run { isBlank() || length !in USERNAME_LENGTH_MIN..USERNAME_LENGTH_MAX }) {
            return CreateSessionResponse.Error(
                CreateSessionResponse.UsernameError.INVALID,
            )
        }

        val username = body.username.lowercase()
        if (pairingCodes.containsKey(body.password)) {
            val session =
                parent ?: return CreateSessionResponse.Error(
                    CreateSessionResponse.UsernameError.INVALID,
                )

            val user = queries.fetchUserByUsername(username) ?: return null
            return if (session.userId == user.id) {
                pairingCodes[body.password] = PairingMessage.Authorized(
                    secret = Hex.toHexString(Random.nextBytes(28)),
                    userId = session.userId,
                )
                CreateSessionResponse.Success(user.toPublic(), session.permissions)
            } else {
                pairingCodes[body.password] = PairingMessage.Failed
                CreateSessionResponse.Error(
                    passwordError = CreateSessionResponse.PasswordError.INCORRECT,
                )
            }
        }

        if (body.password.run { isBlank() || length !in PASSWORD_LENGTH_MIN..PASSWORD_LENGTH_MAX }) {
            return CreateSessionResponse.Error(
                passwordError = CreateSessionResponse.PasswordError.INVALID,
            )
        }

        val user = queries.fetchUserByUsername(username)
            ?: return CreateSessionResponse.Error(
                usernameError = CreateSessionResponse.UsernameError.NOT_FOUND,
            )
        val permissions = queries.fetchPermissions(checkNotNull(user.id)).toSet()

        return if (verifyPassword(body.password, user.passwordHash)) {
            CreateSessionResponse.Success(user.toPublic(), permissions)
        } else {
            CreateSessionResponse.Error(
                passwordError = CreateSessionResponse.PasswordError.INCORRECT,
            )
        }
    }

    fun hashPassword(password: String): String {
        require(password.length in PASSWORD_LENGTH_MIN..PASSWORD_LENGTH_MAX) {
            "Expected password to be in $PASSWORD_LENGTH_MIN..$PASSWORD_LENGTH_MAX but was ${password.length}"
        }
        val salt = Random.nextBytes(SALT_BYTES)
        return OpenBSDBCrypt.generate(password.encodeToByteArray(), salt, BCRYPT_COST)
    }

    fun verifyPassword(checkPassword: String, hashString: String): Boolean {
        return OpenBSDBCrypt.checkPassword(hashString, checkPassword.toCharArray())
    }
}
