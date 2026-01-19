/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
package anystream.db

import anystream.models.Permission
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jooq.DSLContext
import kotlin.time.Duration.Companion.seconds


class UserDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val dao: UserDao by bindForTest({ UserDao(db) })

    test("insert user without permissions") {
        val user = createUserObject()
        val newUser = dao.insertUser(user, emptySet())

        newUser.shouldNotBeNull()

        newUser.id shouldBeEqual user.id
        newUser.username shouldBeEqual user.username
        newUser.displayName shouldBeEqual user.displayName
        newUser.passwordHash.shouldNotBeNull() shouldBeEqual user.passwordHash.shouldNotBeNull()
    }

    test("insert user with permissions") {
        val user = createUserObject()
        val newUser = dao.insertUser(user, Permission.all)

        newUser.shouldNotBeNull()

        newUser.id shouldBeEqual user.id
        newUser.username shouldBeEqual user.username
        newUser.displayName shouldBeEqual user.displayName
        newUser.passwordHash.shouldNotBeNull() shouldBeEqual user.passwordHash.shouldNotBeNull()
    }

    test("fetch user permissions") {
        val user = createUserObject()

        dao.insertUser(user, Permission.all).shouldNotBeNull()

        val permissions = dao.fetchPermissions(user.id)

        permissions.shouldContainAll(permissions)
    }

    test("fetch user permissions - invalid user id") {
        dao.fetchPermissions("not-a-real-user-id").shouldBeEmpty()
    }

    test("fetch user by id") {
        val userObject = createUserObject()
        val id = dao.insertUser(userObject, emptySet())
            .shouldNotBeNull()
            .id

        val loadedUser = dao.fetchUser(id)

        loadedUser.shouldNotBeNull()

        loadedUser.id shouldBeEqual id
        loadedUser.username shouldBeEqual userObject.username
        loadedUser.displayName shouldBeEqual userObject.displayName
        loadedUser.passwordHash.shouldNotBeNull() shouldBeEqual userObject.passwordHash.shouldNotBeNull()
    }

    test("fetch users") {
        val userObjects = List(2, ::createUserObject)
        userObjects.forEach { user ->
            dao.insertUser(user, emptySet()).shouldNotBeNull()
        }

        val dbUsers = dao.fetchUsers()
            .shouldHaveSize(2)
            .toList()

        userObjects.forEachIndexed { index, user ->
            dbUsers[index].shouldBe(user)
        }
    }

    test("fetch users when empty") {
        dao.fetchUsers().shouldBeEmpty()
    }

    test("delete user") {
        val user = dao.insertUser(createUserObject(), emptySet()).shouldNotBeNull()

        dao.deleteUser(user.id).shouldBeTrue()

        dao.fetchUser(user.id).shouldBeNull()
    }

    test("update user - display name") {
        val user = dao.insertUser(createUserObject(), emptySet()).shouldNotBeNull()
        delay(1.seconds)

        dao.updateUser(user.copy(displayName = "updated-username")).shouldBeTrue()

        dao.fetchUser(user.id)
            .shouldNotBeNull()
            .apply {
                id shouldBeEqual user.id
                username shouldBeEqual user.username
                displayName shouldBeEqual "updated-username"
                passwordHash.shouldNotBeNull() shouldBeEqual user.passwordHash.shouldNotBeNull()
                createdAt shouldBeEqual user.createdAt
                updatedAt shouldBeGreaterThan user.updatedAt
            }
    }

    test("update user - password name") {
        val user = dao.insertUser(createUserObject(), emptySet()).shouldNotBeNull()
        delay(1.seconds)

        dao.updateUser(user.copy(passwordHash = "updated-password-hash")).shouldBeTrue()

        val updatedUser = dao.fetchUser(user.id).shouldNotBeNull()

        updatedUser.id shouldBeEqual user.id
        updatedUser.username shouldBeEqual user.username
        updatedUser.displayName shouldBeEqual user.displayName
        updatedUser.passwordHash.shouldNotBeNull() shouldBeEqual "updated-password-hash"
        updatedUser.createdAt shouldBeEqual user.createdAt
        updatedUser.updatedAt shouldBeGreaterThan user.updatedAt
    }

    test("count users") {
        repeat(5) { i ->
            dao.insertUser(createUserObject(i), emptySet()).shouldNotBeNull()
        }
        dao.countUsers() shouldBe 5
    }
})
