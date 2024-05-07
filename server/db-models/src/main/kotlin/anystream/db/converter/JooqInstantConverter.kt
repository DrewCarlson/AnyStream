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
package anystream.db.converter

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jooq.BindingGetResultSetContext
import org.jooq.BindingSetStatementContext
import org.jooq.Converter
import org.jooq.impl.AbstractBinding
import org.jooq.impl.SQLDataType
import java.sql.Timestamp


@Suppress("UNUSED")
private class JooqInstantConverter : Converter<Timestamp, Instant> {

    override fun fromType(): Class<Timestamp> = Timestamp::class.java

    override fun toType(): Class<Instant> = Instant::class.java

    override fun from(databaseObject: Timestamp?): Instant? {
        return databaseObject?.toInstant()?.toKotlinInstant()
    }

    override fun to(userObject: Instant?): Timestamp? {
        return userObject?.toJavaInstant()?.run(Timestamp::from)
    }
}

val INSTANT_DATATYPE = SQLDataType.TIMESTAMP.asConvertedDataType(JooqInstantConverter())

@Suppress("UNUSED")
class JooqInstantBinding : AbstractBinding<Timestamp, Instant>() {
    private val convert = JooqInstantConverter()
    override fun converter(): Converter<Timestamp, Instant> {
        return convert
    }

    override fun get(ctx: BindingGetResultSetContext<Instant>) {
        ctx.convert(converter()).value(ctx.resultSet().getTimestamp(ctx.index()))
    }

    override fun set(ctx: BindingSetStatementContext<Instant>) {
        ctx.statement().setTimestamp(ctx.index(), ctx.convert(converter()).value())
    }
}
