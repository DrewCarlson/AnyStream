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
package anystream.client

import java.util.prefs.Preferences

internal class DesktopSessionDataStore(
    private val prefs: Preferences = Preferences.userRoot().node("AnyStream"),
) : SessionDataStore {

    override fun write(key: String, value: String) {
        prefs.put(key, value)
    }

    override fun read(key: String): String? {
        return prefs.get(key, null)
    }

    override fun remove(key: String) {
        prefs.remove(key)
    }
}
