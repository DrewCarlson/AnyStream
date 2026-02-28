/**
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
package anystream.presentation.app

import anystream.models.Permission
import anystream.models.UserPublic
import anystream.routing.CommonRouter
import anystream.routing.Routes

data class AppModel(
    val appUiModel: AppUiModel,
    val authState: AuthState,
)

sealed interface AuthState {
    data class Authed(
        val user: UserPublic,
        val permissions: Set<Permission>,
    ) : AuthState

    data object Unauthed : AuthState
}

data class AppProps(
    val inviteCode: String? = null,
    val externalRouter: CommonRouter? = null,
    val externalRoute: Routes? = null,
)
