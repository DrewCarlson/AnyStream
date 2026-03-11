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
package anystream.presentation.app

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.presentation.core.Presenter
import anystream.presentation.core.ScreenModel
import anystream.presentation.home.HomeScreenPresenter
import anystream.presentation.home.HomeScreenProps
import anystream.presentation.library.LibraryScreenPresenter
import anystream.presentation.library.LibraryScreenProps
import anystream.presentation.login.LoginScreenPresenter
import anystream.presentation.login.LoginScreenProps
import anystream.presentation.media.MediaScreenPresenter
import anystream.presentation.media.MediaScreenProps
import anystream.presentation.pairing.PairingScannerScreenPresenter
import anystream.presentation.pairing.PairingScannerScreenProps
import anystream.presentation.player.VideoPlayerModel
import anystream.presentation.profile.ProfileScreenPresenter
import anystream.presentation.profile.ProfileScreenProps
import anystream.presentation.signup.SignupScreenPresenter
import anystream.presentation.signup.SignupScreenProps
import anystream.presentation.welcome.WelcomeScreenPresenter
import anystream.presentation.welcome.WelcomeScreenProps
import anystream.routing.CommonRouter
import anystream.routing.Routes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class AppUiProps(
    val externalRouter: CommonRouter? = null,
    val externalRoute: Routes? = null,
    val serverUrl: String? = null,
    val inviteCode: String? = null,
    val authState: AuthState,
)

@SingleIn(AppScope::class)
@Inject
class AppUiPresenter(
    private val client: AnyStreamClient,
    private val homeScreenPresenter: HomeScreenPresenter,
    private val libraryScreenPresenter: LibraryScreenPresenter,
    private val loginScreenPresenter: LoginScreenPresenter,
    private val mediaScreenPresenter: MediaScreenPresenter,
    private val pairingScannerScreenPresenter: PairingScannerScreenPresenter,
    private val profileScreenPresenter: ProfileScreenPresenter,
    private val signupScreenPresenter: SignupScreenPresenter,
    private val welcomeScreenPresenter: WelcomeScreenPresenter,
) : Presenter<AppUiProps, AppUiModel> {
    @Composable
    override fun model(props: AppUiProps): AppUiModel {
        val initialRoute = remember {
            if (client.user.isAuthenticated()) Routes.Home else Routes.Welcome
        }
        var routeStack by remember { mutableStateOf(listOf(initialRoute)) }

        val internalRouter = remember {
            object : CommonRouter {
                override fun replaceTop(route: Routes) {
                    routeStack = routeStack.dropLast(1) + route
                }

                override fun pushRoute(route: Routes) {
                    routeStack = routeStack + route
                }

                override fun replaceStack(routes: List<Routes>) {
                    routeStack = routes
                }

                override fun popCurrentRoute(): Boolean {
                    return if (routeStack.size > 1) {
                        routeStack = routeStack.dropLast(1)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        val router = props.externalRouter ?: internalRouter
        val currentRoute = props.externalRoute ?: routeStack.last()

        // Track authentication state — redirect to Login if session lost
        LaunchedEffect(Unit) {
            client.user
                .authenticated
                .collect { authed ->
                    val isLoginRoute = currentRoute == Routes.Welcome ||
                        currentRoute == Routes.Login
                    if (!authed && !isLoginRoute) {
                        router.replaceTop(Routes.Login)
                    }
                }
        }

        val bottomNavRoots = remember { listOf(Routes.Home, Routes.Profile) }
        val showBottomNavigation = bottomNavRoots.contains(currentRoute)

        val screenModel = produceScreenModel(props, currentRoute, router)

        return AppUiModel(
            screen = screenModel,
            showBottomNavigation = showBottomNavigation,
            selectedRoute = currentRoute,
            onNavigateToRoot = { route -> router.replaceStack(listOf(route)) },
            navigate = router::pushRoute,
            goBack = router::popCurrentRoute,
        )
    }

    @Composable
    private fun produceScreenModel(
        props: AppUiProps,
        route: Routes,
        router: CommonRouter,
    ): ScreenModel {
        return when (route) {
            Routes.Home -> {
                homeScreenPresenter.model(HomeScreenProps)
            }

            Routes.Login -> {
                loginScreenPresenter.model(
                    LoginScreenProps(
                        supportsPairing = false,
                        serverUrl = props.serverUrl,
                        onLoginComplete = { router.replaceStack(listOf(Routes.Home)) },
                    ),
                )
            }

            Routes.SignUp -> {
                signupScreenPresenter.model(
                    SignupScreenProps(
                        inviteCode = props.inviteCode,
                        serverUrl = props.serverUrl,
                        onSignupComplete = { router.replaceStack(listOf(Routes.Home)) },
                    ),
                )
            }

            is Routes.Details -> {
                mediaScreenPresenter.model(
                    MediaScreenProps(route.metadataId),
                )
            }

            is Routes.Library -> {
                libraryScreenPresenter.model(
                    LibraryScreenProps(route.libraryId),
                )
            }

            Routes.PairingScanner -> {
                pairingScannerScreenPresenter.model(
                    PairingScannerScreenProps(
                        onPairingCompleted = router::popCurrentRoute,
                        onPairingCancelled = router::popCurrentRoute,
                    ),
                )
            }

            is Routes.Player -> {
                VideoPlayerModel(
                    mediaLinkId = route.mediaLinkId,
                    onClose = router::popCurrentRoute,
                )
            }

            Routes.Profile -> {
                profileScreenPresenter.model(
                    ProfileScreenProps(
                        onPairDeviceClicked = { router.pushRoute(Routes.PairingScanner) },
                    ),
                )
            }

            Routes.Welcome -> {
                welcomeScreenPresenter.model(
                    WelcomeScreenProps(
                        onCtaClicked = { router.pushRoute(Routes.Login) },
                    ),
                )
            }
        }
    }
}
