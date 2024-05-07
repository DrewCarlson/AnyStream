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
package anystream.models.api

import anystream.models.Permission
import anystream.models.User
import anystream.models.UserPublic
import kotlinx.serialization.Serializable

@Serializable
data class CreateUserBody(
    val username: String,
    val password: String,
    val inviteCode: String?,
)

@Serializable
sealed class CreateUserResponse {

    @Serializable
    data class Success(
        val user: UserPublic,
        val permissions: Set<Permission>,
    ) : CreateUserResponse()

    @Serializable
    data class Error(
        val usernameError: UsernameError? = null,
        val passwordError: PasswordError? = null,
        val signupDisabled: Boolean = true,
    ) : CreateUserResponse()

    enum class PasswordError {
        TOO_SHORT, TOO_LONG, BLANK
    }

    enum class UsernameError {
        TOO_SHORT, TOO_LONG, BLANK, ALREADY_EXISTS
    }
}
