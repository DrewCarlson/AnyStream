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

