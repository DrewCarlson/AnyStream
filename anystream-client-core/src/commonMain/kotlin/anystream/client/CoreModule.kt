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
package anystream.client

import io.ktor.client.*
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

internal expect fun platformCoreModule(): Module

internal const val INITIAL_SERVER_URL = "initial_server_url"

fun coreModule() = module {
    includes(platformCoreModule())

    single { HttpClient(engine = get()) }
    single { SessionManager(dataStore = get()) }
    single {
        AnyStreamClient(
            serverUrl = getOrNull(qualifier(INITIAL_SERVER_URL)),
            httpClient = get(),
            sessionManager = get()
        )
    }
}
