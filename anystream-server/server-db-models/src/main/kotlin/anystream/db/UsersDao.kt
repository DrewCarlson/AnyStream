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
package anystream.db

import anystream.db.model.UserDb
import anystream.models.User
import kotlinx.datetime.Instant
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface UsersDao {

    @SqlQuery("SELECT * FROM users")
    fun all(): List<UserDb>

    @SqlQuery("SELECT * FROM users WHERE id IN (<ids>)")
    fun findByIds(@BindList("ids") ids: List<Int>): List<UserDb>

    @SqlQuery("SELECT * FROM users WHERE id = ?")
    fun findById(userId: Int): UserDb?

    @SqlQuery("SELECT * FROM users WHERE username = ?")
    fun findByUsername(username: String): UserDb?

    @SqlQuery("SELECT COUNT(id) FROM users")
    fun count(): Long

    @SqlUpdate
    @GetGeneratedKeys("id")
    @UseClasspathSqlLocator
    fun insertUser(
        @BindKotlin("user") user: User,
        passwordHash: String,
        createdAt: Instant,
    ): Int

    @SqlUpdate("delete FROM users WHERE id = ?")
    fun deleteById(userId: Int)
}
