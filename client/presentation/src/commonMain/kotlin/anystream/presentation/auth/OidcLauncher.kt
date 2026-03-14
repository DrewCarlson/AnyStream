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

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onEach

interface OidcLauncher {
    fun observeOidcResult(): Flow<OidcLaunchResult>

    fun launchOidcLogin()

    fun onAuthResult(urlString: String): Boolean

    fun clearAuthResult(urlString: String): String

    companion object {
        const val MOBILE_CALLBACK_URI = "anystream://auth/callback"
    }
}

abstract class BaseOidcLauncher : OidcLauncher {
    private val oidcResult = MutableSharedFlow<OidcLaunchResult>(replay = 1)

    internal fun extractAuthResult(urlString: String): OidcLaunchResult? {
        val url = Url(urlString)
        val token = url.parameters["token"]
        val isNewUser = url.parameters["isNewUser"]?.toBoolean()
        return when {
            token != null && isNewUser != null -> {
                OidcLaunchResult.LoginComplete(token, isNewUser)
            }

            else -> {
                null // no auth result details available, not an auth result
            }
        }
    }

    override fun clearAuthResult(urlString: String): String {
        return URLBuilder(urlString)
            .apply {
                parameters.remove("token")
                parameters.remove("isNewUser")
            }.buildString()
    }

    override fun onAuthResult(urlString: String): Boolean {
        val result = extractAuthResult(urlString) ?: return false
        oidcResult.tryEmit(result)
        return true
    }

    override fun observeOidcResult(): Flow<OidcLaunchResult> {
        return oidcResult.onEach { oidcResult.resetReplayCache() }
    }
}

sealed class OidcLaunchResult {
    data class LoginComplete(
        val token: String,
        val isNewUser: Boolean,
    ) : OidcLaunchResult()

    data class LoginFailed(
        val error: String,
    ) : OidcLaunchResult()
}
