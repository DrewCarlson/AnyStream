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
data class UserPublic(
    val id: String,
    val username: String,
    val displayName: String,
)

fun User.toPublic(): UserPublic {
    return UserPublic(
        id = id,
        username = username,
        displayName = displayName,
    )
}


@Serializable
data class UpdateUserBody(
    val displayName: String,
    val password: String?,
    val currentPassword: String?,
)
