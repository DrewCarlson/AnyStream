/*
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
import anystream.libs.QRCodeImage
import anystream.presentation.login.LoginScreenModel
import anystream.presentation.login.LoginScreenModel.State
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import web.window.window

@Composable
fun LoginScreen(model: LoginScreenModel) {
    val router = Router.current

    Div({
        classes("d-flex", "flex-column", "justify-content-center", "align-items-center", "py-4")
        style {
            property("gap", 12.px)
        }
    }) {
        Div { H3 { Text("Login") } }
        if (model.supportsPasswordAuth) {
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
                            model.onSubmitLogin()
                        }
                    }
                    classes("form-control")
                    placeholder("Password")
                    type(InputType.Password)
                    if (model.isInputLocked) disabled()
                }
            }
            Div {
                model.loginError?.run { Text(toString()) }
            }
            Div {
                Button({
                    classes("btn", "btn-primary")
                    type(ButtonType.Button)
                    if (model.isInputLocked) disabled()
                    onClick { model.onSubmitLogin() }
                }) {
                    Text("Confirm")
                }
            }
        }
        if (model.oidcProviderName != null) {
            Div {
                A(
                    attrs = {
                        style {
                            property("cursor", "pointer")
                        }
                        onClick {
                            if (model.state == State.IDLE) {
                                window.location.pathname = "/api/users/oidc/login"
                            }
                        }
                    },
                ) {
                    Text("Login with ${model.oidcProviderName}")
                }
            }
        }
        Div {
            A(
                href = "/signup",
                attrs = {
                    style {
                        property("cursor", "pointer")
                    }
                    onClick {
                        it.preventDefault()
                        if (model.state == State.IDLE) {
                            router.navigate("/signup")
                        }
                    }
                },
            ) {
                Text("Go to Signup")
            }
        }
        Div {
            val pairingCode = model.pairingCode
            if (pairingCode != null) {
                QRCodeImage(pairingCode) {
                    style {
                        width(300.px)
                        height(300.px)
                    }
                }
            }
        }
    }
}
