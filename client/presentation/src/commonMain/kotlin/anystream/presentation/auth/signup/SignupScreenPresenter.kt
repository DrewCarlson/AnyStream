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
package anystream.presentation.auth.signup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import anystream.client.AnyStreamClient
import anystream.models.api.CreateUserResponse
import anystream.presentation.auth.ServerValidation
import anystream.presentation.auth.signup.SignupScreenModel.State
import anystream.presentation.core.Presenter
import anystream.presentation.core.rememberEventTrigger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class SignupScreenProps(
    val inviteCode: String?,
    val serverUrl: String,
    val onServerUrlChange: (String) -> Unit,
    val onSignupComplete: () -> Unit,
    val authTypes: List<String>?,
    val serverValidation: ServerValidation,
    val oidcProviderName: String?,
)

@SingleIn(AppScope::class)
@Inject
class SignupScreenPresenter(
    private val client: AnyStreamClient,
) : Presenter<SignupScreenProps, SignupScreenModel> {
    @Composable
    override fun model(props: SignupScreenProps): SignupScreenModel {
        val scope = rememberCoroutineScope()
        var serverUrl by remember { mutableStateOf(props.serverUrl) }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var inviteCode by remember { mutableStateOf(props.inviteCode) }
        var signupError by remember { mutableStateOf<CreateUserResponse.Error?>(null) }
        var state by remember { mutableStateOf(State.IDLE) }

        val signupTrigger = scope.rememberEventTrigger(props) {
            if (state != State.IDLE || props.serverValidation != ServerValidation.VALID) {
                return@rememberEventTrigger
            }
            if (username.isBlank() || password.isBlank()) {
                return@rememberEventTrigger
            }
            state = State.AUTHENTICATING
            try {
                when (val result = client.user.createUser(username, password, inviteCode)) {
                    is CreateUserResponse.Success -> {
                        state = State.AUTHENTICATED
                        props.onSignupComplete()
                    }

                    is CreateUserResponse.Error -> {
                        state = State.IDLE
                        signupError = result
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                state = State.IDLE
            }
        }

        return SignupScreenModel(
            serverUrl = serverUrl,
            username = username,
            password = password,
            inviteCode = inviteCode.orEmpty(),
            state = state,
            serverValidation = props.serverValidation,
            signupError = signupError,
            isInviteCodeLocked = inviteCode?.isNotBlank() == true,
            onServerUrlChanged = props.onServerUrlChange,
            onUsernameChanged = { username = it },
            onPasswordChanged = { password = it },
            onInviteCodeChanged = { inviteCode = it },
            onSubmitSignup = signupTrigger::trigger,
        )
    }
}
