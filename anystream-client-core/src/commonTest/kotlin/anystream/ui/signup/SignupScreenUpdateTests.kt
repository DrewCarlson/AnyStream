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

import anystream.models.User
import anystream.models.api.CreateUserResponse
import anystream.ui.signup.SignupScreenModel.ServerValidation
import anystream.ui.signup.SignupScreenModel.State
import kt.mobius.test.NextMatchers.hasEffects
import kt.mobius.test.NextMatchers.hasModel
import kt.mobius.test.NextMatchers.hasNoEffects
import kt.mobius.test.NextMatchers.hasNothing
import kt.mobius.test.UpdateSpec
import kt.mobius.test.UpdateSpec.Companion.assertThatNext
import kotlin.test.Test

class SignupScreenUpdateTests {

    private val defaultModel = SignupScreenModel.create()

    @Test
    fun test_OnServerValidated_WithMatchingUrl_UpdatesValidationState() {
        val testServerUrl = "https://test.url"
        UpdateSpec(SignupScreenUpdate)
            .given(defaultModel)
            .whenEvents(
                SignupScreenEvent.OnServerUrlChanged(testServerUrl),
                SignupScreenEvent.OnServerValidated(testServerUrl, ServerValidation.VALID)
            )
            .then(
                assertThatNext(
                    hasModel(
                        defaultModel.copy(
                            serverUrl = testServerUrl,
                            serverValidation = ServerValidation.VALID
                        )
                    )
                )
            )
    }

    @Test
    fun test_OnServerValidated_WithNotMatchingUrl_DoesNothing() {
        val testServerUrl = "https://test.url"
        UpdateSpec(SignupScreenUpdate)
            .given(defaultModel)
            .whenEvents(
                SignupScreenEvent.OnServerUrlChanged(testServerUrl),
                SignupScreenEvent.OnServerValidated("not-matching", ServerValidation.VALID)
            )
            .then(assertThatNext(hasNothing()))
    }

    @Test
    fun test_OnServerValidated_WhenStateIsNotIdle_DoesNothing() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALIDATING
        )
        val testModels = listOf(
            startModel.copy(state = State.AUTHENTICATING),
            startModel.copy(state = State.AUTHENTICATED)
        )

        testModels.forEach { testModel ->
            UpdateSpec(SignupScreenUpdate)
                .given(testModel)
                .whenEvent(SignupScreenEvent.OnServerValidated(testServerUrl, ServerValidation.VALID))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnServerUrlChanged_SetsModelUrl_And_ValidatesUrl() {
        val testServerUrl = "https://test.url"
        UpdateSpec(SignupScreenUpdate)
            .given(defaultModel)
            .whenEvent(SignupScreenEvent.OnServerUrlChanged(testServerUrl))
            .then(
                assertThatNext(
                    hasModel(
                        defaultModel.copy(
                            serverUrl = testServerUrl,
                            serverValidation = ServerValidation.VALIDATING
                        )
                    ),
                    hasEffects(SignupScreenEffect.ValidateServerUrl(testServerUrl))
                )
            )
    }

    @Test
    fun test_OnServerUrlChanged_WhenNotIdle_DoesNothing() {
        val testServerUrl = "https://test.url"
        val testModels = listOf(
            defaultModel.copy(state = State.AUTHENTICATING),
            defaultModel.copy(state = State.AUTHENTICATED)
        )
        testModels.forEach { testModel ->
            UpdateSpec(SignupScreenUpdate)
                .given(testModel)
                .whenEvent(SignupScreenEvent.OnServerUrlChanged(testServerUrl))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnSignupSubmit_WhenNotIdle_DoesNothing() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALID,
            username = "test",
            password = "test"
        )
        val testModels = listOf(
            startModel.copy(state = State.AUTHENTICATING),
            startModel.copy(state = State.AUTHENTICATED)
        )
        testModels.forEach { testModel ->
            UpdateSpec(SignupScreenUpdate)
                .given(testModel)
                .whenEvent(SignupScreenEvent.OnSignupSubmit)
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnSignupSubmit_WithCredentialsAndServerUrl_SendsLoginRequest() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALID,
            username = "test",
            password = "test"
        )
        UpdateSpec(SignupScreenUpdate)
            .given(startModel)
            .whenEvent(SignupScreenEvent.OnSignupSubmit)
            .then(
                assertThatNext(
                    hasModel(startModel.copy(state = State.AUTHENTICATING)),
                    hasEffects(
                        SignupScreenEffect.Signup(
                            startModel.username,
                            startModel.password,
                            startModel.inviteCode,
                            startModel.serverUrl
                        )
                    )
                )
            )
    }

    @Test
    fun test_OnSignupSubmit_WithInvalidServerUrl_DoesNothing() {
        val startModel = defaultModel.copy(
            serverUrl = "",
            serverValidation = ServerValidation.INVALID,
            username = "test",
            password = "test"
        )
        UpdateSpec(SignupScreenUpdate)
            .given(startModel)
            .whenEvent(SignupScreenEvent.OnSignupSubmit)
            .then(assertThatNext(hasNothing()))
    }

    @Test
    fun test_OnSignupSubmit_WithValidatingServerUrl_DoesNothing() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALIDATING,
            username = "test",
            password = "test"
        )
        UpdateSpec(SignupScreenUpdate)
            .given(startModel)
            .whenEvent(SignupScreenEvent.OnSignupSubmit)
            .then(assertThatNext(hasNothing()))
    }

    @Test
    fun test_OnSignupSubmit_WithoutCredentials_DoesNothing() {
        val testServerUrl = "https://test.url"
        val startModel = defaultModel.copy(
            serverUrl = testServerUrl,
            serverValidation = ServerValidation.VALID,
            username = "",
            password = ""
        )
        UpdateSpec(SignupScreenUpdate)
            .given(startModel)
            .whenEvent(SignupScreenEvent.OnSignupSubmit)
            .then(assertThatNext(hasNothing()))
    }

    @Test
    fun test_OnUsernameChanged_WhenIdle_UpdatesUsername() {
        UpdateSpec(SignupScreenUpdate)
            .given(defaultModel)
            .whenEvent(SignupScreenEvent.OnUsernameChanged("test"))
            .then(
                assertThatNext(
                    hasNoEffects(),
                    hasModel(defaultModel.copy(username = "test"))
                )
            )
    }

    @Test
    fun test_OnUsernameChanged_WhenNotIdle_DoesNothing() {
        val testModels = listOf(
            defaultModel.copy(state = State.AUTHENTICATING),
            defaultModel.copy(state = State.AUTHENTICATED)
        )
        testModels.forEach { testModel ->
            UpdateSpec(SignupScreenUpdate)
                .given(testModel)
                .whenEvent(SignupScreenEvent.OnUsernameChanged("test"))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnPasswordChanged_WhenIdle_UpdatesPassword() {
        UpdateSpec(SignupScreenUpdate)
            .given(defaultModel)
            .whenEvent(SignupScreenEvent.OnPasswordChanged("test"))
            .then(
                assertThatNext(
                    hasNoEffects(),
                    hasModel(defaultModel.copy(password = "test"))
                )
            )
    }

    @Test
    fun test_OnPasswordChanged_WhenNotIdle_DoesNothing() {
        val testModels = listOf(
            defaultModel.copy(state = State.AUTHENTICATING),
            defaultModel.copy(state = State.AUTHENTICATED)
        )
        testModels.forEach { testModel ->
            UpdateSpec(SignupScreenUpdate)
                .given(testModel)
                .whenEvent(SignupScreenEvent.OnUsernameChanged("test"))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnSignupSuccess_WhenStateIsAuthenticating_StateBecomesAuthenticated_And_NavigatesToHome() {
        val user = User(-1, "test", "test")
        UpdateSpec(SignupScreenUpdate)
            .given(defaultModel.copy(state = State.AUTHENTICATING))
            .whenEvent(SignupScreenEvent.OnSignupSuccess(user))
            .then(
                assertThatNext(
                    hasEffects(SignupScreenEffect.NavigateToHome),
                    hasModel(defaultModel.copy(state = State.AUTHENTICATED))
                )
            )
    }

    @Test
    fun test_OnSignupSuccess_WhenStateIsNotAuthenticating_DoesNothing() {
        val user = User(-1, "test", "test")
        val testModels = listOf(
            defaultModel.copy(state = State.IDLE),
            defaultModel.copy(state = State.AUTHENTICATED)
        )
        testModels.forEach { testModel ->
            UpdateSpec(SignupScreenUpdate)
                .given(testModel)
                .whenEvent(SignupScreenEvent.OnSignupSuccess(user))
                .then(assertThatNext(hasNothing()))
        }
    }

    @Test
    fun test_OnSignupError_WhenStateIsAuthenticating_SetsLoginError() {
        val signupError = CreateUserResponse.Error(usernameError = CreateUserResponse.UsernameError.TOO_LONG, null)
        UpdateSpec(SignupScreenUpdate)
            .given(defaultModel.copy(state = State.AUTHENTICATING))
            .whenEvent(SignupScreenEvent.OnSignupError(signupError))
            .then(
                assertThatNext(
                    hasNoEffects(),
                    hasModel(defaultModel.copy(signupError = signupError))
                )
            )
    }

    @Test
    fun test_OnSignupError_WhenStateIsNotAuthenticating_DoesNothing() {
        val testModels = listOf(
            defaultModel.copy(state = State.IDLE),
            defaultModel.copy(state = State.AUTHENTICATED)
        )
        testModels.forEach { testModel ->
            UpdateSpec(SignupScreenUpdate)
                .given(testModel)
                .whenEvent(SignupScreenEvent.OnSignupError(CreateUserResponse.Error(null, null)))
                .then(assertThatNext(hasNothing()))
        }
    }
}
