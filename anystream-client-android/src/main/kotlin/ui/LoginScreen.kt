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
package anystream.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import anystream.android.AppTopBar
import anystream.android.AppTypography
import anystream.android.router.AndroidRouter
import anystream.android.ui.components.AutofillInput
import anystream.android.ui.components.QrImage
import anystream.android.ui.components.onFocusStateChanged
import anystream.android.util.createLoopController
import anystream.client.AnyStreamClient
import anystream.ui.login.*
import kt.mobius.android.AndroidLogger
import kt.mobius.flow.FlowMobius
import kt.mobius.functions.Consumer

@Composable
fun LoginScreen(
    client: AnyStreamClient,
    router: AndroidRouter,
    modifier: Modifier = Modifier,
) {
    val (modelState, eventConsumer) = createLoopController(
        LoginScreenModel.create(),
        LoginScreenInit
    ) {
        FlowMobius.loop(
            LoginScreenUpdate,
            LoginScreenHandler.create(client, router)
        ).logger(AndroidLogger.tag("Login"))
    }
    Scaffold(
        topBar = { AppTopBar(client = null, backStack = null) },
        modifier = modifier
    ) { padding ->
        FormBody(modelState, eventConsumer, padding)
    }
}

@Composable
private fun FormBody(
    modelState: State<LoginScreenModel>,
    eventConsumer: Consumer<LoginScreenEvent>,
    paddingValues: PaddingValues
) {
    val showStacked = LocalConfiguration.current.screenWidthDp < 800
    val model by remember { modelState }

    var serverUrlValue by remember { mutableStateOf(TextFieldValue(model.serverUrl)) }
    var usernameValue by remember { mutableStateOf(TextFieldValue(model.username)) }
    var passwordValue by remember { mutableStateOf(TextFieldValue(model.password)) }

    StackedOrSideBySide(stacked = showStacked) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = serverUrlValue,
                placeholder = { Text(text = "Server Url") },
                onValueChange = {
                    serverUrlValue = it
                    eventConsumer.accept(LoginScreenEvent.OnServerUrlChanged(it.text))
                },
                readOnly = model.isInputLocked(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
            )
            AutofillInput(
                autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
                onFill = { eventConsumer.accept(LoginScreenEvent.OnUsernameChanged(it)) }
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
                        imeAction = ImeAction.Next
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
                onFill = { eventConsumer.accept(LoginScreenEvent.OnPasswordChanged(it)) }
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
                        imeAction = ImeAction.Go
                    ),
                    modifier = Modifier.onGloballyPositioned {
                        node.boundingBox = it.boundsInWindow()
                    }.onFocusChanged {
                        autofill?.onFocusStateChanged(it, node)
                    },
                )
            }

            model.loginError?.let { error ->
                Text(text = error.toString())
            }

            Button(onClick = {
                eventConsumer.accept(LoginScreenEvent.OnLoginSubmit)
            }) {
                Text(text = "Submit")
            }
        }

        model.pairingCode?.let { pairingCode ->
            DisplayPairingCode(pairingCode)
        }
    }
}

@Composable
private fun StackedOrSideBySide(
    stacked: Boolean,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit
) {
    if (stacked) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxSize()
        ) {
            item { body() }
        }
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxSize()
        ) {
            item { body() }
        }
    }
}

@Composable
private fun DisplayPairingCode(
    pairingCode: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Pairing Code",
            style = AppTypography.subtitle1
        )
        Text(
            text = "Scan with another device to login.",
            style = AppTypography.subtitle2
        )
        QrImage(content = pairingCode, Modifier.size(250.dp))
    }
}
