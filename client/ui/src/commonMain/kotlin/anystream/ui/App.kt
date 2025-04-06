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
package anystream.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import anystream.client.AnyStreamClient
import anystream.router.BackPressHandler
import anystream.router.BundleScope
import anystream.router.LocalBackPressHandler
import anystream.router.Router
import anystream.router.SharedRouter
import anystream.routing.Routes
import anystream.ui.home.HomeScreen
import anystream.ui.login.LoginScreen
import anystream.ui.login.WelcomeScreen
import anystream.ui.media.MediaScreen
import anystream.ui.movies.MoviesScreen
import anystream.ui.theme.AppTheme
import anystream.ui.video.SharedVideoPlayer
import org.koin.compose.koinInject

private val router = SharedRouter()

val LocalAnyStreamClient = compositionLocalOf<AnyStreamClient> { error("No AnyStream client provided") }

@Composable
fun App() {
    val client: AnyStreamClient = koinInject()

    val backPressHandler = remember { BackPressHandler() }

    CompositionLocalProvider(
        LocalBackPressHandler providesDefault backPressHandler,
        LocalAnyStreamClient provides client,
    ) {
        AppTheme {
            BundleScope(null) {
                val defaultRoute = when {
                    !client.isAuthenticated() -> Routes.Welcome
                    else -> Routes.Home
                }
                Router(
                    contextId = defaultRoute::class.simpleName.orEmpty(),
                    defaultRouting = defaultRoute,
                ) { stack ->
                    LaunchedEffect(stack) {
                        router.setBackStack(stack)
                    }
                    LaunchedEffect("track-authentication-state") {
                        client.authenticated
                            .collect { authed ->
                                val isLoginRoute = stack.last() == Routes.Welcome
                                if (authed && isLoginRoute) {
                                    stack.replace(Routes.Home)
                                } else if (!authed && !isLoginRoute) {
                                    stack.replace(Routes.Login)
                                }
                            }
                    }
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            //.windowInsetsPadding(WindowInsets.systemBars)
                    ) {
                        when (val route = stack.last()) {
                            Routes.Welcome -> WelcomeScreen { stack.push(Routes.Login) }
                            Routes.Login -> LoginScreen(client, router)
                            Routes.Home -> HomeScreen(
                                client = client,
                                backStack = stack,
                                onMediaClick = { mediaLinkId ->
                                    if (mediaLinkId != null) {
                                        stack.push(Routes.Details(mediaLinkId))
                                    }
                                },
                                onContinueWatchingClick = { mediaLinkId ->
                                    if (mediaLinkId != null) {
                                        stack.push(Routes.Player(mediaLinkId))
                                    }
                                },
                                onViewMoviesClicked = {
                                    stack.push(Routes.Movies)
                                },
                            )

                            Routes.Movies -> MoviesScreen(
                                client = client,
                                onMediaClick = { mediaLinkId ->
                                    if (mediaLinkId != null) {
                                        stack.push(Routes.Details(mediaLinkId))
                                    }
                                },
                                onPlayMediaClick = { mediaLinkId ->
                                    if (mediaLinkId != null) {
                                        stack.push(Routes.Player(mediaLinkId))
                                    }
                                },
                                backStack = stack,
                            )

                            is Routes.Details -> MediaScreen(
                                client = client,
                                mediaId = route.mediaRefId,
                                onPlayClick = { mediaLinkId ->
                                    if (mediaLinkId != null) {
                                        stack.push(Routes.Player(mediaLinkId))
                                    }
                                },
                                backStack = stack,
                            )

                            is Routes.Player -> SharedVideoPlayer(route, stack, client)
                            Routes.PairingScanner -> TODO()
//                        Routes.PairingScanner -> PairingScanner(
//                            client = client,
//                            backStack = stack,
//                        )
                            Routes.Tv -> TODO("Tv route not implemented")
                        }
                    }
                }
            }
        }
    }
}
