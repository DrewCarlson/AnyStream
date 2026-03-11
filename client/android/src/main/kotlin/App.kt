/*
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.android

import android.app.Application
import anystream.di.AndroidAppGraph
import anystream.presentation.app.AppModel
import anystream.presentation.app.AppProps
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

class App : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val appGraph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>().create(this)
    }

    val appModels: StateFlow<AppModel> by lazy {
        scope.launchMolecule(RecompositionMode.Immediate) {
            appGraph.appPresenter.model(AppProps())
        }
    }

    override fun onCreate() {
        super.onCreate()
        InitVariantFeaturesImpl().init()
    }
}
