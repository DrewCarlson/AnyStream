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
package anystream.prefs

import org.w3c.dom.Storage

class JsPreferences(
    private val storage: Storage
) : Preferences {

    override val keys: Set<String>
        get() = List(storage.length, storage::key).filterNotNull().toSet()

    override val size: Int
        get() = storage.length

    override fun contains(key: String): Boolean {
        return storage.getItem(key) != null
    }

    override fun removeAll() {
        storage.clear()
    }

    override fun remove(key: String) {
        storage.removeItem(key)
    }

    override fun putInt(key: String, value: Int) {
        storage.setItem(key, value.toString())
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toInt() else defaultValue
    }

    override fun getIntOrNull(key: String): Int? {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toInt() else null
    }

    override fun putLong(key: String, value: Long) {
        storage.setItem(key, value.toString())
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toLong() else defaultValue
    }

    override fun getLongOrNull(key: String): Long? {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toLong() else null
    }

    override fun putString(key: String, value: String) {
        storage.setItem(key, value)
    }

    override fun getString(key: String, defaultValue: String): String {
        return if (contains(key)) checkNotNull(storage.getItem(key)) else defaultValue
    }

    override fun getStringOrNull(key: String): String? {
        return if (contains(key)) checkNotNull(storage.getItem(key)) else null
    }

    override fun putDouble(key: String, value: Double) {
        storage.setItem(key, value.toString())
    }

    override fun getDouble(key: String, defaultValue: Double): Double {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toDouble() else defaultValue
    }

    override fun getDoubleOrNull(key: String): Double? {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toDouble() else null
    }

    override fun putBoolean(key: String, value: Boolean) {
        storage.setItem(key, value.toString())
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toBoolean() else defaultValue
    }

    override fun getBooleanOrNull(key: String): Boolean? {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toBoolean() else null
    }

    override fun putFloat(key: String, value: Float) {
        storage.setItem(key, value.toString())
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toFloat() else defaultValue
    }

    override fun getFloatOrNull(key: String): Float? {
        return if (contains(key)) checkNotNull(storage.getItem(key)).toFloat() else null
    }
}
