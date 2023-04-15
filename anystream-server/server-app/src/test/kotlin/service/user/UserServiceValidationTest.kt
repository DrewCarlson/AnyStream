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

import anystream.db.InvitesDao
import anystream.db.PermissionsDao
import anystream.db.UsersDao
import anystream.db.mappers.registerMappers
import anystream.db.runMigrations
import anystream.models.*
import anystream.models.api.*
import kotlinx.coroutines.runBlocking
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.statement.Slf4JSqlLogger
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.attach
import java.io.File
import kotlin.test.*

class UserServiceValidationTest {

    private lateinit var dbHandle: Handle
    private lateinit var queries: UserServiceQueries
    private lateinit var userService: UserService

    @BeforeTest
    fun setup() {
        runMigrations("jdbc:sqlite:test.db")
        val jdbi = Jdbi.create("jdbc:sqlite:test.db").apply {
            setSqlLogger(Slf4JSqlLogger())
            installPlugin(SqlObjectPlugin())
            installPlugin(KotlinSqlObjectPlugin())
            installPlugin(KotlinPlugin())
            registerMappers()
        }
        dbHandle = jdbi.open()
        queries = UserServiceQueriesJdbi(
            usersDao = dbHandle.attach<UsersDao>(),
            permissionsDao = dbHandle.attach<PermissionsDao>(),
            invitesDao = dbHandle.attach<InvitesDao>(),
        )
        userService = UserService(queries)
    }

    @AfterTest
    fun tearDown() {
        dbHandle.close()
        File("test.db").delete()
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

        assertIs<CreateUserResponse.Error>(response, "Expected error but response was $response")

        assertEquals(CreateUserResponse.UsernameError.BLANK, response.usernameError)
        assertEquals(CreateUserResponse.PasswordError.BLANK, response.passwordError)
    }

    @Test
    fun testCreateUserLengthValidations(): Unit = runBlocking {
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

    @Test
    fun testCreateUserDuplicateUsernameValidations(): Unit = runBlocking {
        val existingUser = User(
            id = 1,
            username = "test",
            displayName = "test",
        )
        queries.createUser(existingUser, "", emptySet())

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

    @Test
    fun testCreateFirstUserWithoutInviteCodeSucceeds(): Unit = runBlocking {
        val body = CreateUserBody(
            username = "firstUser",
            password = "123456",
            inviteCode = null,
        )

        val response = userService.createUser(body)

        assertIs<CreateUserResponse.Success>(response, "Expected success but response was $response")
    }

    @Test
    fun testCreateSecondUserWithoutInviteCodeFails(): Unit = runBlocking {
        val existingUser = User(
            id = 1,
            username = "test",
            displayName = "test",
        )
        queries.createUser(existingUser, "", emptySet())

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
        val existingUser = User(
            id = 1,
            username = "test",
            displayName = "test",
        )
        queries.createUser(existingUser, "", emptySet())
        queries.createInviteCode("test", emptySet(), 1)

        val body = CreateUserBody(
            username = "seconduser",
            password = "123456",
            inviteCode = "test",
        )

        val response = userService.createUser(body)

        assertIs<CreateUserResponse.Success>(response, "Expected success response but was $response")
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
        assertIs<CreateSessionResponse.Error>(result)
        assertEquals(CreateSessionResponse.UsernameError.INVALID, result.usernameError)
    }

    @Test
    fun testCreateSessionPasswordInvalid(): Unit = runBlocking {
        val body = CreateSessionBody(
            username = "helloworld",
            password = "",
        )

        val result = userService.createSession(body, null)
        assertIs<CreateSessionResponse.Error>(result)
        assertEquals(CreateSessionResponse.PasswordError.INVALID, result.passwordError)
    }

    @Test
    fun testCreateSessionUsernameNotFound(): Unit = runBlocking {
        val existingUser = User(
            id = 1,
            username = "test",
            displayName = "test",
        )
        queries.createUser(existingUser, "", emptySet())
        val body = CreateSessionBody(
            username = "test1",
            password = "123456",
        )

        val result = userService.createSession(body, null)

        assertIs<CreateSessionResponse.Error>(result)
        assertEquals(CreateSessionResponse.UsernameError.NOT_FOUND, result.usernameError)
    }

    @Test
    fun testCreateSessionPasswordIncorrect(): Unit = runBlocking {
        val existingUser = User(
            id = 1,
            username = "test",
            displayName = "test",
        )
        queries.createUser(existingUser, userService.hashPassword("123456"), emptySet())
        val body = CreateSessionBody(
            username = "test",
            password = "1234567",
        )

        val result = userService.createSession(body, null)
        assertIs<CreateSessionResponse.Error>(result)
        assertEquals(CreateSessionResponse.PasswordError.INCORRECT, result.passwordError)
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
            "Result should be in BCrypt format but was '$hashString'",
        )
    }

    @Test
    fun testHashPasswordVerification() {
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
    // </editor-fold>
}
