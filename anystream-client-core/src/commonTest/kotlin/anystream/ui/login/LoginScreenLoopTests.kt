package anystream.ui.login

import anystream.ui.login.LoginScreenModel.ServerValidation
import kt.mobius.test.NextMatchers.hasEffects
import kt.mobius.test.NextMatchers.hasModel
import kt.mobius.test.UpdateSpec
import kt.mobius.test.UpdateSpec.Companion.assertThatNext
import kotlin.test.Test

class LoginScreenLoopTests {

    private val defaultModel = LoginScreenModel.create()

    @Test
    fun test_OnServerUrlChanged_SetsModelUrl_And_ValidatesUrl() {
        val testServerUrl = "https://test.url"
        UpdateSpec(LoginScreenUpdate)
            .given(defaultModel)
            .whenEvent(LoginScreenEvent.OnServerUrlChanged(testServerUrl))
            .then(
                assertThatNext(
                    hasModel(
                        defaultModel.copy(
                            serverUrl = testServerUrl,
                            serverValidation = ServerValidation.CHECKING
                        )
                    ),
                    hasEffects(
                        LoginScreenEffect.ValidateServerUrl(testServerUrl)
                    )
                )
            )
    }
}