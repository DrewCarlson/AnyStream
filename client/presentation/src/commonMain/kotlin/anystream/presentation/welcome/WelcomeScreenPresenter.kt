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
package anystream.presentation.welcome

import androidx.compose.runtime.Composable
import anystream.presentation.core.Presenter
import anystream.presentation.core.ScreenModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class WelcomeScreenProps(
    val onCtaClicked: () -> Unit,
)

@SingleIn(AppScope::class)
@Inject
class WelcomeScreenPresenter : Presenter<WelcomeScreenProps, WelcomeScreenModel> {
    @Composable
    override fun model(props: WelcomeScreenProps): WelcomeScreenModel {
        return WelcomeScreenModel(
            onCtaClicked = props.onCtaClicked,
        )
    }
}

data class WelcomeScreenModel(
    val onCtaClicked: () -> Unit = {},
) : ScreenModel
