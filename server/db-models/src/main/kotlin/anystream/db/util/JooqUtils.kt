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
package anystream.db.util

import org.jooq.Record
import org.jooq.Result
import org.jooq.ResultQuery


inline fun <reified T> Record.intoType(): T {
    return into(T::class.java) as T
}

inline fun <reified T, R : Record> Result<R>.intoType(): T {
    return into(T::class.java) as T
}

inline fun <reified T, R : Record> ResultQuery<R>.fetchIntoType(): List<T> {
    return fetchInto(T::class.java)
}

inline fun <reified T, R : Record> ResultQuery<R>.fetchSingleIntoType(): T {
    return fetchSingleInto(T::class.java)
}

inline fun <reified T, R : Record> ResultQuery<R>.fetchOptionalIntoType(): T? {
    return fetchOptionalInto(T::class.java).orElse(null)
}
