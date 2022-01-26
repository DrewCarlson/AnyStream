/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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
package anystream.db.mappers

import anystream.json
import anystream.models.Permission
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.jdbi.v3.core.argument.AbstractArgumentFactory
import org.jdbi.v3.core.argument.Argument
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.mapper.ColumnMapper
import org.jdbi.v3.core.statement.StatementContext
import java.sql.ResultSet
import java.sql.Types

object PermissionMappers {
    object Column : ColumnMapper<Permission> {
        override fun map(r: ResultSet, col: Int, ctx: StatementContext): Permission {
            return json.decodeFromString(r.getString(col))
        }
    }
    val Argument = object : AbstractArgumentFactory<Permission>(Types.VARCHAR) {
        override fun build(value: Permission, config: ConfigRegistry): Argument {
            return Argument { position, statement, _ ->
                statement.setString(position, json.encodeToString(value))
            }
        }
    }

    object SetColumn : ColumnMapper<Set<Permission>> {
        override fun map(r: ResultSet, col: Int, ctx: StatementContext): Set<Permission> {
            return json.decodeFromString<List<Permission>>(r.getString(col)).toSet()
        }
    }
    val SetArgument = object : AbstractArgumentFactory<Set<Permission>>(Types.VARCHAR) {
        override fun build(value: Set<Permission>, config: ConfigRegistry): Argument {
            return Argument { position, statement, _ ->
                statement.setString(position, json.encodeToString(value))
            }
        }
    }
}
