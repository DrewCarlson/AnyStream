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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.client.AnyStreamClient
import anystream.router.SharedRouter
import anystream.ui.components.AutofillInput
import anystream.ui.components.PrimaryButton
import anystream.ui.components.onFocusStateChanged
import anystream.ui.generated.resources.*
import anystream.ui.generated.resources.Res
import anystream.ui.generated.resources.ic_discovery
import anystream.ui.generated.resources.ic_message
import anystream.ui.generated.resources.logo_login
import kt.mobius.SimpleLogger
import kt.mobius.compose.rememberMobiusLoop
import kt.mobius.flow.FlowMobius
import kt.mobius.functions.Consumer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun LoginScreen(
    client: AnyStreamClient,
    router: SharedRouter,
    modifier: Modifier = Modifier,
) {
    val (modelState, eventConsumer) = rememberMobiusLoop(
        LoginScreenModel.create(client.serverUrl, supportsPairing = false),
        LoginScreenInit
    ) {
        FlowMobius.loop(
            LoginScreenUpdate,
            LoginScreenHandler(client, router),
        ).logger(SimpleLogger("Login"))
    }

    /*Scaffold(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { router.popCurrentRoute() },
                        content = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        },
                    )
                }
            )
        },
    ) { padding ->
    }*/
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus(force = true)
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(Res.drawable.logo_login),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            text = "Login to Your Account",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
        )
        FormBody(modelState.value, eventConsumer)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun FormBody(
    model: LoginScreenModel,
    eventConsumer: Consumer<LoginScreenEvent>,
) {
    var serverUrlValue by remember { mutableStateOf(TextFieldValue(model.serverUrl)) }
    var usernameValue by remember { mutableStateOf(TextFieldValue(model.username)) }
    var passwordValue by remember { mutableStateOf(TextFieldValue(model.password)) }
    val focusManager = LocalFocusManager.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        OutlineTextField(
            textFieldValue = serverUrlValue,
            onValueChange = {
                serverUrlValue = it
                eventConsumer.accept(LoginScreenEvent.OnServerUrlChanged(it.text))
            },
            leadingIcon = Res.drawable.ic_discovery,
            placeHolder = "Server URL",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            isError = model.serverValidation == LoginScreenModel.ServerValidation.INVALID,
            readOnly = model.isInputLocked(),
        )

        Spacer(Modifier.height(24.dp))
        AutofillInput(
            autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
            onFill = {
                focusManager.clearFocus(force = true)
                usernameValue = TextFieldValue(it)
                eventConsumer.accept(LoginScreenEvent.OnUsernameChanged(it))
            },
        ) { node ->
            val autofill = LocalAutofill.current
            OutlineTextField(
                textFieldValue = usernameValue,
                onValueChange = {
                    usernameValue = it
                    eventConsumer.accept(LoginScreenEvent.OnUsernameChanged(it.text))
                },
                leadingIcon = Res.drawable.ic_message,
                placeHolder = "Username",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                isError = model.loginError?.usernameError != null,
                readOnly = model.isInputLocked(),
                modifier = Modifier.onGloballyPositioned {
                    node.boundingBox = it.boundsInWindow()
                }.onFocusChanged {
                    autofill?.onFocusStateChanged(it, node)
                },
            )
        }

        ErrorText(
            isError = model.loginError?.usernameError != null,
            errorText = model.loginError?.usernameError?.name.orEmpty(),
            label = "Username is",
        )
        Spacer(Modifier.height(24.dp))

        AutofillInput(
            autofillTypes = listOf(AutofillType.Password),
            onFill = {
                focusManager.clearFocus(force = true)
                passwordValue = TextFieldValue(it)
                eventConsumer.accept(LoginScreenEvent.OnPasswordChanged(it))
            },
        ) { node ->
            val autofill = LocalAutofill.current
            OutlineTextField(
                textFieldValue = passwordValue,
                onValueChange = {
                    passwordValue = it
                    eventConsumer.accept(LoginScreenEvent.OnPasswordChanged(it.text))
                },
                leadingIcon = Res.drawable.ic_lock,
                placeHolder = "Password",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus(force = true)
                        eventConsumer.accept(LoginScreenEvent.OnLoginSubmit)
                    }
                ),
                visualTransformation = PasswordVisualTransformation(),
                isError = model.loginError?.passwordError != null,
                readOnly = model.isInputLocked(),
                modifier = Modifier.onGloballyPositioned {
                    node.boundingBox = it.boundsInWindow()
                }.onFocusChanged {
                    autofill?.onFocusStateChanged(it, node)
                },
            )
        }
        ErrorText(
            isError = model.loginError?.passwordError != null,
            errorText = model.loginError?.passwordError?.name.orEmpty(),
            label = "Password is",
        )

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Sign In",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            isLoading = model.state.isAuthenticating,
            onClick = { eventConsumer.accept(LoginScreenEvent.OnLoginSubmit) },
        )
    }
}

@Composable
private fun ColumnScope.ErrorText(
    isError: Boolean,
    errorText: String,
    label: String,
) {
    AnimatedVisibility(
        visible = isError,
        modifier = Modifier.align(Alignment.Start),
    ) {
        Text(
            text = "$label ${errorText.lowercase()}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun OutlineTextField(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    leadingIcon: DrawableResource?,
    placeHolder: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = textFieldValue,
        placeholder = {
            Text(
                text = placeHolder,
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 0.2.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color(0xFF9E9E9E),
            )
        },
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            letterSpacing = 0.2.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        leadingIcon = {
            leadingIcon?.let {
                Icon(painterResource(it), contentDescription = null)
            }
        },
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth().then(modifier),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
            unfocusedBorderColor = Color.Transparent,
            errorContainerColor = Color(0x14E21221),
            unfocusedContainerColor = Color(0xFF1F222A),
            focusedLabelColor = Color(0xFF1F222A),
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            errorLeadingIconColor = MaterialTheme.colorScheme.error,
        ),
    )
}
