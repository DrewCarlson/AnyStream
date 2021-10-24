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

import anystream.models.*
import java.util.concurrent.ConcurrentHashMap

class TestUserServiceQueries : UserServiceQueries {

    val users = ConcurrentHashMap<String, User>()
    val userCredentials = ConcurrentHashMap<String, UserCredentials>()
    val inviteCodes = ConcurrentHashMap<String, InviteCode>()

    override suspend fun countUsers(): Long {
        return users.size.toLong()
    }

    override suspend fun fetchUser(userId: String): User? {
        return users[userId]
    }

    override suspend fun fetchUserByUsername(username: String): User? {
        return users.values.find { it.username.equals(username, true) }
    }

    override suspend fun fetchUsers(): List<User> {
        return users.values.toList()
    }

    override suspend fun insertUser(user: User): Boolean {
        if (users.containsKey(user.id)) return false // already exists
        users[user.id] = user
        return true
    }

    override suspend fun updateUser(user: User): Boolean {
        if (!users.containsKey(user.id)) return false // unknown user
        users[user.id] = user
        return true
    }

    override suspend fun deleteUser(userId: String): Boolean {
        return users.remove(userId) != null &&
                userCredentials.remove(userId) != null
    }

    override suspend fun fetchCredentials(userId: String): UserCredentials? {
        return userCredentials[userId]
    }

    override suspend fun insertCredentials(credentials: UserCredentials): Boolean {
        if (userCredentials.containsKey(credentials.id)) return false // already exists
        userCredentials[credentials.id] = credentials
        return true
    }

    override suspend fun updateCredentials(credentials: UserCredentials): Boolean {
        if (!userCredentials.containsKey(credentials.id)) return false // unknown user
        userCredentials[credentials.id] = credentials
        return true
    }

    override suspend fun fetchInviteCode(inviteCode: String, byUserId: String?): InviteCode? {
        return inviteCodes[inviteCode]?.takeIf { invite ->
            byUserId == null || invite.createdByUserId == byUserId
        }
    }

    override suspend fun fetchInviteCodes(byUserId: String?): List<InviteCode> {
        return inviteCodes.values.filter { invite ->
            byUserId == null || invite.createdByUserId == byUserId
        }
    }

    override suspend fun insertInviteCode(inviteCode: InviteCode): Boolean {
        if (inviteCodes.containsKey(inviteCode.value)) return false // already exists
        inviteCodes[inviteCode.value] = inviteCode
        return true
    }

    override suspend fun deleteInviteCode(inviteCode: String, byUserId: String?): Boolean {
        return if (byUserId == null) {
            inviteCodes.remove(inviteCode) != null
        } else {
            if (inviteCodes[inviteCode]?.createdByUserId == byUserId) {
                inviteCodes.remove(inviteCode) != null
            } else {
                false
            }
        }
    }
}