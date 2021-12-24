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

import anystream.db.model.PermissionDb
import anystream.db.model.UserDb
import anystream.models.*

interface UserServiceQueries {
    suspend fun countUsers(): Long
    suspend fun fetchUser(userId: Int): UserDb?
    suspend fun fetchUserByUsername(username: String): UserDb?
    suspend fun fetchPermissions(userId: Int): Set<PermissionDb>
    suspend fun fetchUsers(): List<UserDb>
    suspend fun createUser(user: User, passwordHash: String, permissions: Set<Permission>): UserDb?
    suspend fun updateUser(userId: Int, user: User?, passwordHash: String?): Boolean
    suspend fun deleteUser(userId: Int): Boolean

    suspend fun fetchInviteCode(secret: String, byUserId: Int?): InviteCode?
    suspend fun fetchInviteCodes(byUserId: Int?): List<InviteCode>
    suspend fun createInviteCode(secret: String, permissions: Set<Permission>, userId: Int): InviteCode?
    suspend fun deleteInviteCode(secret: String, byUserId: Int?): Boolean
}
