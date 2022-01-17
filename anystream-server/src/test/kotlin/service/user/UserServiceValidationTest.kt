/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.service.user

import anystream.models.*
import anystream.models.api.CreateSessionBody
import anystream.models.api.CreateSessionError
import anystream.models.api.CreateUserBody
import anystream.models.api.CreateUserError
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class UserServiceValidationTest {

    private lateinit var queries: TestUserServiceQueries
    private lateinit var userService: UserService

    @Before
    fun setup() {
        queries = TestUserServiceQueries()
        userService = UserService(queries)
    }

    // <editor-fold desc="Create User Tests">
    @Test
    fun testCreateUserBlankValidations(): Unit = runBlocking {
        val body = CreateUserBody(
            username = "",
            password = "",
            inviteCode = null,
        )

        val response = userService.createUser(body)
        val error = response?.error

        assertNotNull(error, "Expected error but response was $response")

        assertEquals(CreateUserError.UsernameError.BLANK, error.usernameError)
        assertEquals(CreateUserError.PasswordError.BLANK, error.passwordError)
    }

    @Test
    fun testCreateUserLengthValidations(): Unit = runBlocking {
        // too short
        val tooShortBody = CreateUserBody(
            username = CharArray(USERNAME_LENGTH_MIN - 1) { '0' }.concatToString(),
            password = CharArray(PASSWORD_LENGTH_MIN - 1) { '0' }.concatToString(),
            inviteCode = null,
        )

        val tooShortResponse = userService.createUser(tooShortBody)
        val tooShortError = tooShortResponse?.error

        assertNotNull(tooShortError, "Expected error but response was $tooShortResponse")

        assertEquals(CreateUserError.UsernameError.TOO_SHORT, tooShortError.usernameError)
        assertEquals(CreateUserError.PasswordError.TOO_SHORT, tooShortError.passwordError)

        // too long
        val tooLongBody = CreateUserBody(
            username = CharArray(USERNAME_LENGTH_MAX + 1) { '0' }.concatToString(),
            password = CharArray(PASSWORD_LENGTH_MAX + 1) { '0' }.concatToString(),
            inviteCode = null,
        )

        val tooLongResponse = userService.createUser(tooLongBody)
        val tooLongError = tooLongResponse?.error

        assertNotNull(tooLongError, "Expected error but response was $tooLongError")

        assertEquals(CreateUserError.UsernameError.TOO_LONG, tooLongError.usernameError)
        assertEquals(CreateUserError.PasswordError.TOO_LONG, tooLongError.passwordError)
    }

    @Test
    fun testCreateUserDuplicateUsernameValidations(): Unit = runBlocking {
        val existingUser = User(
            id = "test",
            username = "test",
            displayName = "test",
        )
        queries.users["test"] = existingUser

        val body = CreateUserBody(
            username = existingUser.username,
            password = "123456",
            inviteCode = null,
        )

        val response = userService.createUser(body)
        val error = response?.error

        assertNotNull(error, "Expected error but response was $response")

        assertEquals(CreateUserError.UsernameError.ALREADY_EXISTS, error.usernameError)
        assertNull(error.passwordError)
    }

    @Test
    fun testCreateFirstUserWithoutInviteCodeSucceeds(): Unit = runBlocking {
        val body = CreateUserBody(
            username = "firstUser",
            password = "123456",
            inviteCode = null,
        )

        val response = userService.createUser(body)
        val success = response?.success

        assertNotNull(success, "Expected success but response was $response")
    }

    @Test
    fun testCreateSecondUserWithoutInviteCodeFails(): Unit = runBlocking {
        queries.users["test"] = User(
            id = "test",
            username = "test",
            displayName = "test",
        )

        val body = CreateUserBody(
            username = "seconduser",
            password = "123456",
            inviteCode = null,
        )

        val response = userService.createUser(body)

        assertNull(response, "Expected null response but was $response")
    }

    @Test
    fun testCreateSecondUserWithInviteCodeSucceeds(): Unit = runBlocking {
        queries.inviteCodes["test"] = InviteCode("test", emptySet(), "test")
        queries.users["test"] = User(
            id = "test",
            username = "test",
            displayName = "test",
        )

        val body = CreateUserBody(
            username = "seconduser",
            password = "123456",
            inviteCode = "test",
        )

        val response = userService.createUser(body)
        val success = response?.success

        assertNotNull(success, "Expected success response but was $response")
    }
    // </editor-fold>

    // <editor-fold desc="Create Session Tests">
    @Test
    fun testCreateSessionUsernameInvalid(): Unit = runBlocking {
        val body = CreateSessionBody(
            username = "",
            password = "",
        )

        val result = userService.createSession(body, null)
        val error = result?.error

        assertNotNull(error, "Expected result to be error but was $result")

        assertEquals(CreateSessionError.USERNAME_INVALID, error)
    }

    @Test
    fun testCreateSessionPasswordInvalid(): Unit = runBlocking {
        val body = CreateSessionBody(
            username = "helloworld",
            password = "",
        )

        val result = userService.createSession(body, null)
        val error = result?.error

        assertNotNull(error, "Expected result to be error but was $result")

        assertEquals(CreateSessionError.PASSWORD_INVALID, error)
    }

    @Test
    fun testCreateSessionUsernameNotFound(): Unit = runBlocking {
        queries.users["test"] = User(
            id = "test",
            username = "test",
            displayName = "test",
        )
        val body = CreateSessionBody(
            username = "test1",
            password = "123456",
        )

        val result = userService.createSession(body, null)
        val error = result?.error

        assertNotNull(error, "Expected result to be error but was $result")

        assertEquals(CreateSessionError.USERNAME_NOT_FOUND, error)
    }

    @Test
    fun testCreateSessionPasswordIncorrect(): Unit = runBlocking {
        queries.users["test"] = User(
            id = "test",
            username = "test",
            displayName = "test",
        )
        queries.userCredentials["test"] = UserCredentials(
            id = "test",
            password = userService.hashPassword("123456"),
            permissions = emptySet(),
        )
        val body = CreateSessionBody(
            username = "test",
            password = "1234567",
        )

        val result = userService.createSession(body, null)
        val error = result?.error

        assertNotNull(error, "Expected result to be error but was $result")

        assertEquals(CreateSessionError.PASSWORD_INCORRECT, error)
    }
    // </editor-fold>

    // <editor-fold desc="Password Encryption Tests">
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
        val hashString = userService.hashPassword(password)
        assertNotEquals(password, hashString)
        assertTrue(
            hashString.startsWith("\$2y\$10\$"),
            "Result should be in BCrypt format but was '$hashString'"
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
                hashString = userService.hashPassword(password)
            )
        )
    }
    // </editor-fold>
}
