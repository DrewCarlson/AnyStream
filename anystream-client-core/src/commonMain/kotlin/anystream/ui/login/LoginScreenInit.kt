package anystream.ui.login

import anystream.ui.login.LoginScreenModel.ServerValidation
import kt.mobius.First
import kt.mobius.First.Companion.first
import kt.mobius.Init

object LoginScreenInit : Init<LoginScreenModel, LoginScreenEffect> {

    override fun init(model: LoginScreenModel): First<LoginScreenModel, LoginScreenEffect> {
        val effects = mutableSetOf<LoginScreenEffect>()

        if (model.serverUrlIsValid() && model.supportsPairing) {
            effects.add(LoginScreenEffect.PairingSession(model.serverUrl))
        }

        if (model.serverUrl.isNotBlank() && model.serverValidation == ServerValidation.CHECKING) {
            effects.add(LoginScreenEffect.ValidateServerUrl(model.serverUrl))
        }

        return first(model, effects)
    }
}