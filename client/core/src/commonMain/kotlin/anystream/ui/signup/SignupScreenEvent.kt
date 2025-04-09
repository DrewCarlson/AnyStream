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

import anystream.models.UserPublic
import anystream.models.api.CreateUserResponse
import dev.drewhamilton.poko.Poko
import dev.zacsweers.redacted.annotations.Redacted

sealed class SignupScreenEvent {

    @Poko
    class OnServerUrlChanged(
        val serverUrl: String,
    ) : SignupScreenEvent()

    data class OnUsernameChanged(
        @Redacted
        val username: String,
    ) : SignupScreenEvent()

    data class OnPasswordChanged(
        @Redacted
        val password: String,
    ) : SignupScreenEvent()

    data class OnInviteCodeChanged(
        @Redacted
        val inviteCode: String,
    ) : SignupScreenEvent()

    data object OnSignupSubmit : SignupScreenEvent()

    @Poko
    class OnSignupSuccess(
        val user: UserPublic,
    ) : SignupScreenEvent()

    @Poko
    class OnSignupError(
        val error: CreateUserResponse.Error,
    ) : SignupScreenEvent()

    @Poko
    class OnServerValidated(
        val serverUrl: String,
        val result: SignupScreenModel.ServerValidation,
    ) : SignupScreenEvent()
}
