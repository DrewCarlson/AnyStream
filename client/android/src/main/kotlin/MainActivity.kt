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
package anystream.android

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import anystream.router.BackPressHandler
import anystream.router.LocalBackPressHandler
import anystream.ui.App

class LeanbackActivity : MainActivity()
open class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(0, 0)
        )
        super.onCreate(savedInstanceState)
        val backPressHandler = BackPressHandler()
        onBackPressedDispatcher.addCallback(this) {
            if (!backPressHandler.handle()) {
                finish()
            }
        }
        setContent {
            CompositionLocalProvider(
                LocalBackPressHandler provides backPressHandler,
            ) {
                App()
            }
        }
    }
}

/*@Composable
fun AppTopBar(client: AnyStreamClient?, backStack: BackStack<Routes>?) {
    TopAppBar {
        val scope = rememberCoroutineScope()
        Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.as_logo),
            modifier = Modifier
                .padding(all = 8.dp)
                .size(width = 150.dp, height = 50.dp),
            contentDescription = null,
        )

        if (client != null) {
            val authed by client.authenticated.collectAsState(initial = client.isAuthenticated())
            if (authed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val packageManager = LocalContext.current.packageManager
                    val hasCamera = remember {
                        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                    }
                    if (hasCamera) {
                        IconButton(
                            onClick = { backStack?.push(Routes.PairingScanner) },
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(
                                    R.drawable.ic_qr_code_scanner,
                                ),
                                contentDescription = "Pair a device.",
                            )
                        }
                    }

                    IconButton(onClick = { scope.launch { client.logout() } }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign out",
                        )
                    }
                }
            }
        }
    }
}*/
