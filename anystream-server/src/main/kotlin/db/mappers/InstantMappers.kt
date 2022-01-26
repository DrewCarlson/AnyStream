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

import kotlinx.datetime.Instant
import org.jdbi.v3.core.argument.AbstractArgumentFactory
import org.jdbi.v3.core.argument.Argument
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.mapper.ColumnMapper
import org.jdbi.v3.core.statement.StatementContext
import java.sql.ResultSet
import java.sql.Types

object InstantMappers {
    object Column : ColumnMapper<Instant?> {
        override fun map(r: ResultSet, col: Int, ctx: StatementContext): Instant? {
            return r.getString(col)?.run(Instant::parse)
        }
    }
    val Argument = object : AbstractArgumentFactory<Instant>(Types.INTEGER) {
        override fun build(value: Instant, config: ConfigRegistry): Argument {
            return Argument { position, statement, _ ->
                statement.setString(position, value.toString())
            }
        }
    }
}
