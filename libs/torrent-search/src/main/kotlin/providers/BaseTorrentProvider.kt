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
package drewcarlson.torrentsearch.providers

import drewcarlson.torrentsearch.TorrentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

abstract class BaseTorrentProvider(
    enabledByDefault: Boolean = true
) : TorrentProvider, CoroutineScope {

    private var enabled = enabledByDefault

    override val coroutineContext: CoroutineContext =
        Dispatchers.Default + SupervisorJob()

    final override val isEnabled: Boolean = enabled

    override fun enable(
        username: String?,
        password: String?,
        cookies: List<String>
    ) {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }
}
