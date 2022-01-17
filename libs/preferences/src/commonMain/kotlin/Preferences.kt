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

interface Preferences {

    val keys: Set<String>
    val size: Int

    fun contains(key: String): Boolean

    fun removeAll()
    fun remove(key: String)

    fun putInt(key: String, value: Int)
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun getIntOrNull(key: String): Int?

    fun putLong(key: String, value: Long)
    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun getLongOrNull(key: String): Long?

    fun putFloat(key: String, value: Float)
    fun getFloat(key: String, defaultValue: Float = 0f): Float
    fun getFloatOrNull(key: String): Float?

    fun putDouble(key: String, value: Double)
    fun getDouble(key: String, defaultValue: Double = 0.0): Double
    fun getDoubleOrNull(key: String): Double?

    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun getBooleanOrNull(key: String): Boolean?

    fun putString(key: String, value: String)
    fun getString(key: String, defaultValue: String = ""): String
    fun getStringOrNull(key: String): String?
}
