/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.frontend

import anystream.client.AnyStreamClient
import anystream.client.SessionManager
import anystream.models.Permissions
import anystream.models.Permissions.TORRENT_MANAGEMENT
import io.ktor.client.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.*
import io.kvision.Application
import io.kvision.core.*
import io.kvision.html.*
import io.kvision.module
import io.kvision.navbar.*
import io.kvision.navbar.nav
import io.kvision.panel.*
import io.kvision.startApplication
import io.kvision.utils.perc
import io.kvision.navigo.NavigoHooks
import io.kvision.routing.Routing
import io.kvision.routing.routing
import io.kvision.state.observableState
import kotlin.js.RegExp
import kotlin.reflect.KClass

class App : Application() {

    private val httpClient = HttpClient()
    private val client by lazy {
        AnyStreamClient(
            window.location.run { "$protocol//$host" },
            httpClient,
            SessionManager(JsSessionDataStore)
        )
    }

    private val scope = CoroutineScope(Default + SupervisorJob())
    private val activePage = MutableStateFlow<KClass<*>?>(null)

    override fun start() {
        Routing.init("${window.location.protocol}//${window.location.host}", false, "#!")
        root("kvapp") {
            vPanel(className = "main-panel") {
                val mainNavbar = addMainNavbar()
                val mainContainer = simplePanel()
                fun setPage(component: Widget) {
                    val oldComp = mainContainer
                        .getChildren()
                        .firstOrNull()

                    oldComp?.run(mainContainer::remove)
                    (oldComp as? CoroutineScope)?.cancel()

                    activePage.value = component::class
                    mainContainer.add(component)
                    if (component is PlayerPanel) {
                        mainNavbar.slideUp()
                    } else if (oldComp is PlayerPanel) {
                        mainNavbar.slideDown()
                    }
                }

                val authedRouteHook = object : NavigoHooks {
                    val scope = CoroutineScope(SupervisorJob() + Default)
                    override val before = { done: (Boolean) -> Unit ->
                        if (client.isAuthenticated()) {
                            done(true)
                        } else {
                            done(false)
                            routing.navigate("/login", true)
                        }
                    }

                    override val after: () -> Unit = {
                        client.authenticated
                            .filterNot { it }
                            .take(1)
                            .onEach { routing.navigate("/login", true) }
                            .launchIn(scope)
                    }

                    override val leave = { scope.coroutineContext.cancelChildren() }
                }
                routing.notFound({ _ ->
                    // TODO: Add not found page
                    setPage(VPanel(
                        justify = JustifyContent.CENTER,
                        alignItems = AlignItems.CENTER
                    ) {
                        height = 100.perc
                        width = 100.perc
                        h3("Not found")
                    })
                })
                routing
                    .on({ _ -> setPage(TmdbTab(client)) }, authedRouteHook) // TODO: Add homepage
                    .on("/tmdb", { _ -> setPage(TmdbTab(client)) }, authedRouteHook)
                    .on("/movies", { _ -> setPage(MoviesTab(client)) }, authedRouteHook)
                    .on("/tv", { _ -> setPage(TvTab(client)) }, authedRouteHook)
                    .on("/downloads", { _ -> setPage(DownloadsPage(client)) }, authedRouteHook)
                    .on("/play", { _ -> routing.navigate("/movies") }, authedRouteHook)
                    .on(RegExp("/play/(.*)"), { mediaRefId ->
                        setPage(PlayerPanel(mediaRefId.trim('/'), client))
                    }, authedRouteHook)
                    .on("/usermanager", { _ -> setPage(UserManagerPage(client)) }, authedRouteHook)
                    .on("/login", { _ -> setPage(LoginPage(client)) })
                    .on("/signup", { _ -> setPage(SignupPage(client)) })
                    .resolve(currentURL = window.location.pathname)
            }
        }
    }

    override fun dispose(): Map<String, Any> {
        scope.cancel()
        routing.destroy()
        httpClient.close()
        return super.dispose()
    }

    private fun Container.addMainNavbar(): Navbar {
        return navbar(
            nColor = NavbarColor.DARK,
            bgColor = BsBgColor.DARK
        ) {
            link(label = "", className = "navbar-brand") {
                image(src = "/images/as-logo.svg")
            }
            val links = mutableMapOf<KClass<*>, Link>()
            nav {
                links[TmdbTab::class] = navLink(
                    label = "Discover",
                    icon = "fas fa-search"
                ) {
                    onClick { routing.navigate("/tmdb") }
                }
                links[MoviesTab::class] = navLink(
                    label = "Movies",
                    icon = "fas fa-hdd"
                ) {
                    onClick { routing.navigate("/movies") }
                }
                links[TvTab::class] = navLink(
                    label = "TV",
                    icon = "fas fa-hdd"
                ) {
                    onClick { routing.navigate("/tv") }
                }
                links[DownloadsPage::class] = navLink(
                    label = "Downloads",
                    icon = "fas fa-cloud-download-alt"
                ) {
                    onClick { routing.navigate("/downloads") }
                    client.permissions
                        .map { permission ->
                            Permissions.check(TORRENT_MANAGEMENT, permission ?: emptySet())
                        }
                        .onEach { show ->
                            if (show) fadeIn() else fadeOut()
                        }
                        .launchIn(scope)
                }

                visible = client.isAuthenticated()
                client.authenticated
                    .drop(1)
                    .onEach { authed ->
                        if (authed) fadeIn() else fadeOut()
                    }
                    .launchIn(scope)
            }

            nav(
                client.permissions.observableState,
                rightAlign = true
            ) { permissions ->
                if (permissions.orEmpty().contains(Permissions.GLOBAL)) {
                    links[UserManagerPage::class] = navLink("", icon = "fas fa-users-cog") {
                        onClick { routing.navigate("/usermanager") }
                    }
                }

                if (permissions == null) {
                    links[LoginPage::class] = navLink("", icon = "fas fa-user-circle") {
                        onClick { routing.navigate("/login") }
                    }
                } else {
                    links.remove(LoginPage::class)
                    navLink("", icon = "fas fa-sign-out-alt") {
                        onClick {
                            GlobalScope.launch {
                                client.logout()
                                routing.navigate("/login")
                            }
                        }
                    }
                }
            }

            activePage.onEach { activeClass ->
                links.forEach { (kind, link) ->
                    if (activeClass == kind) {
                        link.addCssClass("active")
                        link.setAttribute("aria-current", "page")
                    } else {
                        link.removeCssClass("active")
                        link.removeAttribute("aria-current")
                    }
                }
            }.launchIn(scope)

        }
    }
}

fun main() {
    startApplication(::App, module.hot)
}
