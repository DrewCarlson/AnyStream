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

import anystream.models.InviteCode
import anystream.models.Permission
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface InvitesDao {

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createTable()

    @SqlQuery("SELECT * FROM inviteCodes")
    fun all(): List<InviteCode>

    @SqlQuery("SELECT * FROM inviteCodes WHERE createdByUserId = ?")
    fun allForUser(userId: Int): List<InviteCode>

    @SqlQuery("SELECT count(id) FROM inviteCodes")
    fun count(): Long

    @SqlQuery("SELECT * FROM inviteCodes WHERE id = ?")
    fun findById(id: Long): InviteCode?

    @SqlQuery("SELECT * FROM inviteCodes WHERE secret = ?")
    fun findBySecret(secret: String): InviteCode?

    @SqlQuery("SELECT * from inviteCodes WHERE secret = ? AND createdByUserId = ?")
    fun findBySecretForUser(secret: String, userId: Int): InviteCode?

    @SqlUpdate("delete from inviteCodes WHERE id = ?")
    fun deleteById(id: Long)

    @SqlUpdate("delete from inviteCodes WHERE id = ? AND createdByUserId = ?")
    fun deleteByIdForUser(id: Long, userId: Int)

    @SqlUpdate("delete from inviteCodes WHERE secret = ?")
    fun deleteBySecret(secret: String)

    @SqlUpdate("delete from inviteCodes WHERE secret = ? AND createdByUserId = ?")
    fun deleteBySecretForUser(secret: String, userId: Int)

    @SqlUpdate("insert into inviteCodes (secret, permissions, createdByUserId) values (?, ?, ?)")
    @GetGeneratedKeys("id")
    fun createInviteCode(secret: String, permissions: Set<Permission>, createdByUserId: Int): Long
}
