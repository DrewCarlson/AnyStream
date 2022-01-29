/**
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
package anystream.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import anystream.android.router.BackStack
import anystream.client.AnyStreamClient
import anystream.routing.Routes
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import io.ktor.client.plugins.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PairingScanner(
    client: AnyStreamClient,
    backStack: BackStack<Routes>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var launched = remember { false }
    val hasPermission = remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val scanRequest = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        scope.launch(Dispatchers.Default) {
            if (result is QRResult.QRSuccess) {
                val user = client.user.filterNotNull().first()
                try {
                    client.login(user.username, result.content.rawValue, pairing = true)
                } catch (e: ClientRequestException) {
                    e.printStackTrace()
                }
            }
            backStack.pop()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        hasPermission.value = granted
        if (!granted) {
            backStack.pop()
        }
    }

    LaunchedEffect(hasPermission.value) {
        if (!launched) {
            if (hasPermission.value) {
                scanRequest.launch(null)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            launched = true
        }
    }
}
