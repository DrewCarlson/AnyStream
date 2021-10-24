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
package anystream.service

import anystream.models.PASSWORD_LENGTH_MAX
import anystream.models.PASSWORD_LENGTH_MIN
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class UserServiceTest {

    private lateinit var userService: UserService

    @Before
    fun setup() {
        userService = UserService.createForTest()
    }

    @Test
    fun testHashPasswordMinimumLength() {
        val password = CharArray(PASSWORD_LENGTH_MIN - 1) { '0' }.concatToString()
        assertFailsWith<IllegalArgumentException> {
            userService.hashPassword(password)
        }
    }

    @Test
    fun testHashPasswordMaximumLength() {
        val password = CharArray(PASSWORD_LENGTH_MAX + 1) { '0' }.concatToString()
        assertFailsWith<IllegalArgumentException> {
            userService.hashPassword(password)
        }
    }

    @Test
    fun testHashPassword() {
        val password = "test123"
        val (hashedPassword, _) = userService.hashPassword(password)
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
            userService.verifyPassword("wrong", bcryptString),
            "Invalid password should not verify"
        )
        assertTrue(
            userService.verifyPassword(password, bcryptString),
            "Valid password should verify"
        )

        assertTrue(
            userService.verifyPassword(
                checkPassword = password,
                hashedPassword = userService.hashPassword(password).first
            )
        )
    }
}