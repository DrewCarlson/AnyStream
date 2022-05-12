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
package anystream.frontend.screens

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.frontend.LocalAnyStreamClient
import anystream.frontend.libs.QRCodeImage
import anystream.frontend.routing.WebRouter
import anystream.frontend.util.createLoopController
import anystream.ui.login.*
import app.softwork.routingcompose.BrowserRouter
import kt.mobius.Mobius
import kt.mobius.SimpleLogger
import kt.mobius.flow.FlowMobius
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun LoginScreen() {
    val client = LocalAnyStreamClient.current
    val (modelState, eventConsumerState) = createLoopController {
        val factory = FlowMobius.loop(
            LoginScreenUpdate,
            LoginScreenHandler.create(client, WebRouter())
        ).logger(SimpleLogger("Login"))
        val startModel = LoginScreenModel.create(client.serverUrl, supportsPairing = true)
        Mobius.controller(factory, startModel, LoginScreenInit)
    }

    val model by remember { modelState }
    val eventConsumer by remember { eventConsumerState }

    Div({
        classes("d-flex", "flex-column", "justify-content-center", "align-items-center", "py-4")
        style {
            property("gap", 12.px)
        }
    }) {
        Div { H3 { Text("Login") } }
        Div {
            Input(InputType.Text) {
                onInput { eventConsumer.accept(LoginScreenEvent.OnUsernameChanged(it.value)) }
                classes("form-control")
                placeholder("Username")
                type(InputType.Text)
                if (model.isInputLocked()) disabled()
            }
        }
        Div {
            Input(InputType.Text) {
                onInput { eventConsumer.accept(LoginScreenEvent.OnPasswordChanged(it.value)) }
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
                onClick { eventConsumer.accept(LoginScreenEvent.OnLoginSubmit) }
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
                        if (model.state == LoginScreenModel.State.IDLE) {
                            BrowserRouter.navigate("/signup")
                        }
                    }
                }
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
