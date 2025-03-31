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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalDensity
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
internal fun LoginScreen(client: AnyStreamClient, router: SharedRouter) {
    val (modelState, eventConsumer) = rememberMobiusLoop(
        LoginScreenModel.create(client.serverUrl, supportsPairing = false),
        LoginScreenInit
    ) {
        FlowMobius.loop(
            LoginScreenUpdate,
            LoginScreenHandler.create(client, router),
        ).logger(SimpleLogger("Login"))
    }

    Scaffold(
        topBar = {
            TopAppBar(backgroundColor = Color.Transparent, elevation = 0.dp) {
                IconButton(
                    onClick = { router.popCurrentRoute() },
                    content = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colors.onBackground,
                        )
                    },
                )
            }
        },
    ) { padding ->
        val imePadding = with(LocalDensity.current) {
            WindowInsets.ime.getBottom(this).div(2).toDp()
        }
        val animatedImePadding by animateDpAsState(imePadding)
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 24.dp)
                .padding(bottom = animatedImePadding)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus(force = true)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(Res.drawable.logo_login),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Text(
                text = "Login to Your Account",
                style = MaterialTheme.typography.h3.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(vertical = 16.dp),
            )
            FormBody(modelState.value, eventConsumer, padding)
        }
    }
}

@Composable
internal fun FormBody(
    model: LoginScreenModel,
    eventConsumer: Consumer<LoginScreenEvent>,
    paddingValues: PaddingValues,
) {
    var serverUrlValue by remember { mutableStateOf(TextFieldValue(model.serverUrl)) }
    var usernameValue by remember { mutableStateOf(TextFieldValue(model.username)) }
    var passwordValue by remember { mutableStateOf(TextFieldValue(model.password)) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
            .padding(paddingValues),
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
        ErrorText(
            isError = model.serverValidation == LoginScreenModel.ServerValidation.INVALID,
            errorText = model.loginError?.usernameError?.name.orEmpty(),
            label = "Server URL is",
        )

        Spacer(Modifier.height(24.dp))
        AutofillInput(
            autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
            onFill = { eventConsumer.accept(LoginScreenEvent.OnUsernameChanged(it)) },
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
            label = "Username is ",
        )
        Spacer(Modifier.height(24.dp))

        AutofillInput(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { eventConsumer.accept(LoginScreenEvent.OnPasswordChanged(it)) },
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
                    imeAction = ImeAction.Go,
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
            style = MaterialTheme.typography.subtitle2,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colors.error,
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
                style = MaterialTheme.typography.body2.copy(
                    letterSpacing = 0.2.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color(0xFF9E9E9E),
            )
        },
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.body2.copy(
            letterSpacing = 0.2.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
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
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = MaterialTheme.colors.onBackground,
            unfocusedBorderColor = Color.Transparent,
            backgroundColor = if (isError) Color(0x14E21221) else Color(0xFF1F222A),
            textColor = MaterialTheme.colors.onBackground,
            errorLeadingIconColor = MaterialTheme.colors.error,
        ),
    )
}
