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
package anystream.presentation.login

import anystream.models.UserPublic
import anystream.models.api.CreateSessionResponse
import dev.drewhamilton.poko.Poko
import dev.zacsweers.redacted.annotations.Redacted

sealed class LoginScreenEvent {

    @Poko
    class OnServerUrlChanged(
        val serverUrl: String,
    ) : LoginScreenEvent()

    @Poko
    class OnUsernameChanged(
        val username: String,
    ) : LoginScreenEvent()

    data class OnPasswordChanged(
        @Redacted
        val password: String,
    ) : LoginScreenEvent()

    data object OnLoginSubmit : LoginScreenEvent()

    data class OnPairingStarted(
        @Redacted
        val pairingCode: String,
    ) : LoginScreenEvent()


    data class OnPairingEnded(
        @Redacted
        val pairingCode: String,
    ) : LoginScreenEvent()

    @Poko
    class OnLoginSuccess(
        val user: UserPublic,
    ) : LoginScreenEvent()

    @Poko
    class OnLoginError(
        val error: CreateSessionResponse.Error,
    ) : LoginScreenEvent()

    @Poko
    class OnServerValidated(
        val serverUrl: String,
        val result: LoginScreenModel.ServerValidation,
    ) : LoginScreenEvent()
}
