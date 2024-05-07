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

import anystream.db.*
import anystream.models.*
import anystream.models.api.*
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.datetime.Clock
import org.jooq.DSLContext
import kotlin.test.*


class UserServiceValidationTest : FunSpec({
    val db: DSLContext by bindTestDatabase()
    lateinit var dao: UserDao
    lateinit var inviteDao: InviteCodeDao
    lateinit var userService: UserService

    beforeTest {
        dao = UserDao(db)
        inviteDao = InviteCodeDao(db)
        userService = UserService(dao, inviteDao)
    }

    test("create user blank validations") {
        val body = CreateUserBody(
            username = "",
            password = "",
            inviteCode = null,
        )

        val response = userService.createUser(body)

        assertIs<CreateUserResponse.Error>(response, "Expected error but response was $response")

        assertEquals(CreateUserResponse.UsernameError.BLANK, response.usernameError)
        assertEquals(CreateUserResponse.PasswordError.BLANK, response.passwordError)
    }

    test("create user length validations") {
        // too short
        val tooShortBody = CreateUserBody(
            username = CharArray(USERNAME_LENGTH_MIN - 1) { '0' }.concatToString(),
            password = CharArray(PASSWORD_LENGTH_MIN - 1) { '0' }.concatToString(),
            inviteCode = null,
        )

        val response1 = userService.createUser(tooShortBody)

        assertIs<CreateUserResponse.Error>(response1, "Expected error but response was $response1")

        assertEquals(CreateUserResponse.UsernameError.TOO_SHORT, response1.usernameError)
        assertEquals(CreateUserResponse.PasswordError.TOO_SHORT, response1.passwordError)

        // too long
        val tooLongBody = CreateUserBody(
            username = CharArray(USERNAME_LENGTH_MAX + 1) { '0' }.concatToString(),
            password = CharArray(PASSWORD_LENGTH_MAX + 1) { '0' }.concatToString(),
            inviteCode = null,
        )

        val response2 = userService.createUser(tooLongBody)

        assertIs<CreateUserResponse.Error>(response2, "Expected error but response was $response2")

        assertEquals(CreateUserResponse.UsernameError.TOO_LONG, response2.usernameError)
        assertEquals(CreateUserResponse.PasswordError.TOO_LONG, response2.passwordError)
    }

    test("create user duplicate username validations") {
        val existingUser = User(
            id = ObjectId.get().toString(),
            username = "test",
            displayName = "test",
            passwordHash = "123456",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        dao.insertUser(existingUser, emptySet())

        val body = CreateUserBody(
            username = existingUser.username,
            password = "123456",
            inviteCode = null,
        )

        val response = userService.createUser(body)

        assertIs<CreateUserResponse.Error>(response, "Expected error but response was $response")
        assertEquals(CreateUserResponse.UsernameError.ALREADY_EXISTS, response.usernameError)
        assertNull(response.passwordError)
    }

    test("create first user without invite code succeeds") {
        val body = CreateUserBody(
            username = "firstUser",
            password = "123456",
            inviteCode = null,
        )

        val response = userService.createUser(body)

        assertIs<CreateUserResponse.Success>(response, "Expected success but response was $response")
    }

    test("create second user without invite code fails") {
        val existingUser = User(
            id = ObjectId.get().toString(),
            username = "test",
            displayName = "test",
            passwordHash = "123456",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
        dao.insertUser(existingUser, emptySet())

        val body = CreateUserBody(
            username = "seconduser",
            password = "123456",
            inviteCode = null,
        )

        val response = userService.createUser(body)

        assertIs<CreateUserResponse.Error>(response)
        assertTrue(response.signupDisabled)
    }

    test("create second user with invite code succeeds") {
        val existingUser = User(
            id = ObjectId.get().toString(),
            username = "test",
            displayName = "test",
            passwordHash = "",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
        val userId = dao.insertUser(existingUser, emptySet()).shouldNotBeNull().id

        inviteDao.createInviteCode("test", emptySet(), userId)

        val body = CreateUserBody(
            username = "seconduser",
            password = "123456",
            inviteCode = "test",
        )

        val response = userService.createUser(body)

        assertIs<CreateUserResponse.Success>(response, "Expected success response but was $response")
    }

    test("create session username invalid") {
        val body = CreateSessionBody(
            username = "",
            password = "",
        )

        val result = userService.createSession(body, null)
        assertIs<CreateSessionResponse.Error>(result)
        assertEquals(CreateSessionResponse.UsernameError.INVALID, result.usernameError)
    }

    test("create session password invalid") {
        val body = CreateSessionBody(
            username = "helloworld",
            password = "",
        )

        val result = userService.createSession(body, null)
        assertIs<CreateSessionResponse.Error>(result)
        assertEquals(CreateSessionResponse.PasswordError.INVALID, result.passwordError)
    }

    test("create session username not found") {
        val existingUser = User(
            id = ObjectId.get().toString(),
            username = "test",
            displayName = "test",
            passwordHash = "123456",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
        dao.insertUser(existingUser, emptySet())
        val body = CreateSessionBody(
            username = "test1",
            password = "123456",
        )

        val result = userService.createSession(body, null)

        assertIs<CreateSessionResponse.Error>(result)
        assertEquals(CreateSessionResponse.UsernameError.NOT_FOUND, result.usernameError)
    }

    test("create session password incorrect") {
        val existingUser = User(
            id = ObjectId.get().toString(),
            username = "test",
            displayName = "test",
            passwordHash = userService.hashPassword("123456"),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
        dao.insertUser(existingUser, emptySet())
        val body = CreateSessionBody(
            username = "test",
            password = "1234567",
        )

        val result = userService.createSession(body, null)
        assertIs<CreateSessionResponse.Error>(result)
        assertNull(result.usernameError)
        assertEquals(CreateSessionResponse.PasswordError.INCORRECT, result.passwordError)
    }

    test("hash password minimum length") {
        val password = CharArray(PASSWORD_LENGTH_MIN - 1) { '0' }.concatToString()
        assertFailsWith<IllegalArgumentException> {
            userService.hashPassword(password)
        }
    }

    test("hash password maximum length") {
        val password = CharArray(PASSWORD_LENGTH_MAX + 1) { '0' }.concatToString()
        assertFailsWith<IllegalArgumentException> {
            userService.hashPassword(password)
        }
    }

    test("hash password") {
        val password = "test123"
        val hashString = userService.hashPassword(password)
        assertNotEquals(password, hashString)
        assertTrue(
            hashString.startsWith("\$2y\$10\$"),
            "Result should be in BCrypt format but was '$hashString'",
        )
    }

    test("hash password verification") {
        val password = "test123"
        val bcryptString = "\$2y\$10\$uHBdQFb5YgpLrrJzFmPXteBgDxQn6zEoCzcYO1qfVOjYOvCUr.9Qq"

        assertFalse(
            userService.verifyPassword("wrong", bcryptString),
            "Invalid password should not verify",
        )
        assertTrue(
            userService.verifyPassword(password, bcryptString),
            "Valid password should verify",
        )

        assertTrue(
            userService.verifyPassword(
                checkPassword = password,
                hashString = userService.hashPassword(password),
            ),
        )
    }
})
