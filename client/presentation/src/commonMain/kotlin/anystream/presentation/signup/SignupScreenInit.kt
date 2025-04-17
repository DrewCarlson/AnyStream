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
package anystream.presentation.signup

import anystream.presentation.signup.SignupScreenModel.ServerValidation
import kt.mobius.First
import kt.mobius.First.Companion.first
import kt.mobius.Init

object SignupScreenInit : Init<SignupScreenModel, SignupScreenEffect> {

    override fun init(model: SignupScreenModel): First<SignupScreenModel, SignupScreenEffect> {
        val effects = mutableSetOf<SignupScreenEffect>()

        if (model.serverUrl.isNotBlank() && model.serverValidation == ServerValidation.VALIDATING) {
            effects.add(SignupScreenEffect.ValidateServerUrl(model.serverUrl))
        }

        return first(model, effects)
    }
}
