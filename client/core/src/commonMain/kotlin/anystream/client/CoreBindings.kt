/*
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

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*
import io.ktor.client.engine.HttpClientEngine

internal const val INITIAL_SERVER_URL = "initial_server_url"

@ContributesTo(AppScope::class)
@BindingContainer
expect object PlatformCoreBindings {
    fun provideHttpClientEngine(): HttpClientEngine
}

@ContributesTo(AppScope::class)
@BindingContainer(includes = [PlatformCoreBindings::class])
object CoreBindings {
    @SingleIn(AppScope::class)
    @Provides
    fun provideHttpClient(engine: HttpClientEngine): HttpClient {
        return HttpClient(engine = engine)
    }

    @SingleIn(AppScope::class)
    @Provides
    fun provideSessionManager(dataStore: SessionDataStore): SessionManager {
        return SessionManager(dataStore = dataStore)
    }

    @SingleIn(AppScope::class)
    @Provides
    fun provideAnyStreamClient(
        @Named(INITIAL_SERVER_URL) serverUrl: String? = null,
        httpClient: HttpClient,
        sessionManager: SessionManager,
        dataStore: SessionDataStore,
    ): AnyStreamClient {
        return AnyStreamClient(
            serverUrl = serverUrl,
            httpClient = httpClient,
            sessionManager = sessionManager,
            dataStore = dataStore,
        )
    }
}
