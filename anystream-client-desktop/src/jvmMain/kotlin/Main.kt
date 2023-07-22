/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import anystream.client.configure
import anystream.ui.video.LocalAppWindow
import anystream.ui.video.prepareLibvlc
import kotlinx.coroutines.launch

fun main() = application {
    configure()

    LaunchedEffect(Unit) {
        launch { prepareLibvlc() }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "AnyStream",
        state = rememberWindowState(width = 1600.dp, height = 1200.dp),
        icon = painterResource("as_logo.xml"),
    ) {
        CompositionLocalProvider(LocalAppWindow provides window) {
            MainView {
                window.placement = if (it) WindowPlacement.Fullscreen else WindowPlacement.Floating
            }
        }
    }
}
