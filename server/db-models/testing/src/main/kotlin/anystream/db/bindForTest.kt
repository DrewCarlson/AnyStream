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
package anystream.db

import io.kotest.core.spec.DslDrivenSpec
import kotlin.properties.ReadOnlyProperty

/**
 * Bind the creation of a resource to the `beforeTest` and cleanup in `afterTest`.
 *
 * ```kotlin
 * class MyTest : FunSpec({
 *
 *   val myResource by bindForTest({ MyResource() }, { it.dispose() })
 *
 *   test("my test") {
 *      resource.doSomething()
 *   }
 * })
 * ```
 *
 * @param create The factory method to create the resource.
 * @param cleanup An optional cleanup method when discarding the resource.
 */
fun <T : Any> DslDrivenSpec.bindForTest(
    create: () -> T,
    cleanup: (T) -> Unit = {},
): ReadOnlyProperty<Nothing?, T> {
    var value: T? = null
    beforeTest { value = create() }
    afterTest {
        value?.run(cleanup)
        value = null
    }

    return ReadOnlyProperty { _, _ ->
        checkNotNull(value) { "Value used outside of test case" }
    }
}
