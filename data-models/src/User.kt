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
package anystream.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val USERNAME_LENGTH_MIN = 4
const val USERNAME_LENGTH_MAX = 12
const val PASSWORD_LENGTH_MIN = 6
const val PASSWORD_LENGTH_MAX = 64

@Serializable
data class User(
    @SerialName("_id")
    val id: String,
    val username: String,
    val displayName: String,
)

@Serializable
data class UserCredentials(
    @SerialName("_id")
    val id: String,
    val password: String,
    val salt: String,
    val permissions: Set<String>
)

object Permissions {
    const val GLOBAL = "*"
    const val VIEW_COLLECTION = "view_collection"
    const val MANAGE_COLLECTION = "manage_collection"
    const val TORRENT_MANAGEMENT = "torrent_management"
    const val CONFIGURE_SYSTEM = "configure_system"

    val all = listOf(
        GLOBAL,
        VIEW_COLLECTION,
        MANAGE_COLLECTION,
        TORRENT_MANAGEMENT,
        CONFIGURE_SYSTEM,
    )

    fun check(permission: String, permissions: Set<String>): Boolean {
        return permissions.contains(permission) || permissions.contains(GLOBAL)
    }
}

@Serializable
data class UpdateUserBody(
    val displayName: String,
    val password: String?,
    val currentPassword: String?,
)
