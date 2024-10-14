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

import anystream.client.AnyStreamClient
import anystream.models.api.CreateUserResponse
import anystream.routing.CommonRouter
import anystream.routing.Routes
import anystream.ui.signup.SignupScreenModel.ServerValidation
import kt.mobius.flow.ExecutionPolicy
import kt.mobius.flow.FlowTransformer
import kt.mobius.flow.subtypeEffectHandler

object SignupScreenHandler {

    // Future improvements:
    // - Add anystream verification url to server and validate Server Url points to real instance
    fun create(
        client: AnyStreamClient,
        router: CommonRouter,
    ): FlowTransformer<SignupScreenEffect, SignupScreenEvent> = subtypeEffectHandler {
        addAction<SignupScreenEffect.NavigateToHome> { router.replaceTop(Routes.Home) }

        addFunction<SignupScreenEffect.Signup> { (username, password, inviteCode, serverUrl) ->
            try {
                check(client.verifyAndSetServerUrl(serverUrl))
                client.createUser(username, password, inviteCode).toSignupScreenEvent()
            } catch (e: Throwable) {
                SignupScreenEvent.OnSignupError(CreateUserResponse.Error(null, null))
            }
        }

        addValueCollector<SignupScreenEffect.ValidateServerUrl>(ExecutionPolicy.Latest) { (serverUrl) ->
            val result = try {
                client.verifyAndSetServerUrl(serverUrl)
                ServerValidation.VALID
            } catch (e: Throwable) {
                ServerValidation.INVALID
            }

            emit(SignupScreenEvent.OnServerValidated(serverUrl, result))
        }
    }

    private fun CreateUserResponse.toSignupScreenEvent(): SignupScreenEvent {
        return when (this) {
            is CreateUserResponse.Success -> SignupScreenEvent.OnSignupSuccess(user)
            is CreateUserResponse.Error -> SignupScreenEvent.OnSignupError(this)
        }
    }
}
