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
package anystream.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import anystream.android.AppTopBar
import anystream.android.AppTypography
import anystream.client.AnyStreamClient
import anystream.client.SessionManager
import anystream.models.api.PairingMessage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.ktor.client.features.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress


@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoginCompleted: (client: AnyStreamClient, serverUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { AppTopBar(client = null, backStack = null) },
        modifier = modifier
    ) { padding ->
        FormBody(sessionManager, onLoginCompleted, padding)
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FormBody(
    sessionManager: SessionManager,
    onLoginCompleted: (client: AnyStreamClient, serverUrl: String) -> Unit,
    paddingValues: PaddingValues
) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(TextFieldValue("")) }
    var username by remember { mutableStateOf(TextFieldValue()) }
    var password by remember { mutableStateOf(TextFieldValue()) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val showStacked = LocalConfiguration.current.screenWidthDp < 800
    val isServerUrlValid by produceState(false, serverUrl.text) {
        delay(400)
        value = if (serverUrl.text.run { isNotBlank() && length > 5 }) {
            withContext(Dispatchers.IO) {
                try {
                    // TODO: Check if server is AnyStream instance
                    InetAddress.getByName(serverUrl.text.substringAfter("://")).isReachable(10)
                } catch (e: Throwable) {
                    false
                }
            }
        } else false
    }
    StackedOrSideBySide(stacked = showStacked) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = serverUrl,
                placeholder = { Text(text = "Server Url") },
                onValueChange = { serverUrl = it },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
            )
            Autofill(
                autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
                onFill = { username = TextFieldValue(it) }
            ) { node ->
                val autofill = LocalAutofill.current
                OutlinedTextField(
                    value = username,
                    placeholder = { Text(text = "Username") },
                    onValueChange = { username = it },
                    singleLine = true,
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
            Autofill(
                autofillTypes = listOf(AutofillType.Password),
                onFill = { password = TextFieldValue(it) }
            ) { node ->
                val autofill = LocalAutofill.current
                OutlinedTextField(
                    value = password,
                    placeholder = { Text(text = "Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    onValueChange = { password = it },
                    singleLine = true,
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

            errorMessage?.let { error ->
                Text(text = error)
            }

            Button(onClick = {
                scope.launch {
                    try {
                        val client = AnyStreamClient(
                            serverUrl = serverUrl.text,
                            sessionManager = sessionManager
                        )
                        client.login(username.text, password.text)
                        onLoginCompleted(client, serverUrl.text)
                    } catch (e: ClientRequestException) {
                        e.printStackTrace()
                        errorMessage = "${e.response.status}"
                    }
                }
            }) {
                Text(text = "Submit")
            }
        }

        if (isServerUrlValid) {
            DisplayPairingCode(serverUrl.text, sessionManager, onLoginCompleted)
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
    serverUrl: String,
    sessionManager: SessionManager,
    onLoginCompleted: (client: AnyStreamClient, serverUrl: String) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val client = AnyStreamClient(
            serverUrl = serverUrl,
            sessionManager = sessionManager
        )
        val pairingCode by produceState<String?>(null) {
            while (value == null) {
                client.createPairingSession().collect { message ->
                    when (message) {
                        is PairingMessage.Started -> {
                            value = message.pairingCode
                        }
                        is PairingMessage.Authorized -> {
                            try {
                                client.createPairedSession(value!!, message.secret)
                                onLoginCompleted(
                                    AnyStreamClient(
                                        serverUrl,
                                        sessionManager = sessionManager
                                    ),
                                    serverUrl
                                )
                            } catch (e: ClientRequestException) {
                                e.printStackTrace()
                            }
                        }
                        is PairingMessage.Failed -> {
                            value = null
                        }
                        is PairingMessage.Idle -> Unit
                    }
                }
            }
        }

        if (pairingCode != null) {
            Text(
                text = "Pairing Code",
                style = AppTypography.subtitle1
            )
            Text(
                text = "Scan with another device to login.",
                style = AppTypography.subtitle2
            )
            QrImage(content = pairingCode!!)
        }
    }
}

@Composable
fun QrImage(
    content: String
) {
    val bitmap by produceState<Bitmap?>(null) {
        val size = 500
        val bitMatrix = QRCodeWriter()
            .encode(content, BarcodeFormat.QR_CODE, size, size)
        val w: Int = bitMatrix.width
        val h: Int = bitMatrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) {
                    0xFF000000
                } else {
                    0xFFFFFFFF
                }.toInt()
            }
        }
        value = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, w, h)
        }
    }

    bitmap?.run {
        Image(
            bitmap = asImageBitmap(),
            modifier = Modifier.size(250.dp),
            contentDescription = null
        )
    }
}


@ExperimentalComposeUiApi
@Composable
private fun Autofill(
    autofillTypes: List<AutofillType>,
    onFill: ((String) -> Unit),
    content: @Composable (AutofillNode) -> Unit
) {
    val autofillNode = AutofillNode(onFill = onFill, autofillTypes = autofillTypes)

    val autofillTree = LocalAutofillTree.current
    autofillTree += autofillNode

    Box(
        Modifier.onGloballyPositioned {
            autofillNode.boundingBox = it.boundsInWindow()
        }
    ) {
        content(autofillNode)
    }
}

@ExperimentalComposeUiApi
private fun Autofill.onFocusStateChanged(focusState: FocusState, node: AutofillNode) {
    if (focusState.isFocused) requestAutofillForNode(node) else cancelAutofillForNode(node)
}
