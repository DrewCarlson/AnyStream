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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import anystream.client.AnyStreamClient
import anystream.models.api.CreateSessionResponse
import anystream.models.api.PairingMessage
import anystream.presentation.core.Presenter
import anystream.presentation.core.rememberEventTrigger
import anystream.presentation.login.LoginScreenModel.ServerValidation
import anystream.presentation.login.LoginScreenModel.State
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.Duration.Companion.seconds


data class LoginScreenProps(
    val supportsPairing: Boolean,
    val serverUrl: String? = null,
    val onLoginComplete: () -> Unit,
)

@SingleIn(AppScope::class)
@Inject
class LoginScreenPresenter(
    private val client: AnyStreamClient,
) : Presenter<LoginScreenProps, LoginScreenModel> {
    @Composable
    override fun model(props: LoginScreenProps): LoginScreenModel {
        val scope = rememberCoroutineScope()
        var serverUrl by remember { mutableStateOf(props.serverUrl) }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var oidcProviderName by remember { mutableStateOf<String?>(null) }
        var pairingCode by remember { mutableStateOf<String?>(null) }
        var loginError by remember { mutableStateOf<CreateSessionResponse.Error?>(null) }
        var state by remember { mutableStateOf(State.IDLE) }

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
                if (client.core.verifyAndSetServerUrl(serverUrl.orEmpty())) {
                    ServerValidation.VALID
                } else {
                    ServerValidation.INVALID
                }
            } catch (_: Throwable) {
                ServerValidation.INVALID
            }
        }

        val loginTrigger = scope.rememberEventTrigger {
            if (state == State.IDLE && serverValidation != ServerValidation.VALID) {
                return@rememberEventTrigger
            }
            state = State.AUTHENTICATING
            try {
                when (val result = client.user.login(username, password)) {
                    is CreateSessionResponse.Success -> {
                        state = State.AUTHENTICATED
                        props.onLoginComplete()
                    }

                    is CreateSessionResponse.Error -> {
                        state = State.IDLE
                        loginError = result
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                state = State.IDLE
            }
        }

        LaunchedEffect(props.supportsPairing, serverUrl) {
            if (serverValidation != ServerValidation.VALID) return@LaunchedEffect
            client.user
                .createPairingSession()
                .catch { state = State.IDLE }
                .mapNotNull { message ->
                    when (message) {
                        PairingMessage.Idle -> null // waiting for remote pairing
                        is PairingMessage.Started -> {
                            pairingCode = message.pairingCode
                        }

                        is PairingMessage.Authorized -> {
                            state = State.AUTHENTICATING
                            val result = client.user
                                .createPairedSession(
                                    pairingCode = message.pairingCode,
                                    secret = message.secret
                                )

                            when (result) {
                                is CreateSessionResponse.Success -> {
                                    props.onLoginComplete()
                                }

                                is CreateSessionResponse.Error -> {
                                    state = State.IDLE
                                    loginError = result
                                }
                            }
                        }

                        PairingMessage.Failed -> {
                            state = State.IDLE
                            pairingCode = null
                        }
                    }
                }
                .collect()
        }

        return LoginScreenModel(
            serverUrl = serverUrl.orEmpty(),
            username = username,
            password = password,
            pairingCode = pairingCode,
            loginError = loginError,
            state = state,
            authTypes = authTypes,
            serverValidation = serverValidation,
            oidcProviderName = oidcProviderName,
            supportsPasswordAuth = true,
            supportsPairing = props.supportsPairing,
            onUsernameChanged = { username = it },
            onPasswordChanged = { password = it },
            onServerUrlChanged = { serverUrl = it },
            onSubmitLogin = loginTrigger::trigger,
        )
    }
}
