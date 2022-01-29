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

import anystream.ui.login.LoginScreenModel.ServerValidation
import anystream.ui.login.LoginScreenModel.State
import kt.mobius.Next
import kt.mobius.Next.Companion.next
import kt.mobius.Next.Companion.noChange

object LoginScreenUpdate : LoginScreenUpdateSpec {
    override fun onLoginSubmit(model: LoginScreenModel): Next<LoginScreenModel, LoginScreenEffect> {
        return when (model.state) {
            State.IDLE -> {
                if (model.serverUrlIsValid() && model.credentialsAreSet()) {
                    next(
                        model.copy(state = State.AUTHENTICATING),
                        LoginScreenEffect.Login(model.username, model.password, model.serverUrl)
                    )
                } else noChange()
            }
            else -> noChange()
        }
    }

    override fun onServerUrlChanged(
        model: LoginScreenModel,
        event: LoginScreenEvent.OnServerUrlChanged
    ): Next<LoginScreenModel, LoginScreenEffect> {
        return when (model.state) {
            State.IDLE -> next(
                model.copy(
                    serverUrl = event.serverUrl,
                    serverValidation = ServerValidation.VALIDATING,
                    pairingCode = null,
                ),
                LoginScreenEffect.ValidateServerUrl(serverUrl = event.serverUrl),
                LoginScreenEffect.PairingSession("", cancel = true),
            )
            else -> noChange()
        }
    }

    override fun onUsernameChanged(
        model: LoginScreenModel,
        event: LoginScreenEvent.OnUsernameChanged
    ): Next<LoginScreenModel, LoginScreenEffect> {
        return when (model.state) {
            State.IDLE -> next(model.copy(username = event.username))
            else -> noChange()
        }
    }

    override fun onPasswordChanged(
        model: LoginScreenModel,
        event: LoginScreenEvent.OnPasswordChanged
    ): Next<LoginScreenModel, LoginScreenEffect> {
        return when (model.state) {
            State.IDLE -> next(model.copy(password = event.password))
            else -> noChange()
        }
    }

    override fun onPairingStarted(
        model: LoginScreenModel,
        event: LoginScreenEvent.OnPairingStarted
    ): Next<LoginScreenModel, LoginScreenEffect> {
        return next(model.copy(pairingCode = event.pairingCode))
    }

    override fun onPairingEnded(
        model: LoginScreenModel,
        event: LoginScreenEvent.OnPairingEnded
    ): Next<LoginScreenModel, LoginScreenEffect> {
        return next(model.copy(pairingCode = null))
    }

    override fun onLoginSuccess(
        model: LoginScreenModel,
        event: LoginScreenEvent.OnLoginSuccess
    ): Next<LoginScreenModel, LoginScreenEffect> {
        return when {
            model.state == State.AUTHENTICATING || !model.pairingCode.isNullOrBlank() -> {
                next(
                    model.copy(state = State.AUTHENTICATED),
                    LoginScreenEffect.NavigateToHome,
                )
            }
            else -> noChange()
        }
    }

    override fun onLoginError(
        model: LoginScreenModel,
        event: LoginScreenEvent.OnLoginError
    ): Next<LoginScreenModel, LoginScreenEffect> {
        return when (model.state) {
            State.AUTHENTICATING -> next(
                model.copy(
                    state = State.IDLE,
                    loginError = event.error,
                )
            )
            else -> noChange()
        }
    }

    override fun onServerValidated(
        model: LoginScreenModel,
        event: LoginScreenEvent.OnServerValidated
    ): Next<LoginScreenModel, LoginScreenEffect> {
        return when (model.state) {
            State.IDLE -> {
                if (model.serverUrl == event.serverUrl) {
                    val effects = mutableSetOf<LoginScreenEffect>()
                    val newModel = model.copy(serverValidation = event.result)
                    if (event.result == ServerValidation.VALID && newModel.supportsPairing) {
                        effects.add(LoginScreenEffect.PairingSession(event.serverUrl))
                    }
                    next(newModel, effects)
                } else noChange()
            }
            else -> noChange()
        }
    }
}
