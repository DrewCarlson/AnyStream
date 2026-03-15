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

import anystream.client.AnyStreamClient
import anystream.presentation.auth.BaseOidcLauncher
import anystream.presentation.auth.OidcLauncher
import anystream.presentation.auth.OidcLauncher.Companion.MOBILE_CALLBACK_URI
import dev.zacsweers.metro.*
import platform.AuthenticationServices.*
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.darwin.NSObject

@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    binding = binding<OidcLauncher>(),
)
@Inject
class IosOidcLauncher(
    private val client: AnyStreamClient,
) : BaseOidcLauncher() {
    override fun launchOidcLogin() {
        val urlString = client.user.getOidcLoginUrl(MOBILE_CALLBACK_URI)
        val url = NSURL.URLWithString(urlString) ?: return
        val session = ASWebAuthenticationSession(
            uRL = url,
            callbackURLScheme = "anystream",
        ) { callbackUrl, _ ->
            if (callbackUrl != null) {
                onAuthResult(callbackUrl.toString())
            }
        }
        session.presentationContextProvider = PresentationContextProvider()

        session.start()
    }
}

private class PresentationContextProvider :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        return UIApplication.sharedApplication.keyWindow!!
    }
}
