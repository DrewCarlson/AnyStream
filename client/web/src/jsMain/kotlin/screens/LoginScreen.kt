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
import anystream.client.AnyStreamClient
import anystream.libs.QRCodeImage
import anystream.routing.WebRouter
import anystream.presentation.login.*
import anystream.util.get
import app.softwork.routingcompose.Router
import kt.mobius.SimpleLogger
import kt.mobius.compose.rememberMobiusLoop
import kt.mobius.flow.FlowMobius
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import web.window.window

@Composable
fun LoginScreen() {
    val router = Router.current
    val client = get<AnyStreamClient>()
    val (modelState, eventConsumer) = rememberMobiusLoop(
        LoginScreenModel.create(client.core.serverUrl, supportsPairing = true),
        LoginScreenInit
    ) {
        FlowMobius.loop(
            LoginScreenUpdate,
            LoginScreenHandler(client, WebRouter(router)),
        ).logger(SimpleLogger("Login"))
    }

    val model by remember { modelState }

    Div({
        classes("flex", "flex-col", "justify-content-center", "align-items-center", "py-4")
        style {
            property("gap", 12.px)
        }
    }) {
        Div { H3 { Text("Login") } }
        if (model.supportsPasswordAuth) {
            Div {
                Input(InputType.Text) {
                    onInput { eventConsumer(LoginScreenEvent.OnUsernameChanged(it.value)) }
                    classes("form-control")
                    placeholder("Username")
                    type(InputType.Text)
                    if (model.isInputLocked()) disabled()
                }
            }
            Div {
                Input(InputType.Text) {
                    onInput { eventConsumer(LoginScreenEvent.OnPasswordChanged(it.value)) }
                    classes("form-control")
                    placeholder("Password")
                    type(InputType.Password)
                    if (model.isInputLocked()) disabled()
                }
            }
            Div {
                model.loginError?.run { Text(toString()) }
            }
            Div {
                Button({
                    classes("btn", "btn-primary")
                    type(ButtonType.Button)
                    if (model.isInputLocked()) disabled()
                    onClick { eventConsumer(LoginScreenEvent.OnLoginSubmit) }
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
                            if (model.state == LoginScreenModel.State.IDLE) {
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
                attrs = {
                    style {
                        property("cursor", "pointer")
                    }
                    onClick {
                        if (model.state == LoginScreenModel.State.IDLE) {
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
