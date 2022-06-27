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
package anystream.ui.login

import anystream.models.User
import anystream.models.api.CreateSessionResponse

sealed class LoginScreenEvent {
    data class OnServerUrlChanged(
        val serverUrl: String
    ) : LoginScreenEvent()

    data class OnUsernameChanged(
        val username: String
    ) : LoginScreenEvent()

    data class OnPasswordChanged(
        val password: String
    ) : LoginScreenEvent() {
        override fun toString(): String {
            return "OnPasswordChanged(password='***')"
        }
    }

    object OnLoginSubmit : LoginScreenEvent()

    data class OnPairingStarted(
        val pairingCode: String
    ) : LoginScreenEvent()

    data class OnPairingEnded(
        val pairingCode: String
    ) : LoginScreenEvent()

    data class OnLoginSuccess(
        val user: User
    ) : LoginScreenEvent()

    data class OnLoginError(
        val error: CreateSessionResponse.Error
    ) : LoginScreenEvent()

    data class OnServerValidated(
        val serverUrl: String,
        val result: LoginScreenModel.ServerValidation
    ) : LoginScreenEvent()
}
