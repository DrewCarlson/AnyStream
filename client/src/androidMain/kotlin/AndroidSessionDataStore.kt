/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.core

import android.content.SharedPreferences
import androidx.core.content.edit
import anystream.client.SessionDataStore

class AndroidSessionDataStore(
    private val prefs: SharedPreferences
) : SessionDataStore {

    override fun write(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    override fun read(key: String): String? {
        return prefs.getString(key, null)
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }
}
