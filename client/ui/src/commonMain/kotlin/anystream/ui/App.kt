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
@file:OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalAnimationApi::class)

package anystream.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import anystream.di.AppGraph
import anystream.presentation.app.AppModel
import anystream.presentation.app.AppUiModel
import anystream.presentation.auth.login.LoginScreenModel
import anystream.presentation.auth.signup.SignupScreenModel
import anystream.presentation.home.HomeScreenModel
import anystream.presentation.library.LibraryScreenModel
import anystream.presentation.media.MediaScreenModel
import anystream.presentation.pairing.PairingScannerScreenModel
import anystream.presentation.player.VideoPlayerModel
import anystream.presentation.profile.ProfileScreenModel
import anystream.presentation.welcome.WelcomeScreenModel
import anystream.routing.Routes
import anystream.ui.components.BottomNavigation
import anystream.ui.home.HomeScreen
import anystream.ui.login.LoginScreen
import anystream.ui.login.WelcomeScreen
import anystream.ui.media.LibraryScreen
import anystream.ui.media.MediaScreen
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

val LocalAppGraph = compositionLocalOf<AppGraph> { error("No AppGraph provided") }

@Composable
fun App(
    appGraph: AppGraph,
    appModel: AppModel,
    modifier: Modifier = Modifier,
) {
    val client = appGraph.client
    val hazeState = remember { HazeState() }

    CompositionLocalProvider(
        LocalImageProvider provides client.asImageProvider(),
        LocalAppGraph provides appGraph,
    ) {
        AppTheme {
            Scaffold(
                modifier = modifier
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
                            },
                    ) {
                        TopAppBar(
                            title = {},
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    WindowInsets.statusBars
                                        .asPaddingValues()
                                        .calculateTopPadding(),
                                ),
                        )
                    }
                },
                bottomBar = {
                    AnimatedVisibility(
                        visible = appModel.appUiModel.showBottomNavigation,
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
                            selectedRoute = appModel.appUiModel.selectedRoute,
                            onRouteChange = appModel.appUiModel.onNavigateToRoot,
                        )
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .hazeSource(hazeState)
                        .fillMaxSize(),
                ) {
                    DisplayScreenModel(appModel.appUiModel, padding)
                }
            }
        }
    }
}

@Composable
private fun DisplayScreenModel(
    appUiModel: AppUiModel,
    padding: PaddingValues,
) {
    when (val screenModel = appUiModel.screen) {
        is WelcomeScreenModel -> {
            WelcomeScreen(model = screenModel)
        }

        is HomeScreenModel -> {
            // Ignore top padding to draw behind TopAppBar and statusBars
            val bottomOnlyPadding = PaddingValues(
                bottom = padding.calculateBottomPadding(),
            )
            HomeScreen(
                model = screenModel,
                modifier = Modifier
                    .padding(bottomOnlyPadding)
                    .consumeWindowInsets(bottomOnlyPadding),
                onMetadataClick = { metadataId ->
                    appUiModel.navigate(Routes.Details(metadataId))
                },
                onPlayClick = { mediaLinkId ->
                    appUiModel.navigate(Routes.Player(mediaLinkId))
                },
                onViewMoviesClick = { libraryId ->
                    appUiModel.navigate(Routes.Library(libraryId))
                },
                onViewTvShowsClick = { libraryId ->
                    appUiModel.navigate(Routes.Library(libraryId))
                },
            )
        }

        is LoginScreenModel -> {
            LoginScreen(
                model = screenModel,
                modifier = Modifier.imePadding(),
            )
        }

        is SignupScreenModel -> {
            Text("TODO: implement signup")
        }

        is ProfileScreenModel -> {
            ProfileScreen(
                model = screenModel,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
        }

        is MediaScreenModel -> {
            MediaScreen(
                model = screenModel,
                onPlayClick = { mediaLinkId ->
                    appUiModel.navigate(Routes.Player(mediaLinkId))
                },
                onMetadataClick = { metadataId ->
                    appUiModel.navigate(Routes.Details(metadataId))
                },
                onBackClick = { appUiModel.goBack() },
            )
        }

        is PairingScannerScreenModel -> {
            DevicePairingScannerScreen(
                model = screenModel,
                // note: Don't provide modifiers consuming padding, enabling edge-to-edge display
                modifier = Modifier,
            )
        }

        is LibraryScreenModel -> {
            LibraryScreen(
                model = screenModel,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                onMediaClick = { metadataId ->
                    appUiModel.navigate(Routes.Details(metadataId))
                },
                onPlayMediaClick = { mediaLinkId ->
                    appUiModel.navigate(Routes.Player(mediaLinkId))
                },
            )
        }

        is VideoPlayerModel -> {
            VideoPlayer(
                model = screenModel,
                modifier = Modifier,
            )
        }
    }
}
