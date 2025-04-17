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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginScreenModelTests {

    @Test
    fun test_CreateWithServerUrl_IsValid() {
        assertEquals(
            ServerValidation.VALID,
            LoginScreenModel.create("test", supportsPairing = true).serverValidation,
        )
        assertEquals(
            ServerValidation.VALID,
            LoginScreenModel.create("test", supportsPairing = false).serverValidation,
        )
    }

    @Test
    fun test_CreateWithoutServerUrl_IsValidating() {
        assertEquals(
            ServerValidation.VALIDATING,
            LoginScreenModel.create().serverValidation,
        )
        assertEquals(
            ServerValidation.VALIDATING,
            LoginScreenModel.create(supportsPairing = false).serverValidation,
        )
    }

    @Test
    fun testIsServerUrlValid() {
        assertTrue(LoginScreenModel(serverValidation = ServerValidation.VALID).isServerUrlValid())
        assertFalse(LoginScreenModel(serverValidation = ServerValidation.VALIDATING).isServerUrlValid())
        assertFalse(LoginScreenModel(serverValidation = ServerValidation.INVALID).isServerUrlValid())
    }

    @Test
    fun testIsInputLocked() {
        assertFalse(LoginScreenModel(state = LoginScreenModel.State.IDLE).isInputLocked())
        assertTrue(LoginScreenModel(state = LoginScreenModel.State.AUTHENTICATING).isInputLocked())
        assertTrue(LoginScreenModel(state = LoginScreenModel.State.AUTHENTICATED).isInputLocked())
    }

    @Test
    fun testCredentialsAreSet() {
        assertFalse(LoginScreenModel(username = "", password = "").credentialsAreSet())
        assertFalse(LoginScreenModel(username = "", password = "test").credentialsAreSet())
        assertFalse(LoginScreenModel(username = "test", password = "").credentialsAreSet())
        assertTrue(LoginScreenModel(username = "test", password = "test").credentialsAreSet())
    }
}
