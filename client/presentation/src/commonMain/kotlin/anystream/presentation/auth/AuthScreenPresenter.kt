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
package anystream.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import anystream.client.AnyStreamClient
import anystream.presentation.auth.login.LoginScreenPresenter
import anystream.presentation.auth.login.LoginScreenProps
import anystream.presentation.auth.signup.SignupScreenPresenter
import anystream.presentation.auth.signup.SignupScreenProps
import anystream.presentation.core.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

data class AuthScreenProps(
    val serverUrl: String,
    val authType: AuthScreenType,
    val inviteCode: String?,
    val onAuthComplete: (isNewUser: Boolean) -> Unit,
)

@SingleIn(AppScope::class)
@Inject
class AuthScreenPresenter(
    private val client: AnyStreamClient,
    private val signupPresenter: SignupScreenPresenter,
    private val loginPresenter: LoginScreenPresenter,
) : Presenter<AuthScreenProps, AuthScreenModel> {
    @Composable
    override fun model(props: AuthScreenProps): AuthScreenModel {
        var serverUrl by remember(props.serverUrl) { mutableStateOf(props.serverUrl) }
        var oidcProviderName by remember { mutableStateOf<String?>(null) }
        val authTypes by produceState<List<String>?>(null) {
            while (value == null) {
                value = try {
                    client.user.fetchAuthTypes()
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    null
                }
                if (value == null) {
                    delay(5.seconds)
                }
            }
            if ((value?.size ?: 0) > 1) {
                oidcProviderName = value?.last()
            }
        }

        val serverValidation by produceState(ServerValidation.VALIDATING, serverUrl) {
            value = try {
                if (client.core.verifyAndSetServerUrl(serverUrl)) {
                    ServerValidation.VALID
                } else {
                    ServerValidation.INVALID
                }
            } catch (_: Throwable) {
                ServerValidation.INVALID
            }
        }

        return when (props.authType) {
            AuthScreenType.LOGIN -> {
                loginPresenter.model(
                    LoginScreenProps(
                        supportsPairing = true, // TODO:
                        serverUrl = serverUrl,
                        onServerUrlChange = { serverUrl = it },
                        onLoginComplete = { props.onAuthComplete(false) },
                        authTypes = authTypes,
                        serverValidation = serverValidation,
                        oidcProviderName = oidcProviderName,
                    ),
                )
            }

            AuthScreenType.SIGNUP -> {
                signupPresenter.model(
                    SignupScreenProps(
                        inviteCode = props.inviteCode,
                        serverUrl = serverUrl,
                        onServerUrlChange = { serverUrl = it },
                        onSignupComplete = { props.onAuthComplete(true) },
                        authTypes = authTypes,
                        serverValidation = serverValidation,
                        oidcProviderName = oidcProviderName,
                    ),
                )
            }
        }
    }
}
