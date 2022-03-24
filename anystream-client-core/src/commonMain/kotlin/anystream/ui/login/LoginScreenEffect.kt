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

sealed class LoginScreenEffect {
    data class Login(
        val username: String,
        val password: String,
        val serverUrl: String,
    ) : LoginScreenEffect() {
        override fun toString(): String {
            return "Login(username='$username', " +
                    "password='***', " +
                    "serverUrl='$serverUrl')"
        }
    }

    data class ValidateServerUrl(
        val serverUrl: String
    ) : LoginScreenEffect()

    data class PairingSession(
        val serverUrl: String,
        val cancel: Boolean = false,
    ) : LoginScreenEffect()

    object NavigateToHome : LoginScreenEffect()
}
