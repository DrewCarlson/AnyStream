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
import anystream.models.*
import anystream.models.api.CreateUserError
import app.softwork.routingcompose.BrowserRouter
import com.soywiz.korio.async.launch
import io.ktor.client.features.*
import io.ktor.http.*
import kotlinx.browser.window
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun SignupScreen(client: AnyStreamClient) {
    val authMutex = Mutex()
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val launchInviteCode = remember {
        Url(window.location.href).parameters["inviteCode"]
    }
    var inviteCode by remember { mutableStateOf(launchInviteCode) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }

    suspend fun signup() {
        try {
            val (success, error) = client.createUser(username, password, inviteCode)
            when {
                success != null -> Unit
                error != null -> {
                    locked = false
                    errorMessage = error.usernameError?.message ?: error.passwordError?.message
                }
            }
        } catch (e: ResponseException) {
            locked = false
            errorMessage = if (e.response.status == HttpStatusCode.Forbidden) {
                "A valid invite code is required."
            } else {
                e.printStackTrace()
                e.message
            }
        }
    }

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
        Div { H3 { Text("Signup") } }
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
            Input(
                value = inviteCode ?: "",
                attrs = {
                    onTextInput { inviteCode = it.inputValue }
                    classes("form-control")
                    placeholder("Invite Code")
                    type(InputType.Text)
                    if (!launchInviteCode.isNullOrBlank()) disabled()
                }
            )
        }
        Div {
            errorMessage?.run { Text(this) }
        }
        Div {
            Button({
                classes("btn", "btn-primary")
                type(ButtonType.Button)
                if (locked) disabled()
                onClick {
                    scope.launch {
                        authMutex.withLock { signup() }
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
                    onClick { BrowserRouter.navigate("/login") }
                }
            ) {
                Text("Go to Login")
            }
        }
    }
}

private val CreateUserError.PasswordError?.message: String?
    get() = when (this) {
        CreateUserError.PasswordError.BLANK -> "Password cannot be blank"
        CreateUserError.PasswordError.TOO_SHORT -> "Password must be at least $PASSWORD_LENGTH_MIN characters."
        CreateUserError.PasswordError.TOO_LONG -> "Password must be $PASSWORD_LENGTH_MAX or fewer characters."
        null -> null
    }

private val CreateUserError.UsernameError?.message: String?
    get() = when (this) {
        CreateUserError.UsernameError.BLANK -> "Username cannot be blank"
        CreateUserError.UsernameError.TOO_SHORT -> "Username must be at least $USERNAME_LENGTH_MIN characters."
        CreateUserError.UsernameError.TOO_LONG -> "Username must be $USERNAME_LENGTH_MAX or fewer characters."
        CreateUserError.UsernameError.ALREADY_EXISTS -> "Username already exists."
        null -> null
    }
