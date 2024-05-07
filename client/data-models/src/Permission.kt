/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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


@Serializable
sealed class Permission {
    @Serializable
    data object Global : Permission()

    @Serializable
    data object ViewCollection : Permission()

    @Serializable
    data object ManageCollection : Permission()

    @Serializable
    data object ManageTorrents : Permission()

    @Serializable
    data object ConfigureSystem : Permission()

    companion object {
        val all: Set<Permission> = setOf(Global, ViewCollection, ManageCollection, ManageTorrents, ConfigureSystem)

        fun check(permission: Permission, permissions: Set<Permission>): Boolean {
            return permissions.contains(permission) || permissions.contains(Global)
        }
    }
}