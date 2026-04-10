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

import android.content.Context
import anystream.AndroidSessionDataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.*

@BindingContainer
actual object PlatformCoreBindings {
    @SingleIn(AppScope::class)
    @Provides
    actual fun provideHttpClientEngine(): HttpClientEngine = CIO.create { }

    @SingleIn(AppScope::class)
    @Provides
    fun provideDataStore(context: Context): SessionDataStore =
        AndroidSessionDataStore(
            prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE),
        )
}
