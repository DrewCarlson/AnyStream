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

import anystream.ui.signup.SignupScreenModel.State
import kt.mobius.Next
import kt.mobius.Next.Companion.next
import kt.mobius.Next.Companion.noChange
import kt.mobius.Update
import kt.mobius.gen.GenerateUpdate

@GenerateUpdate
object SignupScreenUpdate :
    Update<SignupScreenModel, SignupScreenEvent, SignupScreenEffect>,
    SignupScreenGeneratedUpdate {

    override fun onSignupSubmit(model: SignupScreenModel): Next<SignupScreenModel, SignupScreenEffect> {
        return when (model.state) {
            State.IDLE -> {
                if (model.isServerUrlValid() && model.credentialsAreSet()) {
                    next(
                        model.copy(state = State.AUTHENTICATING),
                        SignupScreenEffect.Signup(model.username, model.password, model.inviteCode, model.serverUrl)
                    )
                } else noChange()
            }
            else -> noChange()
        }
    }

    override fun onServerUrlChanged(
        model: SignupScreenModel,
        event: SignupScreenEvent.OnServerUrlChanged
    ): Next<SignupScreenModel, SignupScreenEffect> {
        return when (model.state) {
            State.IDLE -> next(
                model.copy(
                    serverUrl = event.serverUrl,
                    serverValidation = SignupScreenModel.ServerValidation.VALIDATING
                ),
                SignupScreenEffect.ValidateServerUrl(serverUrl = event.serverUrl)
            )
            else -> noChange()
        }
    }

    override fun onUsernameChanged(
        model: SignupScreenModel,
        event: SignupScreenEvent.OnUsernameChanged
    ): Next<SignupScreenModel, SignupScreenEffect> {
        return when (model.state) {
            State.IDLE -> next(model.copy(username = event.username))
            else -> noChange()
        }
    }

    override fun onPasswordChanged(
        model: SignupScreenModel,
        event: SignupScreenEvent.OnPasswordChanged
    ): Next<SignupScreenModel, SignupScreenEffect> {
        return when (model.state) {
            State.IDLE -> next(model.copy(password = event.password))
            else -> noChange()
        }
    }

    override fun onInviteCodeChanged(
        model: SignupScreenModel,
        event: SignupScreenEvent.OnInviteCodeChanged
    ): Next<SignupScreenModel, SignupScreenEffect> {
        return when (model.state) {
            State.IDLE -> next(model.copy(inviteCode = event.inviteCode))
            else -> noChange()
        }
    }

    override fun onSignupSuccess(
        model: SignupScreenModel,
        event: SignupScreenEvent.OnSignupSuccess
    ): Next<SignupScreenModel, SignupScreenEffect> {
        return when (model.state) {
            State.AUTHENTICATING -> {
                next(
                    model.copy(state = State.AUTHENTICATED),
                    SignupScreenEffect.NavigateToHome
                )
            }
            else -> noChange()
        }
    }

    override fun onSignupError(
        model: SignupScreenModel,
        event: SignupScreenEvent.OnSignupError
    ): Next<SignupScreenModel, SignupScreenEffect> {
        return when (model.state) {
            State.AUTHENTICATING -> next(
                model.copy(
                    state = State.IDLE,
                    signupError = event.error
                )
            )
            else -> noChange()
        }
    }

    override fun onServerValidated(
        model: SignupScreenModel,
        event: SignupScreenEvent.OnServerValidated
    ): Next<SignupScreenModel, SignupScreenEffect> {
        return when (model.state) {
            State.IDLE -> {
                if (model.serverUrl == event.serverUrl) {
                    next(model.copy(serverValidation = event.result))
                } else noChange()
            }
            else -> noChange()
        }
    }
}
