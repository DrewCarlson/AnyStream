/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.util

import anystream.models.PASSWORD_LENGTH_MAX
import anystream.models.PASSWORD_LENGTH_MIN
import org.junit.Test
import kotlin.test.*

class UserAuthenticatorTest {

    @Test
    fun testHashPasswordMinimumLength() {
        val password = CharArray(PASSWORD_LENGTH_MIN - 1) { '0' }.concatToString()
        assertFailsWith<IllegalArgumentException> {
            UserAuthenticator.hashPassword(password)
        }
    }

    @Test
    fun testHashPasswordMaximumLength() {
        val password = CharArray(PASSWORD_LENGTH_MAX + 1) { '0' }.concatToString()
        assertFailsWith<IllegalArgumentException> {
            UserAuthenticator.hashPassword(password)
        }
    }

    @Test
    fun testHashPassword() {
        val password = "test123"
        val (hashedPassword, _) = UserAuthenticator.hashPassword(password)
        assertNotEquals(password, hashedPassword)
        assertTrue(
            hashedPassword.startsWith("\$2y\$10\$"),
            "Result should be in BCrypt format"
        )
    }

    @Test
    fun testHashPasswordVerification() {
        val password = "test123"
        val bcryptString = "\$2y\$10\$uHBdQFb5YgpLrrJzFmPXteBgDxQn6zEoCzcYO1qfVOjYOvCUr.9Qq"

        assertFalse(
            UserAuthenticator.verifyPassword("wrong", bcryptString),
            "Invalid password should not verify"
        )
        assertTrue(
            UserAuthenticator.verifyPassword(password, bcryptString),
            "Valid password should verify"
        )
    }
}