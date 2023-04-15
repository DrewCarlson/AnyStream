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
package anystream

import anystream.client.SessionDataStore
import platform.Foundation.NSUserDefaults
import platform.Foundation.setValue

internal class IosSessionDataStore(
    private val defaults: NSUserDefaults,
) : SessionDataStore {
    override fun write(key: String, value: String) {
        defaults.setValue(value = value, forKey = key)
    }

    override fun read(key: String): String? {
        return defaults.stringForKey(key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
