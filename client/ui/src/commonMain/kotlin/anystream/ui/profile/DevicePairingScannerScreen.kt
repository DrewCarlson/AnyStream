/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import anystream.ui.LocalAnyStreamClient
import anystream.ui.components.QrScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun DevicePairingScannerScreen(
    onPairingCompleted: () -> Unit,
    onPairingCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val client = LocalAnyStreamClient.current
    var scannedCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedCode) {
        val currentCode = scannedCode
        if (currentCode != null) {
            val user = client.user.filterNotNull().first()
            try {
                client.login(user.username, currentCode, pairing = true)
                onPairingCompleted()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
    }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        QrScanner(
            onScanned = { data ->
                if (scannedCode == null) {
                    scannedCode = data
                }
            },
            onClose = onPairingCancelled
        )
    }
}