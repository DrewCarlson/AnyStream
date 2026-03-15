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
import anystream.presentation.auth.AuthScreenPresenter
import anystream.presentation.auth.AuthScreenProps
import anystream.presentation.auth.AuthScreenType
import anystream.presentation.core.Presenter
import anystream.presentation.core.ScreenModel
import anystream.presentation.home.HomeScreenPresenter
import anystream.presentation.home.HomeScreenProps
import anystream.presentation.library.LibraryScreenPresenter
import anystream.presentation.library.LibraryScreenProps
import anystream.presentation.media.MediaScreenPresenter
import anystream.presentation.media.MediaScreenProps
import anystream.presentation.pairing.PairingScannerScreenPresenter
import anystream.presentation.pairing.PairingScannerScreenProps
import anystream.presentation.player.VideoPlayerModel
import anystream.presentation.profile.ProfileScreenPresenter
import anystream.presentation.profile.ProfileScreenProps
import anystream.presentation.welcome.WelcomeScreenPresenter
import anystream.presentation.welcome.WelcomeScreenProps
import anystream.routing.CommonRouter
import anystream.routing.Routes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.filter

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
    private val authScreenPresenter: AuthScreenPresenter,
    private val mediaScreenPresenter: MediaScreenPresenter,
    private val pairingScannerScreenPresenter: PairingScannerScreenPresenter,
    private val profileScreenPresenter: ProfileScreenPresenter,
    private val welcomeScreenPresenter: WelcomeScreenPresenter,
) : Presenter<AppUiProps, AppUiModel> {
    @Composable
    override fun model(props: AppUiProps): AppUiModel {
        val internalRouter = remember {
            val initialRoute = when {
                client.user.isAuthenticated() -> Routes.Home
                else -> Routes.Welcome
            }
            ComposeRouter(initialRoute)
        }

        val router = props.externalRouter ?: internalRouter
        val currentRoute by remember(props.externalRoute) {
            derivedStateOf {
                props.externalRoute ?: internalRouter.stack[internalRouter.stack.lastIndex]
            }
        }

        // Track authentication state — redirect to Login if session lost
        LaunchedEffect(currentRoute) {
            client.user
                .authenticated
                .filter { authed ->
                    !authed && !Routes.isOnboardingRoute(currentRoute)
                }.collect {
                    router.replaceStack(Routes.LOGIN_STACK)
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

            Routes.Login,
            Routes.SignUp,
            -> {
                val authType = remember(route) { AuthScreenType.fromRoute(route) }
                authScreenPresenter.model(
                    AuthScreenProps(
                        authType = authType,
                        inviteCode = props.inviteCode,
                        serverUrl = props.serverUrl.orEmpty(),
                        onAuthComplete = {
                            router.replaceStack(listOf(Routes.Home))
                        },
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
