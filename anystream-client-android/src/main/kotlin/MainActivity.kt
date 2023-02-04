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

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import anystream.android.router.*
import anystream.android.ui.*
import anystream.client.AnyStreamClient
import anystream.routing.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class LeanbackActivity : MainActivity()
open class MainActivity : AppCompatActivity() {
    private val backPressHandler = BackPressHandler()

    private val androidRouter: AndroidRouter by inject()
    private val client: AnyStreamClient by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalBackPressHandler provides backPressHandler) {
                AppTheme {
                    BundleScope(savedInstanceState) {
                        val defaultRoute = when {
                            !client.isAuthenticated() -> Routes.Login
                            else -> Routes.Home
                        }
                        Router(defaultRouting = defaultRoute) { stack ->
                            val androidRouter = remember(stack) {
                                androidRouter.apply { setBackStack(stack) }
                            }
                            remember {
                                client.authenticated
                                    .onEach { authed ->
                                        val isLoginRoute = stack.last() == Routes.Login
                                        if (authed && isLoginRoute) {
                                            stack.replace(Routes.Home)
                                        } else if (!authed && !isLoginRoute) {
                                            stack.replace(Routes.Login)
                                        }
                                    }
                                    .launchIn(scope)
                            }
                            when (val route = stack.last()) {
                                Routes.Login -> LoginScreen(client, androidRouter)
                                Routes.Home -> HomeScreen(
                                    client = client,
                                    backStack = stack,
                                    onMediaClick = { mediaLinkId ->
                                        if (mediaLinkId != null) {
                                            stack.push(Routes.Player(mediaLinkId))
                                        }
                                    },
                                    onViewMoviesClicked = {
                                        stack.push(Routes.Movies)
                                    }
                                )
                                Routes.Movies -> MoviesScreen(
                                    client = client,
                                    onMediaClick = { mediaLinkId ->
                                        if (mediaLinkId != null) {
                                            stack.replace(Routes.Player(mediaLinkId))
                                        }
                                    },
                                    backStack = stack
                                )
                                Routes.Tv -> TODO("Tv route not implemented")
                                Routes.PairingScanner -> PairingScanner(
                                    client = client,
                                    backStack = stack
                                )
                                is Routes.Player -> PlayerScreen(
                                    client = client,
                                    mediaLinkId = route.mediaLinkId
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.saveLocal()
    }

    override fun onBackPressed() {
        if (!backPressHandler.handle()) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        androidRouter.setBackStack(null)
    }
}

@Composable
fun AppTopBar(client: AnyStreamClient?, backStack: BackStack<Routes>?) {
    TopAppBar {
        val scope = rememberCoroutineScope()
        Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.as_logo),
            modifier = Modifier
                .padding(all = 8.dp)
                .size(width = 150.dp, height = 50.dp),
            contentDescription = null
        )

        if (client != null) {
            val authed by client.authenticated.collectAsState(initial = client.isAuthenticated())
            if (authed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val packageManager = LocalContext.current.packageManager
                    val hasCamera = remember {
                        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                    }
                    if (hasCamera) {
                        IconButton(
                            onClick = { backStack?.push(Routes.PairingScanner) }
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_qr_code_scanner),
                                contentDescription = "Pair a device."
                            )
                        }
                    }

                    IconButton(onClick = {
                        scope.launch {
                            client.logout()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ExitToApp,
                            contentDescription = "Sign out"
                        )
                    }
                }
            }
        }
    }
}
