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
package anystream.ui.signup

import kt.mobius.test.FirstMatchers.hasEffects
import kt.mobius.test.FirstMatchers.hasModel
import kt.mobius.test.FirstMatchers.hasNoEffects
import kt.mobius.test.InitSpec
import kotlin.test.Test

class SignupScreenInitTests {

    @Test
    fun test_ModelWithoutUrl_DoesNothing() {
        val startModel = SignupScreenModel.create()
        InitSpec(SignupScreenInit)
            .whenInit(startModel)
            .then(
                InitSpec.assertThatFirst(
                    hasModel(startModel),
                    hasNoEffects(),
                )
            )
    }

    @Test
    fun test_ModelWithUrl_RequiringValidation_RequestsValidation() {
        val startModel = SignupScreenModel(
            serverUrl = "test",
            serverValidation = SignupScreenModel.ServerValidation.VALIDATING,
        )
        InitSpec(SignupScreenInit)
            .whenInit(startModel)
            .then(
                InitSpec.assertThatFirst(
                    hasModel(startModel),
                    hasEffects(SignupScreenEffect.ValidateServerUrl("test")),
                )
            )
    }
}
