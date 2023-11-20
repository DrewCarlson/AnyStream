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
import anystream.ui.login.LoginScreenModel.ServerValidation
import kt.mobius.test.NextMatchers.hasEffects
import kt.mobius.test.NextMatchers.hasModel
import kt.mobius.test.NextMatchers.hasNoEffects
import kt.mobius.test.NextMatchers.hasNothing
import kt.mobius.test.UpdateSpec
import kt.mobius.test.UpdateSpec.Companion.assertThatNext
import kotlin.test.Test

class LoginScreenUpdateTests {

    private val defaultModel = LoginScreenModel.create()

    @Test
    fun test_OnServerValidated_WithPairingDisabled_DoesNotStartPairing() {
        val testServerUrl = "https://test.url"
        val startModel = LoginScreenModel.create(supportsPairing = false)
        UpdateSpec(LoginScreenUpdate)
            .given(startModel)
            .whenEvents(
                LoginScreenEvent.OnServerUrlChanged(testServerUrl),
                LoginScreenEvent.OnServerValidated(testServerUrl, ServerValidation.VALID),
            )
            .then(assertThatNext(hasNoEffects()))
    }

    @Test
    fun test_OnServerValidated_WithPairingEnabled_StartsPairing() {
        val testServerUrl = "https://test.url"
        val startModel = LoginScreenModel.create(supportsPairing = true)
        UpdateSpec(LoginScreenUpdate)
            .given(startModel)
            .whenEvents(
                LoginScreenEvent.OnServerUrlChanged(testServerUrl),
                LoginScreenEvent.OnServerValidated(testServerUrl, ServerValidation.VALID),
            )
            .then(assertThatNext(hasEffects(LoginScreenEffect.PairingSession(testServerUrl))))
    }

    @Test
    fun test_OnServerValidated_WithMatchingUrl_UpdatesValidationState() {
        val testServerUrl = "https://test.url"
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel)
            .whenEvents(
                LoginScreenEvent.OnServerUrlChanged(testServerUrl),
                LoginScreenEvent.OnServerValidated(testServerUrl, ServerValidation.VALID),
            )
            .then(
                assertThatNext(
                    hasModel(
                        defaultModel.copy(
                            serverUrl = testServerUrl,
                            serverValidation = ServerValidation.VALID,
                        ),
                    ),
                ),
            )
    }

    @Test
    fun test_OnServerValidated_WithNotMatchingUrl_DoesNothing() {
        val testServerUrl = "https://test.url"
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel)
            .whenEvents(
                LoginScreenEvent.OnServerUrlChanged(testServerUrl),
                LoginScreenEvent.OnServerValidated("not-matching", ServerValidation.VALID),
            )
            .then(assertThatNext(hasNothing()))
    }

    @Test
    fun test_OnServerValidated_WhenStateIsNotIdle_DoesNothing() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALIDATING,
        )
        val testModels = listOf(
            startModel.copy(state = LoginScreenModel.State.AUTHENTICATING),
            startModel.copy(state = LoginScreenModel.State.AUTHENTICATED),
        )

        testModels.forEach { testModel ->
            UpdateSpec(LoginScreenUpdate)
                .given(testModel)
                .whenEvent(LoginScreenEvent.OnServerValidated(testServerUrl, ServerValidation.VALID))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnServerUrlChanged_SetsModelUrl_And_ValidatesUrl() {
        val testServerUrl = "https://test.url"
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel)
            .whenEvent(LoginScreenEvent.OnServerUrlChanged(testServerUrl))
            .then(
                assertThatNext(
                    hasModel(
                        defaultModel.copy(
                            serverUrl = testServerUrl,
                            serverValidation = ServerValidation.VALIDATING,
                        ),
                    ),
                    hasEffects(LoginScreenEffect.ValidateServerUrl(testServerUrl)),
                ),
            )
    }

    @Test
    fun test_OnServerUrlChanged_CancelsPairingSession() {
        val testServerUrl = "https://test.url"
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel.copy(pairingCode = "test-pairing-code"))
            .whenEvent(LoginScreenEvent.OnServerUrlChanged(testServerUrl))
            .then(
                assertThatNext(
                    hasModel(
                        defaultModel.copy(
                            serverUrl = testServerUrl,
                            pairingCode = null,
                        ),
                    ),
                    hasEffects(LoginScreenEffect.PairingSession("", cancel = true)),
                ),
            )
    }

    @Test
    fun test_OnServerUrlChanged_WhenNotIdle_DoesNothing() {
        val testServerUrl = "https://test.url"
        val testModels = listOf(
            defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATING),
            defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATED),
        )
        testModels.forEach { testModel ->
            UpdateSpec(LoginScreenUpdate)
                .given(testModel)
                .whenEvent(LoginScreenEvent.OnServerUrlChanged(testServerUrl))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnLoginSubmit_WhenNotIdle_DoesNothing() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALID,
            username = "test",
            password = "test",
        )
        val testModels = listOf(
            startModel.copy(state = LoginScreenModel.State.AUTHENTICATING),
            startModel.copy(state = LoginScreenModel.State.AUTHENTICATED),
        )
        testModels.forEach { testModel ->
            UpdateSpec(LoginScreenUpdate)
                .given(testModel)
                .whenEvent(LoginScreenEvent.OnLoginSubmit)
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnLoginSubmit_WithCredentialsAndServerUrl_SendsLoginRequest() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALID,
            username = "test",
            password = "test",
        )
        UpdateSpec(LoginScreenUpdate)
            .given(startModel)
            .whenEvent(LoginScreenEvent.OnLoginSubmit)
            .then(
                assertThatNext(
                    hasModel(startModel.copy(state = LoginScreenModel.State.AUTHENTICATING)),
                    hasEffects(LoginScreenEffect.Login(startModel.username, startModel.password, startModel.serverUrl)),
                ),
            )
    }

    @Test
    fun test_OnLoginSubmit_WithInvalidServerUrl_DoesNothing() {
        val startModel = defaultModel.copy(
            serverUrl = "",
            serverValidation = ServerValidation.INVALID,
            username = "test",
            password = "test",
        )
        UpdateSpec(LoginScreenUpdate)
            .given(startModel)
            .whenEvent(LoginScreenEvent.OnLoginSubmit)
            .then(assertThatNext(hasNothing()))
    }

    @Test
    fun test_OnLoginSubmit_WithValidatingServerUrl_DoesNothing() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALIDATING,
            username = "test",
            password = "test",
        )
        UpdateSpec(LoginScreenUpdate)
            .given(startModel)
            .whenEvent(LoginScreenEvent.OnLoginSubmit)
            .then(assertThatNext(hasNothing()))
    }

    @Test
    fun test_OnLoginSubmit_WithoutCredentials_DoesNothing() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALID,
            username = "",
            password = "",
        )
        UpdateSpec(LoginScreenUpdate)
            .given(startModel)
            .whenEvent(LoginScreenEvent.OnLoginSubmit)
            .then(assertThatNext(hasNothing()))
    }

    @Test
    fun test_OnUsernameChanged_WhenIdle_UpdatesUsername() {
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel)
            .whenEvent(LoginScreenEvent.OnUsernameChanged("test"))
            .then(
                assertThatNext(
                    hasNoEffects(),
                    hasModel(defaultModel.copy(username = "test")),
                ),
            )
    }

    @Test
    fun test_OnUsernameChanged_WhenNotIdle_DoesNothing() {
        val testModels = listOf(
            defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATING),
            defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATED),
        )
        testModels.forEach { testModel ->
            UpdateSpec(LoginScreenUpdate)
                .given(testModel)
                .whenEvent(LoginScreenEvent.OnUsernameChanged("test"))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnPasswordChanged_WhenIdle_UpdatesPassword() {
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel)
            .whenEvent(LoginScreenEvent.OnPasswordChanged("test"))
            .then(
                assertThatNext(
                    hasNoEffects(),
                    hasModel(defaultModel.copy(password = "test")),
                ),
            )
    }

    @Test
    fun test_OnPasswordChanged_WhenNotIdle_DoesNothing() {
        val testModels = listOf(
            defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATING),
            defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATED),
        )
        testModels.forEach { testModel ->
            UpdateSpec(LoginScreenUpdate)
                .given(testModel)
                .whenEvent(LoginScreenEvent.OnUsernameChanged("test"))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnPairingStarted_SetsPairingCode() {
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel)
            .whenEvent(LoginScreenEvent.OnPairingStarted("test"))
            .then(
                assertThatNext(
                    hasNoEffects(),
                    hasModel(defaultModel.copy(pairingCode = "test")),
                ),
            )
    }

    @Test
    fun test_OnPairingEnded_ClearsPairingCode() {
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel)
            .whenEvent(LoginScreenEvent.OnPairingEnded("test"))
            .then(
                assertThatNext(
                    hasNoEffects(),
                    hasModel(defaultModel.copy(pairingCode = null)),
                ),
            )
    }

    @Test
    fun test_OnLoginSuccess_WhenStateIsAuthenticating_StateBecomesAuthenticated_And_NavigatesToHome() {
        val user = User(-1, "test", "test")
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATING))
            .whenEvent(LoginScreenEvent.OnLoginSuccess(user))
            .then(
                assertThatNext(
                    hasEffects(LoginScreenEffect.NavigateToHome),
                    hasModel(defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATED)),
                ),
            )
    }

    @Test
    fun test_OnLoginSuccess_WithActivePairingSession_StateBecomesAuthenticated_And_NavigatesToHome() {
        val user = User(-1, "test", "test")
        val startModel = defaultModel.copy(pairingCode = "test")
        UpdateSpec(LoginScreenUpdate)
            .given(startModel)
            .whenEvent(LoginScreenEvent.OnLoginSuccess(user))
            .then(
                assertThatNext(
                    hasEffects(LoginScreenEffect.NavigateToHome),
                    hasModel(startModel.copy(state = LoginScreenModel.State.AUTHENTICATED)),
                ),
            )
    }

    @Test
    fun test_OnLoginSuccess_WhenStateIsNotAuthenticating_DoesNothing() {
        val user = User(-1, "test", "test")
        val testModels = listOf(
            defaultModel.copy(state = LoginScreenModel.State.IDLE),
            defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATED),
        )
        testModels.forEach { testModel ->
            UpdateSpec(LoginScreenUpdate)
                .given(testModel)
                .whenEvent(LoginScreenEvent.OnLoginSuccess(user))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnLoginError_WhenStateIsAuthenticating_SetsLoginError() {
        val loginError = CreateSessionResponse.Error(usernameError = CreateSessionResponse.UsernameError.INVALID)
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATING))
            .whenEvent(LoginScreenEvent.OnLoginError(loginError))
            .then(
                assertThatNext(
                    hasNoEffects(),
                    hasModel(defaultModel.copy(loginError = loginError)),
                ),
            )
    }

    @Test
    fun test_OnLoginError_WhenStateIsNotAuthenticating_DoesNothing() {
        val testModels = listOf(
            defaultModel.copy(state = LoginScreenModel.State.IDLE),
            defaultModel.copy(state = LoginScreenModel.State.AUTHENTICATED),
        )
        testModels.forEach { testModel ->
            UpdateSpec(LoginScreenUpdate)
                .given(testModel)
                .whenEvent(LoginScreenEvent.OnLoginError(CreateSessionResponse.Error()))
                .then(assertThatNext(hasNothing()))
        }
    }
}
