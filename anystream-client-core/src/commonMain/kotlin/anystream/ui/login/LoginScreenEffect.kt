package anystream.ui.login

sealed class LoginScreenEffect {
    data class Login(
        val username: String,
        val password: String,
        val serverUrl: String,
    ) : LoginScreenEffect()

    data class ValidateServerUrl(
        val serverUrl: String
    ) : LoginScreenEffect()

    data class PairingSession(
        val serverUrl: String,
        val cancel: Boolean = false,
    ) : LoginScreenEffect()

    object NavigateToHome : LoginScreenEffect()
}
