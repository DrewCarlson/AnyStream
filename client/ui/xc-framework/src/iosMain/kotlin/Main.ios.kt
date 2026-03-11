/*
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
import androidx.compose.ui.window.ComposeUIViewController
import anystream.di.IosAppGraph
import anystream.presentation.app.AppProps
import anystream.ui.App
import anystream.ui.util.LocalSystemBarController
import anystream.ui.util.SystemBarController
import anystream.ui.util.SystemBarControllerExport
import dev.zacsweers.metro.createGraphFactory
import platform.UIKit.UIViewController

@Suppress("FunctionName", "Unused") // called from Swift
fun MainViewController(statusBarController: SystemBarControllerExport): UIViewController {
    val appGraph = createGraphFactory<IosAppGraph.Factory>().create()
    val actualSystemBarController = object : SystemBarController {
        override fun setSystemBars(hidden: Boolean) {
            statusBarController.setSystemBars(hidden)
        }
    }
    return ComposeUIViewController {
        val appModel = appGraph.appPresenter.model(AppProps())
        CompositionLocalProvider(
            LocalSystemBarController provides actualSystemBarController,
        ) {
            App(
                appGraph = appGraph,
                appModel = appModel,
            )
        }
    }
}
