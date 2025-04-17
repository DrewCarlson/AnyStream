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
package anystream.presentation.signup

import anystream.client.AnyStreamClient
import anystream.models.api.CreateUserResponse
import anystream.routing.CommonRouter
import anystream.routing.Routes
import anystream.presentation.signup.SignupScreenModel.ServerValidation
import kotlinx.coroutines.CancellationException
import kt.mobius.flow.ExecutionPolicy
import kt.mobius.flow.FlowTransformer
import kt.mobius.flow.subtypeEffectHandler
import anystream.presentation.signup.SignupScreenEffect as Effect
import anystream.presentation.signup.SignupScreenEvent as Event

class SignupScreenHandler(
    client: AnyStreamClient,
    router: CommonRouter,
) : FlowTransformer<Effect, Event> by subtypeEffectHandler({

    // Future improvements:
    // - Add anystream verification url to server and validate Server Url points to real instance

    addAction<Effect.NavigateToHome> { router.replaceTop(Routes.Home) }

    addFunction<Effect.Signup> { (username, password, inviteCode, serverUrl) ->
        try {
            check(client.verifyAndSetServerUrl(serverUrl))
            client.createUser(username, password, inviteCode).toSignupScreenEvent()
        } catch (e: Throwable) {
            Event.OnSignupError(CreateUserResponse.Error(null, null))
        }
    }

    addValueCollector<Effect.ValidateServerUrl>(ExecutionPolicy.Latest) { (serverUrl) ->
        val result = try {
            client.verifyAndSetServerUrl(serverUrl)
            ServerValidation.VALID
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            ServerValidation.INVALID
        }

        emit(Event.OnServerValidated(serverUrl, result))
    }
})

private fun CreateUserResponse.toSignupScreenEvent(): Event {
    return when (this) {
        is CreateUserResponse.Success -> Event.OnSignupSuccess(user)
        is CreateUserResponse.Error -> Event.OnSignupError(this)
    }
}
