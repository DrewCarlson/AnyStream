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
import anystream.frontend.libs.QRCodeImage
import anystream.models.api.CreateSessionError
import anystream.models.api.PairingMessage
import app.softwork.routingcompose.BrowserRouter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun LoginScreen(client: AnyStreamClient) {
    val authMutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLocked by remember { mutableStateOf(false) }

    val pairingMessage by produceState<PairingMessage?>(null) {
        client.createPairingSession()
            .onEach { message ->
                if (message is PairingMessage.Authorized) {
                    client.createPairedSession(
                        (value as PairingMessage.Started).pairingCode,
                        message.secret,
                    )
                    BrowserRouter.navigate("/home")
                }
                value = message
            }
            .launchIn(this)
    }
    suspend fun login() {
        isLocked = true
        error = null
        val response = client.login(username, password)
        when {
            response.success != null -> BrowserRouter.navigate("/home")
            response.error != null -> {
                error = when (response.error) {
                    CreateSessionError.USERNAME_INVALID -> "Invalid username"
                    CreateSessionError.USERNAME_NOT_FOUND -> "Username not found"
                    CreateSessionError.PASSWORD_INVALID -> "Invalid password"
                    CreateSessionError.PASSWORD_INCORRECT -> "Incorrect password"
                    null -> null
                }
                isLocked = false
            }
        }
    }

    Div({
        style {
            classes("d-flex", "flex-column", "justify-content-center", "align-items-center", "py-4")
            property("gap", 12.px)
        }
    }) {
        Div { H3 { Text("Login") } }
        Div {
            Input(InputType.Text) {
                onInput { username = it.value }
                classes("form-control")
                placeholder("Username")
                type(InputType.Text)
                if (isLocked) disabled()
            }
        }
        Div {
            Input(InputType.Text) {
                onInput { password = it.value }
                classes("form-control")
                placeholder("Password")
                type(InputType.Password)
                if (isLocked) disabled()
            }
        }
        Div {
            error?.run { Text(this) }
        }
        Div {
            Button({
                classes("btn", "btn-primary")
                type(ButtonType.Button)
                if (isLocked) disabled()
                onClick {
                    scope.launch {
                        authMutex.withLock { login() }
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
                    onClick {
                        if (!isLocked) BrowserRouter.navigate("/signup")
                    }
                }
            ) {
                Text("Go to Signup")
            }
        }
        Div {
            if (pairingMessage is PairingMessage.Started) {
                QRCodeImage((pairingMessage as PairingMessage.Started).pairingCode) {
                    style {
                        width(300.px)
                        height(300.px)
                    }
                }
            }
        }
    }
}