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
package anystream.frontend.screens

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.models.api.CreateSessionError
import app.softwork.routingcompose.BrowserRouter
import com.soywiz.korio.async.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun LoginScreen(client: AnyStreamClient) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Div({
        style {
            classes("py-4")
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            justifyContent(JustifyContent.Center)
            alignItems(AlignItems.Center)
            property("gap", 12.px)
        }
    }) {
        Div { H3 { Text("Login") } }
        Div {
            Input(
                attrs = {
                    onTextInput { username = it.inputValue }
                    classes("form-control")
                    placeholder("Username")
                    type(InputType.Text)
                }
            )
        }
        Div {
            Input(
                attrs = {
                    onTextInput { password = it.inputValue }
                    classes("form-control")
                    placeholder("Password")
                    type(InputType.Password)
                }
            )
        }
        Div {
            error?.run { Text(this) }
        }
        Div {
            Button({
                classes("btn", "btn-primary")
                type(ButtonType.Button)
                onClick {
                    scope.launch {
                        error = null
                        val response = client.login(username, password)
                        error = when (response.error) {
                            CreateSessionError.USERNAME_INVALID -> "Invalid username"
                            CreateSessionError.USERNAME_NOT_FOUND -> "Username not found"
                            CreateSessionError.PASSWORD_INVALID -> "Invalid password"
                            CreateSessionError.PASSWORD_INCORRECT -> "Incorrect password"
                            null -> null
                        }
                    }
                }
            }) {
                Text("Confirm")
            }
        }
        Div {
            A(
                attrs = {
                    style {
                        property("cursor", "pointer")
                    }
                    onClick { BrowserRouter.navigate("/signup") }
                }
            ) {
                Text("Go to Signup")
            }
        }
    }
}