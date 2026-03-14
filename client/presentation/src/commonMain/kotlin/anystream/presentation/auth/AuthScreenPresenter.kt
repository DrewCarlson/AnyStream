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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import anystream.client.AnyStreamClient
import anystream.models.ServerValidation
import anystream.models.api.AuthProviderType
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlin.collections.filterIsInstance
import kotlin.time.Duration.Companion.seconds

data class AuthScreenProps(
    val serverUrl: String,
    val authType: AuthScreenType,
    val inviteCode: String?,
    val onAuthComplete: (isNewUser: Boolean) -> Unit,
)

data class AuthSubProps(
    val hasInternalAuth: Boolean,
    val oidcProvider: AuthProviderType.Oidc?,
    val onStartOidcAuth: (() -> Unit)?,
)

@SingleIn(AppScope::class)
@Inject
class AuthScreenPresenter(
    private val client: AnyStreamClient,
    private val signupPresenter: SignupScreenPresenter,
    private val loginPresenter: LoginScreenPresenter,
    private val oidcLauncher: OidcLauncher,
) : Presenter<AuthScreenProps, AuthScreenModel> {
    @Composable
    override fun model(props: AuthScreenProps): AuthScreenModel {
        // Initial check to redirect away from auth screens if session exists
        LaunchedEffect(Unit) {
            if (client.user.isAuthenticated()) {
                props.onAuthComplete(false)
            }
        }

        var serverUrl by remember(props.serverUrl) { mutableStateOf(props.serverUrl) }
        val serverValidation by produceState(ServerValidation.VALIDATING, serverUrl) {
            value = client.core.verifyAndSetServerUrl(serverUrl)
        }
        val authProviders by produceState<List<AuthProviderType>?>(null, serverValidation) {
            if (serverValidation != ServerValidation.VALID) {
                value = null
                return@produceState
            }
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
        }
        val hasInternalAuth by remember {
            derivedStateOf {
                authProviders
                    ?.filterIsInstance<AuthProviderType.Internal>()
                    ?.firstOrNull() != null
            }
        }
        val oidcProvider by remember {
            derivedStateOf {
                authProviders
                    ?.filterIsInstance<AuthProviderType.Oidc>()
                    ?.firstOrNull()
            }
        }
        val onStartOidcAuth by produceState<(() -> Unit)?>(null, authProviders) {
            if (oidcProvider == null) return@produceState
            value = { oidcLauncher.launchOidcLogin() }
        }
        LaunchedEffect(Unit) {
            oidcLauncher
                .observeOidcResult()
                .filterIsInstance<OidcLaunchResult.LoginComplete>() // TODO: Handle oidc error type
                .onEach { result ->
                    client.user.completeOauth(result.token)
                    props.onAuthComplete(result.isNewUser)
                }.collect()
        }

        return when (props.authType) {
            AuthScreenType.LOGIN -> {
                loginPresenter.model(
                    LoginScreenProps(
                        supportsPairing = true, // TODO:
                        serverUrl = serverUrl,
                        onServerUrlChange = { serverUrl = it },
                        onLoginComplete = { props.onAuthComplete(false) },
                        serverValidation = serverValidation,
                        authSubProps = AuthSubProps(
                            hasInternalAuth = hasInternalAuth,
                            oidcProvider = oidcProvider,
                            onStartOidcAuth = onStartOidcAuth,
                        ),
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
                        serverValidation = serverValidation,
                        authSubProps = AuthSubProps(
                            hasInternalAuth = hasInternalAuth,
                            oidcProvider = oidcProvider,
                            onStartOidcAuth = onStartOidcAuth,
                        ),
                    ),
                )
            }
        }
    }
}
