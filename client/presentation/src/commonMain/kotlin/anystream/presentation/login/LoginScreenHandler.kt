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
package anystream.presentation.login

import anystream.client.AnyStreamClient
import anystream.models.api.CreateSessionResponse
import anystream.models.api.PairingMessage
import anystream.routing.CommonRouter
import anystream.routing.Routes
import anystream.presentation.login.LoginScreenModel.ServerValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kt.mobius.flow.ExecutionPolicy
import kt.mobius.flow.FlowTransformer
import kt.mobius.flow.subtypeEffectHandler
import kotlin.time.Duration.Companion.seconds
import anystream.presentation.login.LoginScreenEffect as Effect
import anystream.presentation.login.LoginScreenEvent as Event

class LoginScreenHandler(
    client: AnyStreamClient,
    router: CommonRouter,
) : FlowTransformer<Effect, Event> by subtypeEffectHandler({

    // Future improvements:
    // - Add anystream verification url to server and validate Server Url points to real instance

    addFunction<Effect.LoadAuthTypes>(ExecutionPolicy.Latest) {
        var authTypes: List<String>? = null
        while (authTypes == null) {
            authTypes = try {
                client.user.fetchAuthTypes()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                null
            }
            if (authTypes == null) {
                delay(5.seconds)
            }
        }
        Event.OnAuthTypesLoaded(authTypes)
    }

    addValueCollector<Effect.RedirectOnAuth>(ExecutionPolicy.Latest) {
        client.user.user.filterNotNull().collect {
            router.replaceTop(Routes.Home)
        }
    }

    addAction<Effect.NavigateToHome> { router.replaceTop(Routes.Home) }

    addFunction<Effect.Login> { (username, password, serverUrl) ->
        try {
            check(client.core.verifyAndSetServerUrl(serverUrl))
            client.user.login(username, password).toLoginScreenEvent()
        } catch (e: Throwable) {
            Event.OnLoginError(CreateSessionResponse.Error(null, null))
        }
    }

    addValueCollector<Effect.ValidateServerUrl>(ExecutionPolicy.Latest) { (serverUrl) ->
        val result = try {
            if (client.core.verifyAndSetServerUrl(serverUrl)) {
                ServerValidation.VALID
            } else {
                ServerValidation.INVALID
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            ServerValidation.INVALID
        }

        emit(Event.OnServerValidated(serverUrl, result))
    }

    addValueCollector<Effect.PairingSession>(ExecutionPolicy.Latest) { (serverUrl, cancel) ->
        if (cancel || !client.core.verifyAndSetServerUrl(serverUrl)) return@addValueCollector

        lateinit var pairingCode: String
        val pairingFlow = client.user
            .createPairingSession()
            .catch { }
            .mapNotNull { message ->
                when (message) {
                    PairingMessage.Idle -> null // waiting for remote pairing
                    is PairingMessage.Started -> {
                        pairingCode = message.pairingCode
                        Event.OnPairingStarted(message.pairingCode)
                    }

                    is PairingMessage.Authorized -> {
                        client.user.createPairedSession(pairingCode, message.secret).toLoginScreenEvent()
                    }

                    PairingMessage.Failed -> Event.OnPairingEnded(pairingCode)
                }
            }
        emitAll(pairingFlow)
    }
})

private fun CreateSessionResponse.toLoginScreenEvent(): Event {
    return when (this) {
        is CreateSessionResponse.Success -> Event.OnLoginSuccess(user)
        is CreateSessionResponse.Error -> Event.OnLoginError(this)
    }
}
