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
import kt.mobius.Update
import kt.mobius.gen.GenerateUpdate
import anystream.ui.login.LoginScreenEffect as Effect
import anystream.ui.login.LoginScreenModel as Model

@GenerateUpdate
object LoginScreenUpdate : Update<Model, LoginScreenEvent, Effect>, LoginScreenGeneratedUpdate {
    override fun onLoginSubmit(model: Model): Next<Model, Effect> {
        return when (model.state) {
            State.IDLE -> {
                if (model.isServerUrlValid() && model.credentialsAreSet()) {
                    next(
                        model.copy(state = State.AUTHENTICATING),
                        Effect.Login(model.username, model.password, model.serverUrl),
                    )
                } else {
                    noChange()
                }
            }

            else -> noChange()
        }
    }

    override fun onServerUrlChanged(
        model: Model,
        event: LoginScreenEvent.OnServerUrlChanged,
    ): Next<Model, Effect> {
        return when (model.state) {
            State.IDLE -> next(
                model.copy(
                    serverUrl = event.serverUrl,
                    serverValidation = ServerValidation.VALIDATING,
                    pairingCode = null,
                ),
                Effect.ValidateServerUrl(serverUrl = event.serverUrl),
                Effect.PairingSession("", cancel = true),
            )

            else -> noChange()
        }
    }

    override fun onUsernameChanged(
        model: Model,
        event: LoginScreenEvent.OnUsernameChanged,
    ): Next<Model, Effect> {
        return when (model.state) {
            State.IDLE -> next(model.copy(username = event.username))
            else -> noChange()
        }
    }

    override fun onPasswordChanged(
        model: Model,
        event: LoginScreenEvent.OnPasswordChanged,
    ): Next<Model, Effect> {
        return when (model.state) {
            State.IDLE -> next(model.copy(password = event.password))
            else -> noChange()
        }
    }

    override fun onPairingStarted(
        model: Model,
        event: LoginScreenEvent.OnPairingStarted,
    ): Next<Model, Effect> {
        return next(model.copy(pairingCode = event.pairingCode))
    }

    override fun onPairingEnded(
        model: Model,
        event: LoginScreenEvent.OnPairingEnded,
    ): Next<Model, Effect> {
        return next(model.copy(pairingCode = null))
    }

    override fun onLoginSuccess(
        model: Model,
        event: LoginScreenEvent.OnLoginSuccess,
    ): Next<Model, Effect> {
        return when {
            model.state == State.AUTHENTICATING || !model.pairingCode.isNullOrBlank() -> {
                next(
                    model.copy(state = State.AUTHENTICATED),
                    Effect.NavigateToHome,
                )
            }

            else -> noChange()
        }
    }

    override fun onLoginError(
        model: Model,
        event: LoginScreenEvent.OnLoginError,
    ): Next<Model, Effect> {
        return when (model.state) {
            State.AUTHENTICATING -> next(
                model.copy(
                    state = State.IDLE,
                    loginError = event.error,
                ),
            )

            else -> noChange()
        }
    }

    override fun onServerValidated(
        model: Model,
        event: LoginScreenEvent.OnServerValidated,
    ): Next<Model, Effect> {
        if (model.state != State.IDLE) {
            return noChange()
        }
        return if (model.serverUrl == event.serverUrl) {
            val effects = mutableSetOf<Effect>()
            val newModel = model.copy(serverValidation = event.result)
            if (event.result == ServerValidation.VALID && newModel.supportsPairing) {
                effects.add(Effect.PairingSession(event.serverUrl))
            }
            next(newModel, effects)
        } else {
            noChange()
        }
    }
}
