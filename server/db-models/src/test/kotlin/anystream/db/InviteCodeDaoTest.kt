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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext

class InviteCodeDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    lateinit var userDao: UserDao
    lateinit var dao: InviteCodeDao

    beforeTest {
        userDao = UserDao(db)
        dao = InviteCodeDao(db)
    }

    test("create invite code") {
        val user = userDao.insertUser(createUserObject(), emptySet())
            .shouldNotBeNull()
        val inviteCode = dao.createInviteCode("secret", Permission.all, user.id)
            .shouldNotBeNull()

        inviteCode.secret shouldBe "secret"
        inviteCode.permissions shouldBe Permission.all
        inviteCode.createdByUserId shouldBe user.id
    }

    test("delete invite code") {
        val user = userDao.insertUser(createUserObject(), emptySet())
            .shouldNotBeNull()

        dao.createInviteCode("secret", Permission.all, user.id)
            .shouldNotBeNull()

        dao.deleteInviteCode("secret", null)
            .shouldBeTrue()

        dao.fetchInviteCodes(user.id)
            .shouldBeEmpty()
    }

    test("delete invite code with user id") {
        val user = userDao.insertUser(createUserObject(), emptySet())
            .shouldNotBeNull()

        dao.createInviteCode("secret", Permission.all, user.id)
            .shouldNotBeNull()

        dao.deleteInviteCode("secret", user.id)
            .shouldBeTrue()

        dao.fetchInviteCodes(user.id)
            .shouldBeEmpty()
    }

    test("delete invite code with incorrect user id") {
        val user = userDao.insertUser(createUserObject(), emptySet())
            .shouldNotBeNull()

        dao.createInviteCode("secret", Permission.all, user.id)
            .shouldNotBeNull()

        dao.deleteInviteCode("secret", "00000000000000")
            .shouldBeTrue()

        dao.fetchInviteCode("secret")
            .shouldNotBeNull()
    }

    test("user delete triggers invite code delete") {
        val user = userDao.insertUser(createUserObject(), emptySet())
            .shouldNotBeNull()

        repeat(3) { i ->
            dao.createInviteCode("secret-$i", Permission.all, user.id)
                .shouldNotBeNull()
        }

        userDao.deleteUser(user.id)
            .shouldBeTrue()

        dao.fetchInviteCodes(user.id)
            .shouldBeEmpty()
    }
})