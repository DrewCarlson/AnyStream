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

import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.*
import org.jooq.impl.UpdatableRecordImpl
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.reactivestreams.Publisher


suspend inline fun <reified R : UpdatableRecordImpl<R>, reified T> DSLContext.newRecordAsync(
    table: Table<R>,
    source: R
): T {
    return transactionCoroutine {
        newRecord(table)
            .apply {
                from(source)
                store()
            }
            .intoType<T>()
    }
}

suspend fun <R : Record> DSLContext.fetchCountAsync(table: Table<R>, vararg condition: Condition): Int {
    return transactionCoroutine { fetchCount(table, *condition) }
}

suspend inline fun <reified K, reified V, R : Record> SelectWhereStep<R>.awaitIntoMap(): Map<K, V> {
    return fetchAsync().thenApplyAsync { it.intoMap(K::class.java, V::class.java) }.await()
}

suspend inline fun <reified T, R : Record> ResultQuery<R>.awaitInto(): List<T> {
    return fetchAsync().thenApplyAsync { it.into(T::class.java) }.await()
}

suspend inline fun <reified T> Publisher<out Record>.awaitFirstOrNullInto(): T? {
    return awaitFirstOrNull()?.intoType()
}

inline fun <reified T> Record.intoType(): T {
    return into(T::class.java) as T
}

inline fun <reified T, R : Record> Result<R>.intoType(): T {
    return into(T::class.java) as T
}
