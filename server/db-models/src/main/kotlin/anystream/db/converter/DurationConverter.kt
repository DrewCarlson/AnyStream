/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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

import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.BindingGetResultSetContext
import org.jooq.BindingSetStatementContext
import org.jooq.Converter
import org.jooq.ConverterProvider
import org.jooq.impl.AbstractBinding
import kotlin.time.Duration


@Suppress("UNUSED")
class DurationBinding : AbstractBinding<String, Duration>() {
    private val convert = JooqDurationConverter()
    override fun converter(): Converter<String, Duration> {
        return convert
    }

    override fun get(ctx: BindingGetResultSetContext<Duration>) {
        val string = ctx.resultSet().getString(ctx.index())
        ctx.convert(converter()).value(string)
    }

    override fun set(ctx: BindingSetStatementContext<Duration>) {
        val string = ctx.convert(converter()).value()
        ctx.statement().setString(ctx.index(), string)
    }
}

@Suppress("UNUSED")
class JooqDurationConverter : Converter<String, Duration> {

    override fun fromType(): Class<String> = String::class.java

    override fun toType(): Class<Duration> = Duration::class.java

    override fun from(databaseObject: String?): Duration? {
        return databaseObject?.let(Duration::parseIsoStringOrNull)
    }

    override fun to(userObject: Duration?): String? {
        return userObject?.toIsoString()
    }
}

class DurationLongConverter : Converter<Duration, Long> {
    override fun from(databaseObject: Duration?): Long =
        databaseObject?.inWholeNanoseconds?.shl(1) ?: 0

    override fun to(userObject: Long?): Duration = error("unsupported mapping")

    override fun fromType(): Class<Duration> = Duration::class.java

    override fun toType(): Class<Long> = Long::class.java
}
