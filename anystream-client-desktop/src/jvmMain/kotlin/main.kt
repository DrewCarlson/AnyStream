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
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import anystream.SharedRes
import anystream.client.configure
import anystream.ui.video.LocalAppWindow
import anystream.ui.video.prepareLibvlc
import com.sun.javafx.application.PlatformImpl
import dev.icerock.moko.resources.compose.painterResource
import javafx.application.Platform

fun main() = application {
    configure()

    // NOTE: This listener is only set to keep the JavaFX Platform thread alive
    val finishListener = remember {
        object : PlatformImpl.FinishListener {
            override fun idle(implicitExit: Boolean) = Unit
            override fun exitCalled() = Unit
        }
    }

    LaunchedEffect(Unit) {
        PlatformImpl.addListener(finishListener)
        prepareLibvlc()
    }

    Window(
        onCloseRequest = {
            PlatformImpl.removeListener(finishListener)
            Platform.exit()
            exitApplication()
        },
        title = "AnyStream",
        state = rememberWindowState(width = 1600.dp, height = 1200.dp),
        icon = painterResource(SharedRes.images.as_icon),
    ) {
        CompositionLocalProvider(LocalAppWindow provides window) {
            MainView()
        }
    }
}