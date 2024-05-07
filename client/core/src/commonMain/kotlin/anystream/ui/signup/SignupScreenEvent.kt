/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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
package anystream.ui.signup

import anystream.models.User
import anystream.models.UserPublic
import anystream.models.api.CreateUserResponse

sealed class SignupScreenEvent {

    data class OnServerUrlChanged(
        val serverUrl: String,
    ) : SignupScreenEvent()

    data class OnUsernameChanged(
        val username: String,
    ) : SignupScreenEvent()

    data class OnPasswordChanged(
        val password: String,
    ) : SignupScreenEvent() {
        override fun toString(): String {
            return "OnPasswordChanged(password='***')"
        }
    }

    data class OnInviteCodeChanged(
        val inviteCode: String,
    ) : SignupScreenEvent()

    object OnSignupSubmit : SignupScreenEvent()

    data class OnSignupSuccess(
        val user: UserPublic,
    ) : SignupScreenEvent()

    data class OnSignupError(
        val error: CreateUserResponse.Error,
    ) : SignupScreenEvent()

    data class OnServerValidated(
        val serverUrl: String,
        val result: SignupScreenModel.ServerValidation,
    ) : SignupScreenEvent()
}
