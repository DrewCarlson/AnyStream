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
package anystream.presentation.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import anystream.client.AnyStreamClient
import anystream.presentation.core.Presenter
import anystream.presentation.core.rememberEventTrigger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class ProfileScreenProps(
    val onPairDeviceClicked: () -> Unit,
)

@SingleIn(AppScope::class)
@Inject
class ProfileScreenPresenter(
    private val client: AnyStreamClient,
) : Presenter<ProfileScreenProps, ProfileScreenModel> {
    @Composable
    override fun model(props: ProfileScreenProps): ProfileScreenModel {
        val scope = rememberCoroutineScope()
        val user by client.user.user.collectAsState(client.user.authedUser())

        val logoutTrigger = scope.rememberEventTrigger {
            client.user.logout()
        }

        return ProfileScreenModel(
            user = user,
            onPairDeviceClicked = props.onPairDeviceClicked,
            onLogoutClicked = logoutTrigger::trigger,
        )
    }
}
