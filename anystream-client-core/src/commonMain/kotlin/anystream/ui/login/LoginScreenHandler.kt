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

import anystream.client.AnyStreamClient
import anystream.models.api.CreateSessionResponse
import anystream.models.api.PairingMessage
import anystream.ui.login.LoginScreenModel.ServerValidation
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.mapNotNull
import kt.mobius.flow.FlowTransformer
import kt.mobius.flow.subtypeEffectHandler

object LoginScreenHandler {

    // Future improvements:
    // - Setup CommonRouter interface and connect to routing effects
    // - Add anystream verification url to server and validate Server Url points to real instance
    fun create(
        client: AnyStreamClient,
        routeToHome: () -> Unit,
    ): FlowTransformer<LoginScreenEffect, LoginScreenEvent> = subtypeEffectHandler {
        addAction<LoginScreenEffect.NavigateToHome> { routeToHome() }

        addFunction<LoginScreenEffect.Login> { effect ->
            client.login(effect.username, effect.password).toLoginScreenEvent()
        }

        addLatestValueCollector<LoginScreenEffect.ValidateServerUrl> { effect ->
            val result = try {
                check(client.verifyAndSetServerUrl(effect.serverUrl))
                ServerValidation.VALID
            } catch (e: Throwable) {
                ServerValidation.INVALID
            }

            emit(LoginScreenEvent.OnServerValidated(effect.serverUrl, result))
        }

        addLatestValueCollector<LoginScreenEffect.PairingSession> { effect ->
            if (effect.cancel) return@addLatestValueCollector

            lateinit var pairingCode: String
            val pairingFlow = client.createPairingSession().mapNotNull { message ->
                when (message) {
                    PairingMessage.Idle -> null // waiting for remote pairing
                    is PairingMessage.Started -> {
                        pairingCode = message.pairingCode
                        LoginScreenEvent.OnPairingStarted(message.pairingCode)
                    }
                    is PairingMessage.Authorized -> {
                        client.createPairedSession(pairingCode, message.secret).toLoginScreenEvent()
                    }
                    PairingMessage.Failed -> LoginScreenEvent.OnPairingEnded(pairingCode)
                }
            }
            emitAll(pairingFlow)
        }
    }

    private fun CreateSessionResponse.toLoginScreenEvent(): LoginScreenEvent {
        return when (this) {
            is CreateSessionResponse.Success -> LoginScreenEvent.OnLoginSuccess(user)
            is CreateSessionResponse.Error -> LoginScreenEvent.OnLoginError(this)
        }
    }
}
