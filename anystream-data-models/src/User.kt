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
package anystream.models

import kotlinx.serialization.Serializable

const val USERNAME_LENGTH_MIN = 4
const val USERNAME_LENGTH_MAX = 12
const val PASSWORD_LENGTH_MIN = 6
const val PASSWORD_LENGTH_MAX = 64

@Serializable
data class User(
    val id: Int,
    val username: String,
    val displayName: String
)

@Serializable
sealed class Permission {
    @Serializable
    object Global : Permission() {
        override fun toString(): String = "Global"
    }

    @Serializable
    object ViewCollection : Permission() {
        override fun toString(): String = "ViewCollection"
    }

    @Serializable
    object ManageCollection : Permission() {
        override fun toString(): String = "ManageCollection"
    }

    @Serializable
    object ManageTorrents : Permission() {
        override fun toString(): String = "ManageTorrents"
    }

    @Serializable
    object ConfigureSystem : Permission() {
        override fun toString(): String = "ConfigureSystem"
    }

    companion object {
        val all: List<Permission> = listOf(Global, ViewCollection, ManageCollection, ManageTorrents, ConfigureSystem)

        fun check(permission: Permission, permissions: Set<Permission>): Boolean {
            return permissions.contains(permission) || permissions.contains(Global)
        }
    }
}

@Serializable
data class UpdateUserBody(
    val displayName: String,
    val password: String?,
    val currentPassword: String?
)
