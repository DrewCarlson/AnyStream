/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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
package anystream.ui.auth

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import anystream.client.AnyStreamClient
import anystream.presentation.auth.BaseOidcLauncher
import anystream.presentation.auth.OidcLauncher
import anystream.presentation.auth.OidcLauncher.Companion.MOBILE_CALLBACK_URI
import anystream.ui.util.ActivityProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    binding = binding<OidcLauncher>(),
)
@Inject
class AndroidOidcLauncher(
    private val activityProvider: ActivityProvider,
    private val client: AnyStreamClient,
) : BaseOidcLauncher() {
    override fun launchOidcLogin() {
        val activity = activityProvider.activity ?: return
        val redirectUrl = Uri.encode(MOBILE_CALLBACK_URI)
        val url = client.user.getOidcLoginUrl(redirectUrl)
        CustomTabsIntent
            .Builder()
            .setEphemeralBrowsingEnabled(true)
            .setUrlBarHidingEnabled(true)
            .build()
            .launchUrl(activity, url.toUri())
    }
}
