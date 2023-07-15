/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.router

actual class Bundle actual constructor() {

    private var bundle: android.os.Bundle = android.os.Bundle()

    constructor(bundle: android.os.Bundle) : this() {
        this.bundle = bundle
    }

    actual fun putBundle(key: String, bundle: Bundle) {
        this.bundle.putBundle(key, bundle.bundle)
    }

    actual fun getInt(key: String, value: Int): Int =
        bundle.getInt(key, value)

    actual fun putInt(key: String, value: Int) {
        bundle.putInt(key, value)
    }

    actual fun getBundle(key: String): Bundle? {
        return bundle.getBundle(key)?.run(::Bundle)
    }

    actual fun remove(key: String) {
        bundle.remove(key)
    }
}
