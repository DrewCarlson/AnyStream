package anystream.ui.login

import anystream.models.api.CreateSessionError
import kt.mobius.gen.UpdateSpec

@UpdateSpec(
    eventClass = LoginScreenEvent::class,
    effectClass = LoginScreenEffect::class,
)
data class LoginScreenModel(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val supportsPairing: Boolean = true,
    val pairingCode: String? = null,
    val state: State = State.IDLE,
    val serverValidation: ServerValidation = ServerValidation.CHECKING,
    val loginError: CreateSessionError? = null,
) {
    enum class State {
        IDLE, AUTHENTICATING, AUTHENTICATED,
    }

    enum class ServerValidation {
        VALID, INVALID, CHECKING,
    }

    fun credentialsAreSet(): Boolean {
        return username.isNotBlank() && username.isNotBlank()
    }

    fun serverUrlIsValid(): Boolean {
        return serverValidation == ServerValidation.VALID
    }

    companion object {
        fun create(): LoginScreenModel {
            return LoginScreenModel()
        }

        fun create(serverUrl: String): LoginScreenModel {
            return LoginScreenModel(serverUrl = serverUrl)
        }
    }
}