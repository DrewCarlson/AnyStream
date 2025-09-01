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

import anystream.presentation.login.LoginScreenModel.ServerValidation
import kt.mobius.test.FirstMatchers.hasEffects
import kt.mobius.test.FirstMatchers.hasModel
import kt.mobius.test.InitSpec
import kt.mobius.test.InitSpec.Companion.assertThatFirst
import kotlin.test.Test

class LoginScreenInitTests {

    @Test
    fun test_ModelWithoutUrl_LoadsAuthTypes() {
        val startModel = LoginScreenModel.create()
        InitSpec(LoginScreenInit)
            .whenInit(startModel)
            .then(
                assertThatFirst(
                    hasModel(startModel),
                    hasEffects(
                        LoginScreenEffect.LoadAuthTypes,
                        LoginScreenEffect.RedirectOnAuth
                    ),
                ),
            )
    }

    @Test
    fun test_ModelWithUrl_RequiringValidation_RequestsValidation() {
        val startModel = LoginScreenModel(
            serverUrl = "test",
            serverValidation = ServerValidation.VALIDATING,
        )
        InitSpec(LoginScreenInit)
            .whenInit(startModel)
            .then(
                assertThatFirst(
                    hasModel(startModel),
                    hasEffects(LoginScreenEffect.ValidateServerUrl("test")),
                ),
            )
    }

    @Test
    fun test_ModelWithValidUrlAndPairingSupport_StartsPairingSession() {
        val startModel = LoginScreenModel.create("test", supportsPairing = true)
        InitSpec(LoginScreenInit)
            .whenInit(startModel)
            .then(
                assertThatFirst(
                    hasModel(startModel),
                    hasEffects(LoginScreenEffect.PairingSession("test")),
                ),
            )
    }
}
