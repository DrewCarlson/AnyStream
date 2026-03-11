/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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
package anystream.presentation.signup

import anystream.models.api.CreateUserResponse
import anystream.presentation.core.ScreenModel


data class SignupScreenModel(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val inviteCode: String = "",
    val state: State = State.IDLE,
    val serverValidation: ServerValidation = ServerValidation.VALIDATING,
    val signupError: CreateUserResponse.Error? = null,
    val isInviteCodeLocked: Boolean = false,
    // events
    val onServerUrlChanged: (String) -> Unit = {},
    val onUsernameChanged: (String) -> Unit = {},
    val onPasswordChanged: (String) -> Unit = {},
    val onInviteCodeChanged: (String) -> Unit = {},
    val onSubmitSignup: () -> Unit = {},
) : ScreenModel {
    val isInputLocked: Boolean = state != State.IDLE
    val isServerUrlValid: Boolean = serverValidation == ServerValidation.VALID

    enum class State {
        IDLE, AUTHENTICATING, AUTHENTICATED,
    }

    enum class ServerValidation {
        VALID, INVALID, VALIDATING,
    }
}
