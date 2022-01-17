package anystream.ui.login

import anystream.models.User
import anystream.models.api.CreateSessionError

sealed class LoginScreenEvent {
    data class OnServerUrlChanged(
        val serverUrl: String
    ) : LoginScreenEvent()

    data class OnUsernameChanged(
        val username: String
    ) : LoginScreenEvent()

    data class OnPasswordChanged(
        val password: String
    ) : LoginScreenEvent()

    object OnLoginSubmit : LoginScreenEvent()

    data class OnPairingStarted(
        val pairingCode: String,
    ) : LoginScreenEvent()

    data class OnPairingEnded(
        val pairingCode: String,
    ) : LoginScreenEvent()

    data class OnLoginSuccess(
        val user: User,
    ) : LoginScreenEvent()

    data class OnLoginError(
        val error: CreateSessionError,
    ) : LoginScreenEvent()

    data class OnServerValidated(
        val serverUrl: String,
        val result: LoginScreenModel.ServerValidation,
    ) : LoginScreenEvent()
}