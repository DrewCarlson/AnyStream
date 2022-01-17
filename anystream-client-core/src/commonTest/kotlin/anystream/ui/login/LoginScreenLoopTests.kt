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
                            serverValidation = ServerValidation.VALIDATING
                        )
                    ),
                    hasEffects(
                        LoginScreenEffect.ValidateServerUrl(testServerUrl)
                    )
                )
            )
    }
}
