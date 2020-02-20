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

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.client.SessionManager
import anystream.frontend.screens.HomeScreen
import anystream.frontend.screens.LoginScreen
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

fun webApp() = renderComposable(rootElementId = "root") {
    val client = AnyStreamClient(
        serverUrl = window.location.run { "$protocol//$host" },
        sessionManager = SessionManager(JsSessionDataStore)
    )
    Div(
        attrs = {
            id("main-panel")
            classes("h-100", "w-100")
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
            }
        },
    ) {
        Div { Navbar(client) }
        ContentContainer(client)
    }
}

@Composable
private fun ContentContainer(client: AnyStreamClient) {
    Div(
        attrs = {
            classes("container-fluid")
            style {
                flexGrow(1)
                flexShrink(1)
                property("flex-basis", "auto")
                property("overflow-y", "auto")
            }
        }
    ) {
        val isAuthenticated = client.authenticated.collectAsState(client.isAuthenticated())
        if (isAuthenticated.value) {
            HomeScreen(client)
        } else {
            LoginScreen(client)
        }
    }
}
