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

import org.jooq.Converter
import org.jooq.ConverterProvider
import org.jooq.impl.DefaultConverterProvider
import kotlin.time.Duration

class JooqConverterProvider : ConverterProvider {

    private val delegate: ConverterProvider = DefaultConverterProvider()

    override fun <T : Any?, U : Any?> provide(tType: Class<T>, uType: Class<U>): Converter<T, U>? {
        if (tType == Duration::class.java && uType == Long::class.java) {
            @Suppress("UNCHECKED_CAST")
            return DurationLongConverter() as Converter<T, U>
        }

        return delegate.provide(tType, uType)
    }
}
