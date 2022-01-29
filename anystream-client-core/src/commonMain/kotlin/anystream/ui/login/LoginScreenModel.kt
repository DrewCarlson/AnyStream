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

import anystream.models.api.CreateSessionResponse
import kt.mobius.gen.UpdateSpec

@UpdateSpec(
    eventClass = LoginScreenEvent::class,
    effectClass = LoginScreenEffect::class,
)
data class LoginScreenModel(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val supportsPairing: Boolean = false,
    val pairingCode: String? = null,
    val state: State = State.IDLE,
    val serverValidation: ServerValidation = ServerValidation.VALIDATING,
    val loginError: CreateSessionResponse.Error? = null,
) {
    enum class State {
        IDLE, AUTHENTICATING, AUTHENTICATED,
    }

    enum class ServerValidation {
        VALID, INVALID, VALIDATING,
    }

    fun credentialsAreSet(): Boolean {
        return username.isNotBlank() && password.isNotBlank()
    }

    fun isServerUrlValid(): Boolean {
        return serverValidation == ServerValidation.VALID
    }

    fun isInputLocked(): Boolean {
        return state != State.IDLE
    }

    companion object {
        fun create(): LoginScreenModel {
            return LoginScreenModel()
        }

        fun create(supportsPairing: Boolean): LoginScreenModel {
            return LoginScreenModel(supportsPairing = supportsPairing)
        }

        fun create(serverUrl: String, supportsPairing: Boolean): LoginScreenModel {
            return LoginScreenModel(
                serverUrl = serverUrl,
                supportsPairing = supportsPairing,
                serverValidation = ServerValidation.VALID,
            )
        }
    }
}
