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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext


class UserDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    lateinit var dao: UserDao

    beforeTest {
        dao = UserDao(db)
    }

    test("insert user") {
        val user = createUserObject()
        val newUser = dao.insertUser(user, emptySet())

        dao.fetchUsers()

        newUser.shouldNotBeNull()

        newUser.id shouldBe user.id
        newUser.username shouldBe user.username
        newUser.displayName shouldBe user.displayName
        newUser.passwordHash shouldBe user.passwordHash
    }

    test("insert user with permissions") {
        val user = createUserObject()
        val newUser = dao.insertUser(user, Permission.all)

        dao.fetchUsers()

        newUser.shouldNotBeNull()

        newUser.id shouldBe user.id
        newUser.username shouldBe user.username
        newUser.displayName shouldBe user.displayName
        newUser.passwordHash shouldBe user.passwordHash
    }

    test("fetch user by id") {
        val userObject = createUserObject()
        val id = dao.insertUser(userObject, emptySet())
            .shouldNotBeNull()
            .id

        val loadedUser = dao.fetchUser(id)

        loadedUser.shouldNotBeNull()

        loadedUser.id shouldBe id
        loadedUser.username shouldBe userObject.username
        loadedUser.displayName shouldBe userObject.displayName
        loadedUser.passwordHash shouldBe userObject.passwordHash
    }

    test("fetch users") {
        val userObjects = List(2, ::createUserObject)
        userObjects.forEach { user ->
            dao.insertUser(user, emptySet()).shouldNotBeNull()
        }

        val users = dao.fetchUsers()
        users.shouldHaveSize(2)

        userObjects.forEachIndexed { index, user ->
            val loadedUser = users[index]
            loadedUser.id shouldBe user.id
            loadedUser.username shouldBe user.username
            loadedUser.displayName shouldBe user.displayName
            loadedUser.passwordHash shouldBe user.passwordHash
        }
    }

    test("test delete user") {
        val user = dao.insertUser(createUserObject(), emptySet()).shouldNotBeNull()

        dao.deleteUser(user.id).shouldBeTrue()

        dao.fetchUser(user.id).shouldBeNull()
    }

    test("test count users") {
        repeat(5) { i ->
            dao.insertUser(createUserObject(i), emptySet()).shouldNotBeNull()
        }
        dao.countUsers() shouldBe 5
    }
})
