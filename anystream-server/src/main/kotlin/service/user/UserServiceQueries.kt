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

interface UserServiceQueries {
    suspend fun countUsers(): Long
    suspend fun fetchUser(userId: String): User?
    suspend fun fetchUserByUsername(username: String): User?
    suspend fun fetchUsers(): List<User>
    suspend fun insertUser(user: User): Boolean
    suspend fun updateUser(user: User): Boolean
    suspend fun deleteUser(userId: String): Boolean

    suspend fun fetchCredentials(userId: String): UserCredentials?
    suspend fun insertCredentials(credentials: UserCredentials): Boolean
    suspend fun updateCredentials(credentials: UserCredentials): Boolean

    suspend fun fetchInviteCode(inviteCode: String, byUserId: String?): InviteCode?
    suspend fun fetchInviteCodes(byUserId: String?): List<InviteCode>
    suspend fun insertInviteCode(inviteCode: InviteCode): Boolean
    suspend fun deleteInviteCode(inviteCode: String, byUserId: String?): Boolean
}
