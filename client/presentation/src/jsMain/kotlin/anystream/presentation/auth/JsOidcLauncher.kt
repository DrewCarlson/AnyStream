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
package anystream.presentation.auth

import anystream.client.AnyStreamClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    binding = binding<OidcLauncher>(),
)
@Inject
class JsOidcLauncher(
    private val client: AnyStreamClient,
) : BaseOidcLauncher() {
    override fun observeOidcResult(): Flow<OidcLaunchResult> {
        return super
            .observeOidcResult()
            .onStart {
                val url = window.location.href
                if (onAuthResult(url)) {
                    val newUrl = clearAuthResult(url)
                    window.history.replaceState(null, "", newUrl)
                }
            }
    }

    override fun launchOidcLogin() {
        val redirectUrl = window.location.href
        window.location.href = client.user.getOidcLoginUrl(redirectUrl)
    }
}
