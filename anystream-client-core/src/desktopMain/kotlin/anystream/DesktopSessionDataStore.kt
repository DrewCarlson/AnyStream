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
package anystream.client.anystream

import anystream.client.SessionDataStore
import java.util.prefs.Preferences

internal class DesktopSessionDataStore(
    private val defaults: Preferences = Preferences.userRoot().node("Anystream"),
) : SessionDataStore {

    override fun write(key: String, value: String) {
        // Session value too long for jvm
        if (key == "SESSION_TOKEN") {
            defaults.put(value.substring(0, 49), key)
            defaults.put(value.substring(49), "SESSION_TOKEN2")
            return
        }
        defaults.put(value, key)
    }

    override fun read(key: String): String? {
        // Session value too long for jvm
        if (key == "SESSION_TOKEN") {
            val tokenStart = defaults.get(key, null) ?: return null
            val tokenEnd = defaults.get("SESSION_TOKEN2", null) ?: return null
            return tokenStart + tokenEnd
        }
        return defaults.get(key, null)
    }

    override fun remove(key: String) {
        defaults.remove(key)
    }
}
