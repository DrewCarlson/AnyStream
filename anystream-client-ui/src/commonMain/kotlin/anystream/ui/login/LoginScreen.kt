package anystream.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import anystream.SharedRes
import anystream.client.AnyStreamClient
import anystream.router.BackStack
import anystream.router.SharedRouter
import anystream.routing.Routes
import anystream.ui.components.AutofillInput
import anystream.ui.components.onFocusStateChanged
import anystream.util.createLoopController
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.launch
import kt.mobius.Mobius
import kt.mobius.SimpleLogger
import kt.mobius.flow.FlowMobius
import kt.mobius.functions.Consumer

@Composable
internal fun LoginScreen() {
    val client = AnyStreamClient(null)
    val (modelState, eventConsumerState) = createLoopController {
        val factory = FlowMobius.loop(
            LoginScreenUpdate,
            LoginScreenHandler.create(
                client,
                SharedRouter(),
            ),
        ).logger(SimpleLogger("Login"))
        val startModel = LoginScreenModel.create(client.serverUrl, supportsPairing = true)
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
private fun FormBody(
    model: LoginScreenModel,
    eventConsumer: Consumer<LoginScreenEvent>,
    paddingValues: PaddingValues,
) {
    var serverUrlValue by remember { mutableStateOf(TextFieldValue(model.serverUrl)) }
    var usernameValue by remember { mutableStateOf(TextFieldValue(model.username)) }
    var passwordValue by remember { mutableStateOf(TextFieldValue(model.password)) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
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

@Composable
fun AppTopBar(client: AnyStreamClient?, backStack: BackStack<Routes>? = null) {
    TopAppBar {
        val scope = rememberCoroutineScope()
        Image(
            painter = painterResource(SharedRes.images.as_logo),
            modifier = Modifier
                .padding(all = 8.dp)
                .size(width = 150.dp, height = 50.dp),
            contentDescription = null,
        )

        if (client != null) {
            val authed by client.authenticated.collectAsState(initial = client.isAuthenticated())
            if (authed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxSize(),
                ) {
//                    val packageManager = LocalContext.current.packageManager
//                    val hasCamera = remember {
//                        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
//                    }
                    // TODO Re-enable camera detection
                    if (false) {
                        IconButton(
                            onClick = { backStack?.push(Routes.PairingScanner) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QrCodeScanner,
                                contentDescription = "Pair a device.",
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                client.logout()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Sign out",
                        )
                    }
                }
            }
        }
    }
}