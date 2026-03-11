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
package anystream.presentation.login

import anystream.models.api.CreateSessionResponse
import anystream.presentation.core.ScreenModel


data class LoginScreenModel(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val supportsPairing: Boolean = false,
    val pairingCode: String? = null,
    val state: State = State.IDLE,
    val authTypes: List<String>? = null,
    val serverValidation: ServerValidation = ServerValidation.VALIDATING,
    val loginError: CreateSessionResponse.Error? = null,
    val supportsPasswordAuth: Boolean = true,
    val oidcProviderName: String? = null,
    // events
    val onServerUrlChanged: (String) -> Unit = {},
    val onUsernameChanged: (String) -> Unit = {},
    val onPasswordChanged: (String) -> Unit = {},
    val onSubmitLogin: () -> Unit = {},
) : ScreenModel {
    val isInputLocked: Boolean = state != State.IDLE
    val isServerUrlValid: Boolean = serverValidation == ServerValidation.VALID

    enum class State {
        IDLE, AUTHENTICATING, AUTHENTICATED;

        val isAuthenticating: Boolean
            get() = this == AUTHENTICATING
    }

    enum class ServerValidation {
        VALID, INVALID, VALIDATING,
    }
}
