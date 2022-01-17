/**
 * AnyStream
 * Copyright (C) 2022 Drew Carlson
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
import kt.mobius.First
import kt.mobius.First.Companion.first
import kt.mobius.Init

object LoginScreenInit : Init<LoginScreenModel, LoginScreenEffect> {

    override fun init(model: LoginScreenModel): First<LoginScreenModel, LoginScreenEffect> {
        val effects = mutableSetOf<LoginScreenEffect>()

        if (model.serverUrlIsValid() && model.supportsPairing) {
            effects.add(LoginScreenEffect.PairingSession(model.serverUrl))
        }

        if (model.serverUrl.isNotBlank() && model.serverValidation == ServerValidation.VALIDATING) {
            effects.add(LoginScreenEffect.ValidateServerUrl(model.serverUrl))
        }

        return first(model, effects)
    }
}