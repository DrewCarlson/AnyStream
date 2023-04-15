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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignupScreenModelTests {

    @Test
    fun test_CreateWithServerUrl_IsValid() {
        assertEquals(
            SignupScreenModel.ServerValidation.VALID,
            SignupScreenModel.create("test", "").serverValidation,
        )
        assertEquals(
            SignupScreenModel.ServerValidation.VALID,
            SignupScreenModel.create("test", "").serverValidation,
        )
    }

    @Test
    fun test_CreateWithoutServerUrl_IsValidating() {
        assertEquals(
            SignupScreenModel.ServerValidation.VALIDATING,
            SignupScreenModel.create().serverValidation,
        )
        assertEquals(
            SignupScreenModel.ServerValidation.VALIDATING,
            SignupScreenModel.create().serverValidation,
        )
    }

    @Test
    fun testIsServerUrlValid() {
        assertTrue(SignupScreenModel(serverValidation = SignupScreenModel.ServerValidation.VALID).isServerUrlValid())
        assertFalse(SignupScreenModel(serverValidation = SignupScreenModel.ServerValidation.VALIDATING).isServerUrlValid())
        assertFalse(SignupScreenModel(serverValidation = SignupScreenModel.ServerValidation.INVALID).isServerUrlValid())
    }

    @Test
    fun testIsInputLocked() {
        assertFalse(SignupScreenModel(state = SignupScreenModel.State.IDLE).isInputLocked())
        assertTrue(SignupScreenModel(state = SignupScreenModel.State.AUTHENTICATING).isInputLocked())
        assertTrue(SignupScreenModel(state = SignupScreenModel.State.AUTHENTICATED).isInputLocked())
    }

    @Test
    fun testCredentialsAreSet() {
        assertFalse(SignupScreenModel(username = "", password = "").credentialsAreSet())
        assertFalse(SignupScreenModel(username = "", password = "test").credentialsAreSet())
        assertFalse(SignupScreenModel(username = "test", password = "").credentialsAreSet())
        assertTrue(SignupScreenModel(username = "test", password = "test").credentialsAreSet())
    }
}
