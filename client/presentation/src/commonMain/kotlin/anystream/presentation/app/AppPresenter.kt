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
package anystream.presentation.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import anystream.client.AnyStreamClient
import anystream.models.Permission
import anystream.models.UserPublic
import anystream.presentation.core.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.combine

@SingleIn(AppScope::class)
@Inject
class AppPresenter(
    private val appUiPresenter: AppUiPresenter,
    private val client: AnyStreamClient,
) : Presenter<AppProps, AppModel> {
    @Composable
    override fun model(props: AppProps): AppModel {
        val initialAuthState = remember {
            userDetailsToAuthState(
                user = client.user.authedUser(),
                permissions = client.user.userPermissions(),
            )
        }
        val authState by remember {
            combine(
                client.user.user,
                client.user.permissions,
                ::userDetailsToAuthState,
            )
        }.collectAsState(initialAuthState)
        val serverUrl by client.core.serverUrlFlow.collectAsState()
        val appUiModel = appUiPresenter.model(
            AppUiProps(
                serverUrl = serverUrl,
                externalRoute = props.externalRoute,
                externalRouter = props.externalRouter,
                authState = authState,
                inviteCode = props.inviteCode,
            )
        )
        return AppModel(
            appUiModel = appUiModel,
            authState = authState,
        )
    }

    private fun userDetailsToAuthState(
        user: UserPublic?,
        permissions: Set<Permission>?,
    ): AuthState {
        return if (user == null || permissions == null) {
            AuthState.Unauthed
        } else {
            AuthState.Authed(
                user = user,
                permissions = permissions,
            )
        }
    }
}
