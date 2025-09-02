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
package anystream.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import anystream.client.AnyStreamClient
import anystream.router.BackStack
import anystream.routing.Routes
import anystream.ui.generated.resources.Res
import anystream.ui.generated.resources.as_logo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    client: AnyStreamClient,
    backStack: BackStack<Routes>? = null,
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val authed by client.user.authenticated.collectAsState(client.user.isAuthenticated())
    TopAppBar(
        modifier = modifier,
        title = {
            Image(
                painter = painterResource(Res.drawable.as_logo),
                modifier = Modifier
                    .padding(all = 8.dp)
                    .size(width = 150.dp, height = 50.dp),
                contentDescription = null,
            )
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { backStack?.pop() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = {

            if (authed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxSize(),
                ) {
//                    val packageManager = LocalContext.current.packageManager
//                    val hasCamera = remember {
//                        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
//                    }
                    // TODO Re-enable camera detection
                    if (false) {
                        IconButton(onClick = { backStack?.push(Routes.PairingScanner) }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Pair a device.")
                        }
                    }

                    IconButton(onClick = { scope.launch { client.user.logout() } }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            }
        }
    )
}
