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
package anystream.prefs

private typealias JvmPrefs = java.util.prefs.Preferences

class JvmPreferences(
    private val delegate: JvmPrefs,
) : Preferences {

    override val keys: Set<String> get() = delegate.keys().toSet()
    override val size: Int get() = delegate.keys().size

    override fun contains(key: String): Boolean = key in delegate.keys()

    override fun removeAll(): Unit = delegate.clear()

    override fun remove(key: String): Unit = delegate.remove(key)

    override fun putInt(key: String, value: Int): Unit = delegate.putInt(key, value)

    override fun getInt(key: String, defaultValue: Int): Int =
        delegate.getInt(key, defaultValue)

    override fun getIntOrNull(key: String): Int? =
        if (key in delegate.keys()) delegate.getInt(key, 0) else null

    override fun putLong(key: String, value: Long): Unit = delegate.putLong(key, value)

    override fun getLong(key: String, defaultValue: Long): Long =
        delegate.getLong(key, defaultValue)

    override fun getLongOrNull(key: String): Long? =
        if (key in delegate.keys()) delegate.getLong(key, 0L) else null

    override fun putString(key: String, value: String): Unit = delegate.put(key, value)

    override fun getString(key: String, defaultValue: String): String =
        delegate.get(key, defaultValue)

    override fun getStringOrNull(key: String): String? =
        if (key in delegate.keys()) delegate.get(key, "") else null

    override fun putFloat(key: String, value: Float): Unit = delegate.putFloat(key, value)

    override fun getFloat(key: String, defaultValue: Float): Float =
        delegate.getFloat(key, defaultValue)

    override fun getFloatOrNull(key: String): Float? =
        if (key in delegate.keys()) delegate.getFloat(key, 0f) else null

    override fun putDouble(key: String, value: Double): Unit = delegate.putDouble(key, value)

    override fun getDouble(key: String, defaultValue: Double): Double =
        delegate.getDouble(key, defaultValue)

    override fun getDoubleOrNull(key: String): Double? =
        if (key in delegate.keys()) delegate.getDouble(key, 0.0) else null

    override fun putBoolean(key: String, value: Boolean): Unit =
        delegate.putBoolean(key, value)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        delegate.getBoolean(key, defaultValue)

    override fun getBooleanOrNull(key: String): Boolean? =
        if (key in delegate.keys()) delegate.getBoolean(key, false) else null
}