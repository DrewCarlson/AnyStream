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
package anystream.presentation.pairing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import anystream.client.AnyStreamClient
import anystream.presentation.core.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

data class PairingScannerScreenProps(
    val onPairingCompleted: () -> Unit,
    val onPairingCancelled: () -> Unit,
)

@SingleIn(AppScope::class)
@Inject
class PairingScannerScreenPresenter(
    private val client: AnyStreamClient,
) : Presenter<PairingScannerScreenProps, PairingScannerScreenModel> {
    @Composable
    override fun model(props: PairingScannerScreenProps): PairingScannerScreenModel {
        var scannedCode by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(scannedCode) {
            val currentCode = scannedCode
            if (currentCode != null) {
                val user = client.user.user
                    .filterNotNull()
                    .first()
                try {
                    client.user.login(user.username, currentCode, pairing = true)
                    props.onPairingCompleted()
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }
        }

        return PairingScannerScreenModel(
            onScanned = { data ->
                if (scannedCode == null) {
                    scannedCode = data
                }
            },
            onCancelled = props.onPairingCancelled,
        )
    }
}
