/**
 * AnyStream
 * Copyright (C) 2022 Amit Goel
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
package helper

import android.app.Application
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.plugins.databases.DatabasesFlipperPlugin
import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin
import com.facebook.flipper.plugins.network.FlipperOkhttpInterceptor
import com.facebook.flipper.plugins.network.NetworkFlipperPlugin
import com.facebook.flipper.plugins.sharedpreferences.SharedPreferencesFlipperPlugin
import com.facebook.soloader.SoLoader
import okhttp3.Interceptor

object FlipperProvider {
    fun init(context: Application) {
        // Flipper init
        SoLoader.init(context, false)
        AndroidFlipperClient.getInstance(context).apply {
            addPlugin(networkFlipperPlugin)
            addPlugin(
                InspectorFlipperPlugin(context, DescriptorMapping.withDefaults())
            )
            addPlugin(SharedPreferencesFlipperPlugin(context))
            addPlugin(DatabasesFlipperPlugin(context))
        }.also { it.start() }
    }

    private val networkFlipperPlugin = NetworkFlipperPlugin()

    @Suppress("RedundantNullableReturnType")
    fun getFlipperOkhttpInterceptor(): Interceptor? {
        return FlipperOkhttpInterceptor(networkFlipperPlugin)
    }
}

