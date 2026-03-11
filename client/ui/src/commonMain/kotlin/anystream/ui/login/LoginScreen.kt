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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDataType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.onFillData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.presentation.login.LoginScreenModel
import anystream.ui.components.PrimaryButton
import anystream.ui.components.QrCodeImage
import anystream.ui.generated.resources.*
import anystream.ui.generated.resources.Res
import anystream.ui.generated.resources.ic_discovery
import anystream.ui.generated.resources.ic_message
import anystream.ui.generated.resources.logo_login
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun LoginScreen(
    model: LoginScreenModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLargeScreen = remember(constraints) {
            maxWidth >= 840.dp && maxHeight >= 600.dp
        }

        val focusManager = LocalFocusManager.current
        if (isLargeScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusManager.clearFocus(force = true)
                        }
                    }
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: QR code pairing panel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    QrCodePairingPanel(pairingCode = model.pairingCode)
                }
                // Right: Login form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
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
                    FormBody(model)
                }
            }
        } else {
            Column(
                modifier = Modifier
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
                FormBody(model)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QrCodePairingPanel(pairingCode: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (pairingCode == null) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            QrCodeImage(
                content = pairingCode,
                modifier = Modifier
                    .size(300.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Scan to sign in",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
internal fun FormBody(
    model: LoginScreenModel,
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
                model.onServerUrlChanged(it.text)
            },
            leadingIcon = Res.drawable.ic_discovery,
            placeHolder = "Server URL",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            showSuccess = model.isServerUrlValid,
            isError = !model.isServerUrlValid,
            readOnly = model.isInputLocked,
        )

        Spacer(Modifier.height(24.dp))
        OutlineTextField(
            textFieldValue = usernameValue,
            onValueChange = {
                usernameValue = it
                model.onUsernameChanged(it.text)
            },
            leadingIcon = Res.drawable.ic_message,
            placeHolder = "Username",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            isError = model.loginError?.usernameError != null,
            readOnly = model.isInputLocked,
            modifier = Modifier
                .semantics {
                    contentType = ContentType.Username
                    contentDataType = ContentDataType.Text
                    onFillData {
                        val value = it.textValue?.toString().orEmpty()
                        focusManager.clearFocus(force = true)
                        usernameValue = TextFieldValue(value)
                        model.onUsernameChanged(value)
                        true
                    }
                },
        )

        ErrorText(
            isError = model.loginError?.usernameError != null,
            errorText = model.loginError?.usernameError?.name.orEmpty(),
            label = "Username is",
        )
        Spacer(Modifier.height(24.dp))

        OutlineTextField(
            textFieldValue = passwordValue,
            onValueChange = {
                passwordValue = it
                model.onPasswordChanged(it.text)
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
                    model.onSubmitLogin()
                }
            ),
            visualTransformation = PasswordVisualTransformation(),
            isError = model.loginError?.passwordError != null,
            readOnly = model.isInputLocked,
            modifier = Modifier
                .semantics {
                    contentType = ContentType.Password
                    contentDataType = ContentDataType.Text
                    onFillData {
                        val value = it.textValue?.toString().orEmpty()
                        focusManager.clearFocus(force = true)
                        passwordValue = TextFieldValue(value)
                        model.onPasswordChanged(value)
                        true
                    }
                },
        )
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
            onClick = model.onSubmitLogin,
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
    showSuccess: Boolean = false,
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
            focusedBorderColor = if (showSuccess) {
                Color.Green
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            unfocusedBorderColor = if (showSuccess) {
                Color.Green
            } else {
                Color.Transparent
            },
            errorContainerColor = Color(0x14E21221),
            unfocusedContainerColor = Color(0xFF1F222A),
            focusedLabelColor = Color(0xFF1F222A),
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            errorLeadingIconColor = MaterialTheme.colorScheme.error,
        ),
    )
}
