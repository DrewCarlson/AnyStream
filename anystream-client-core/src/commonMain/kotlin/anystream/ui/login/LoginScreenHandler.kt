package anystream.ui.login

import anystream.client.AnyStreamClient
import anystream.models.api.CreateSessionResponse
import anystream.models.api.PairingMessage
import anystream.ui.login.LoginScreenModel.ServerValidation
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.mapNotNull
import kt.mobius.flow.FlowTransformer
import kt.mobius.flow.subtypeEffectHandler

object LoginScreenHandler {

    // Future improvements:
    // - Setup CommonRouter interface and connect to routing effects
    // - Cache AnyStreamClient for a given Server Url, remove when new Url is validated
    // - Add anystream verification url to server and validate Server Url points to real instance
    fun create(
        httpClient: HttpClient,
    ): FlowTransformer<LoginScreenEffect, LoginScreenEvent> = subtypeEffectHandler {
        addAction<LoginScreenEffect.NavigateToHome> {
            // router.replaceTop(Routes.Home)
        }
        addFunction<LoginScreenEffect.ValidateServerUrl> { effect ->
            val result = try {
                httpClient.get(effect.serverUrl)
                ServerValidation.VALID
            } catch (e: Throwable) {
                ServerValidation.INVALID
            }

            LoginScreenEvent.OnServerValidated(effect.serverUrl, result)
        }

        addFunction<LoginScreenEffect.Login> { effect ->
            val client = AnyStreamClient(
                serverUrl = effect.serverUrl,
                http = httpClient,
            )

            client.login(effect.username, effect.password).toLoginScreenEvent()
        }

        addLatestValueCollector<LoginScreenEffect.PairingSession> { effect ->
            if (effect.cancel) return@addLatestValueCollector

            val client = AnyStreamClient(
                serverUrl = effect.serverUrl,
                http = httpClient,
            )

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
        return let { (success, error) ->
            success?.user?.run(LoginScreenEvent::OnLoginSuccess)
                ?: error?.run(LoginScreenEvent::OnLoginError)
                ?: error("Unexpected server response")
        }
    }
}