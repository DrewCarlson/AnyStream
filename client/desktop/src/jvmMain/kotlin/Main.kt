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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import anystream.client.configure
import anystream.ui.UiModule
import anystream.ui.generated.resources.Res
import anystream.ui.generated.resources.as_icon
import anystream.ui.video.LocalAppWindow
import anystream.ui.video.PlayerHandle
import anystream.ui.video.VlcjPlayerHandle
import anystream.ui.video.prepareLibvlc
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.dsl.module

fun main() = application {
    configure {
        modules(UiModule)
    }

    LaunchedEffect(Unit) {
        launch { prepareLibvlc() }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "AnyStream",
        state = rememberWindowState(width = 1600.dp, height = 1200.dp),
        icon = painterResource(Res.drawable.as_icon),
    ) {
        CompositionLocalProvider(LocalAppWindow provides window) {
            MainView()
        }
    }
}
