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
package anystream

import anystream.client.coreModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

private var koinApp: KoinApplication? = null

fun configure(body: KoinApplication.() -> Unit = {}) {
    if (koinApp == null) {
        koinApp = startKoin {
            modules(coreModule())
            body()
        }
    }
}
