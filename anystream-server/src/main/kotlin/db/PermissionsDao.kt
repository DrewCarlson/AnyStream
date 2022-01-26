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

import anystream.db.model.PermissionDb
import anystream.models.InviteCode
import anystream.models.Permission
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface PermissionsDao {

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createTable()

    @SqlQuery("SELECT * FROM permissions WHERE userId = ?")
    fun allForUser(userId: Int): List<PermissionDb>

    @SqlQuery("SELECT * FROM permissions WHERE userId = ? AND value = ?")
    fun find(userId: Int, value: String): PermissionDb?

    @SqlUpdate("DELETE FROM permissions WHERE userId = ? AND value = ?")
    fun delete(userId: Int, value: String)

    @SqlUpdate("DELETE FROM permissions WHERE userId = ?")
    fun deleteAllForUser(userId: Int)

    @SqlUpdate("INSERT INTO permissions (userId, value) VALUES (:userId, :permission)")
    fun insertPermission(userId: Int, permission: Permission)
}
