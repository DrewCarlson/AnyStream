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
@file:OptIn(ExperimentalHazeMaterialsApi::class)

package anystream.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import anystream.client.AnyStreamClient
import anystream.router.*
import anystream.router.Router
import anystream.routing.Routes
import anystream.ui.components.BottomNavigation
import anystream.ui.home.HomeScreen
import anystream.ui.login.LoginScreen
import anystream.ui.login.WelcomeScreen
import anystream.ui.media.MediaScreen
import anystream.ui.movies.MoviesScreen
import anystream.ui.profile.DevicePairingScannerScreen
import anystream.ui.profile.ProfileScreen
import anystream.ui.theme.AppTheme
import anystream.ui.util.LocalImageProvider
import anystream.ui.util.asImageProvider
import anystream.ui.video.VideoPlayer
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val router = SharedRouter()

val LocalAnyStreamClient = compositionLocalOf<AnyStreamClient> { error("No AnyStream client provided") }

@Composable
fun App() {
    val client: AnyStreamClient = koinInject()
    val scope = rememberCoroutineScope()
    val backPressHandler = remember { BackPressHandler() }
    val hazeState = remember { HazeState() }

    var showBottomNavigation by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf<Routes?>(null) }

    CompositionLocalProvider(
        LocalBackPressHandler providesDefault backPressHandler,
        LocalAnyStreamClient provides client,
        LocalImageProvider provides client.asImageProvider(),
    ) {
        AppTheme {
            BundleScope(null) {
                val routeChannel = remember { Channel<Routes>() }
                val defaultRoute = when {
                    !client.isAuthenticated() -> Routes.Welcome
                    else -> Routes.Home
                }
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(WindowInsets.statusBars),
                    topBar = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .hazeEffect(
                                    state = hazeState,
                                    style = HazeMaterials.ultraThin(),
                                ) {
                                    progressive = HazeProgressive.verticalGradient(
                                        startIntensity = 0.6f,
                                        endIntensity = 0f,
                                    )
                                }
                        ) {

                            TopAppBar(
                                title = {},
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                            )
                        }
                    },

                    bottomBar = {
                        AnimatedVisibility(
                            visible = showBottomNavigation,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                            label = "bottom-navigation-visibility-animation",
                        ) {
                            BottomNavigation(
                                modifier = Modifier
                                    .hazeEffect(
                                        state = hazeState,
                                        style = HazeMaterials.regular(),
                                    ),
                                selectedRoute = selectedRoute ?: Routes.Home,
                                onRouteChanged = { scope.launch { routeChannel.send(it) } },
                            )
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .hazeSource(hazeState)
                            .fillMaxSize()
                    ) {
                        Router(
                            contextId = defaultRoute::class.simpleName.orEmpty(),
                            defaultRouting = defaultRoute,
                        ) { stack ->
                            LaunchedEffect(stack) {
                                router.setBackStack(stack)
                            }
                            LaunchedEffect("track-route-requests") {
                                routeChannel.consumeEach { route ->
                                    stack.newRoot(route)
                                }
                            }
                            LaunchedEffect("track-authentication-state") {
                                client.authenticated
                                    .collect { authed ->
                                        val isLoginRoute = listOf(
                                            Routes.Welcome,
                                            Routes.Login,
                                        ).contains(stack.last())
                                        if (!authed && !isLoginRoute) {
                                            stack.replace(Routes.Login)
                                        }
                                    }
                            }
                            val route = stack.last()
                            LaunchedEffect(route) {
                                val bottomNavRoots = listOf(
                                    Routes.Home,
                                    Routes.Profile,
                                )
                                showBottomNavigation = bottomNavRoots.contains(route)
                                selectedRoute = route
                            }
                            DisplayRoute(
                                route = route,
                                stack = stack,
                                padding = padding
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayRoute(
    route: Routes,
    stack: BackStack<Routes>,
    padding: PaddingValues,
) {
    val client = LocalAnyStreamClient.current
    when (route) {
        Routes.Welcome -> WelcomeScreen { stack.push(Routes.Login) }
        Routes.Login -> {
            LoginScreen(
                client = client,
                router = router,
                modifier = Modifier.imePadding(),
            )
        }

        Routes.Home -> {
            // Ignore top padding to draw behind TopAppBar and statusBars
            val bottomOnlyPadding = PaddingValues(
                bottom = padding.calculateBottomPadding(),
            )
            HomeScreen(
                modifier = Modifier
                    .padding(bottomOnlyPadding)
                    .consumeWindowInsets(bottomOnlyPadding),
                client = client,
                onMetadataClick = { metadataId ->
                    stack.push(Routes.Details(metadataId))
                },
                onPlayClick = { mediaLinkId ->
                    stack.push(Routes.Player(mediaLinkId))
                },
                onViewMoviesClicked = {
                    stack.push(Routes.Movies)
                },
            )
        }

        Routes.Movies -> {
            MoviesScreen(
                client = client,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                onMediaClick = { metadataId ->
                    stack.push(Routes.Details(metadataId))
                },
                onPlayMediaClick = { mediaLinkId ->
                    stack.push(Routes.Player(mediaLinkId))
                },
            )
        }

        is Routes.Details -> {
            MediaScreen(
                mediaId = route.metadataId,
                onPlayClick = { mediaLinkId ->
                    stack.push(Routes.Player(mediaLinkId))
                },
                onMetadataClick = { metadataId ->
                    stack.push(Routes.Details(metadataId))
                },
                onBackClicked = { stack.pop() },
            )
        }

        is Routes.Player -> {
            VideoPlayer(
                route = route,
                stack = stack,
                modifier = Modifier,
            )
        }
        Routes.PairingScanner -> {
            DevicePairingScannerScreen(
                // note: Don't provide modifiers consuming padding, enabling edge-to-edge display
                modifier = Modifier,
                onPairingCompleted = { stack.pop() },
                onPairingCancelled = { stack.pop() },
            )
        }

        Routes.Tv -> TODO("Tv route not implemented")
        Routes.Profile -> {
            ProfileScreen(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                onPairDeviceClicked = { stack.push(Routes.PairingScanner) }
            )
        }
    }
}
