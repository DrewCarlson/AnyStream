/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.screens

import androidx.compose.runtime.*
import anystream.models.*
import anystream.models.api.CreateUserResponse
import anystream.presentation.signup.SignupScreenModel
import anystream.presentation.signup.SignupScreenModel.State
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun SignupScreen(
    model: SignupScreenModel
) {
    val router = Router.current
    Div({
        classes("d-flex", "flex-column", "justify-content-center", "align-items-center", "py-4")
        style {
            property("gap", 12.px)
        }
    }) {
        Div { H3 { Text("Signup") } }
        Div {
            Input(InputType.Text) {
                onInput { model.onUsernameChanged(it.value) }
                classes("form-control")
                placeholder("Username")
                type(InputType.Text)
                if (model.isInputLocked) disabled()
            }
        }
        Div {
            Input(InputType.Text) {
                onInput { model.onPasswordChanged(it.value) }
                onKeyDown {
                    if (it.key == "Enter") {
                        it.preventDefault()
                        model.onSubmitSignup()
                    }
                }
                classes("form-control")
                placeholder("Password")
                type(InputType.Password)
                if (model.isInputLocked) disabled()
            }
        }
        Div {
            Input(InputType.Text) {
                value(model.inviteCode)
                onInput {
                    if (model.isInviteCodeLocked) {
                        return@onInput it.preventDefault()
                    }
                    model.onInviteCodeChanged(it.value)
                }
                classes("form-control")
                placeholder("Invite Code")
                type(InputType.Text)
                if (model.isInputLocked || model.isInviteCodeLocked) disabled()
            }
        }
        Div {
            model.signupError?.run {
                Text(
                    usernameError?.message
                        ?: passwordError?.message
                        ?: reason?.message
                        ?: "Unknown error"
                )
            }
        }
        Div {
            Button({
                classes("btn", "btn-primary")
                type(ButtonType.Button)
                if (model.isInputLocked) disabled()
                onClick {
                    model.onSubmitSignup()
                }
            }) {
                Text("Confirm")
            }
        }
        Div {
            A(
                href = "/login",
                attrs = {
                    style {
                        property("cursor", "pointer")
                    }
                    onClick {
                        it.preventDefault()
                        if (model.state == State.IDLE) {
                            router.navigate("/login")
                        }
                    }
                },
            ) {
                Text("Go to Login")
            }
        }
    }
}

private val CreateUserResponse.ErrorReason?.message: String?
    get() = when (this) {
        is CreateUserResponse.ErrorReason.MissingOidcGroup -> "Missing groups: $groups"
        CreateUserResponse.ErrorReason.SignupDisabled -> "Signup is disabled"
        null -> null
    }
private val CreateUserResponse.PasswordError?.message: String?
    get() = when (this) {
        CreateUserResponse.PasswordError.BLANK -> "Password cannot be blank"
        CreateUserResponse.PasswordError.TOO_SHORT -> "Password must be at least $PASSWORD_LENGTH_MIN characters."
        CreateUserResponse.PasswordError.TOO_LONG -> "Password must be $PASSWORD_LENGTH_MAX or fewer characters."
        null -> null
    }

private val CreateUserResponse.UsernameError?.message: String?
    get() = when (this) {
        CreateUserResponse.UsernameError.BLANK -> "Username cannot be blank"
        CreateUserResponse.UsernameError.TOO_SHORT -> "Username must be at least $USERNAME_LENGTH_MIN characters."
        CreateUserResponse.UsernameError.TOO_LONG -> "Username must be $USERNAME_LENGTH_MAX or fewer characters."
        CreateUserResponse.UsernameError.ALREADY_EXISTS -> "Username already exists."
        null -> null
    }
