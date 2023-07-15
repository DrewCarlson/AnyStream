/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.ui.login

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import anystream.client.AnyStreamClient
import anystream.router.SharedRouter
import anystream.ui.components.AppTopBar
import anystream.ui.components.AutofillInput
import anystream.ui.components.onFocusStateChanged
import anystream.util.createLoopController
import kt.mobius.Mobius
import kt.mobius.SimpleLogger
import kt.mobius.flow.FlowMobius
import kt.mobius.functions.Consumer

@Composable
internal fun LoginScreen(client: AnyStreamClient, router: SharedRouter) {
    val (modelState, eventConsumerState) = createLoopController {
        val factory = FlowMobius.loop(
            LoginScreenUpdate,
            LoginScreenHandler.create(client, router),
        ).logger(SimpleLogger("Login"))
        val startModel = LoginScreenModel.create(client.serverUrl, supportsPairing = false)
        Mobius.controller(factory, startModel, LoginScreenInit)
    }

    Scaffold(
        topBar = { AppTopBar(client = null) },
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                FormBody(modelState.value, eventConsumerState.value, padding)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FormBody(
    model: LoginScreenModel,
    eventConsumer: Consumer<LoginScreenEvent>,
    paddingValues: PaddingValues,
) {
    var serverUrlValue by remember { mutableStateOf(TextFieldValue(model.serverUrl)) }
    var usernameValue by remember { mutableStateOf(TextFieldValue(model.username)) }
    var passwordValue by remember { mutableStateOf(TextFieldValue(model.password)) }

    val imePadding = with(LocalDensity.current) {
        WindowInsets.ime.getBottom(this).toDp()
    }
    val animatedImePadding by animateDpAsState(imePadding)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(bottom = animatedImePadding),
    ) {
        OutlinedTextField(
            value = serverUrlValue,
            placeholder = { Text("Server Url") },
            onValueChange = {
                serverUrlValue = it
                eventConsumer.accept(LoginScreenEvent.OnServerUrlChanged(it.text))
            },
            readOnly = model.isInputLocked(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
        )

        AutofillInput(
            autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
            onFill = { eventConsumer.accept(LoginScreenEvent.OnUsernameChanged(it)) },
        ) { node ->
            val autofill = LocalAutofill.current
            OutlinedTextField(
                value = usernameValue,
                placeholder = { Text(text = "Username") },
                onValueChange = {
                    usernameValue = it
                    eventConsumer.accept(LoginScreenEvent.OnUsernameChanged(it.text))
                },
                singleLine = true,
                readOnly = model.isInputLocked(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.onGloballyPositioned {
                    node.boundingBox = it.boundsInWindow()
                }.onFocusChanged {
                    autofill?.onFocusStateChanged(it, node)
                },
            )
        }
        AutofillInput(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { eventConsumer.accept(LoginScreenEvent.OnPasswordChanged(it)) },
        ) { node ->
            val autofill = LocalAutofill.current
            OutlinedTextField(
                value = passwordValue,
                placeholder = { Text(text = "Password") },
                visualTransformation = PasswordVisualTransformation(),
                onValueChange = {
                    passwordValue = it
                    eventConsumer.accept(LoginScreenEvent.OnPasswordChanged(it.text))
                },
                singleLine = true,
                readOnly = model.isInputLocked(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
                modifier = Modifier.onGloballyPositioned {
                    node.boundingBox = it.boundsInWindow()
                }.onFocusChanged {
                    autofill?.onFocusStateChanged(it, node)
                },
            )
        }

        Button(
            onClick = {
                eventConsumer.accept(LoginScreenEvent.OnLoginSubmit)
            },
        ) {
            Text(text = "Submit")
        }
    }
}
