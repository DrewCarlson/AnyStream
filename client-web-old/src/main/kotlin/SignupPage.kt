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
package anystream.frontend

import anystream.client.AnyStreamClient
import anystream.models.*
import anystream.models.api.CreateUserError.PasswordError
import anystream.models.api.CreateUserError.UsernameError
import io.ktor.client.features.*
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.w3c.dom.url.URLSearchParams
import io.kvision.core.AlignItems
import io.kvision.core.JustifyContent
import io.kvision.form.text.TextInputType
import io.kvision.form.text.textInput
import io.kvision.html.*
import io.kvision.panel.VPanel
import io.kvision.routing.routing
import io.kvision.toast.Toast

class SignupPage(
    private val client: AnyStreamClient
) : VPanel(
    justify = JustifyContent.CENTER,
    alignItems = AlignItems.CENTER,
), CoroutineScope {

    override val coroutineContext = Default + SupervisorJob()

    private val authMutex = Mutex()

    init {
        h3("Signup")
        val username = textInput {
            placeholder = "Username"
        }

        val password = textInput(type = TextInputType.PASSWORD) {
            placeholder = "Password"
        }

        val confirmPassword = textInput(type = TextInputType.PASSWORD) {
            placeholder = "Confirm Password"
        }

        val inviteCodeInput = textInput {
            placeholder = "Invite Code"
            value = URLSearchParams(window.location.search).get("inviteCode")
            disabled = value != null
        }

        val error = label()

        button("Confirm") {
            onClick {
                val user = username.value ?: ""
                val pass = password.value ?: ""
                val inviteCode = inviteCodeInput.value
                val passConf = confirmPassword.value ?: ""
                if (pass == passConf) {
                    error.content = null
                    attemptSignup(this,  error, user, pass, inviteCode)
                } else {
                    error.content = "Passwords do not match!"
                }
            }
        }

        link("Go to Login") {
            setStyle("cursor", "pointer")
            onClick {
                routing.navigate("/login")
            }
        }
    }

    private fun attemptSignup(
        button: Button,
        errorLabel: Label,
        username: String,
        password: String,
        inviteCode: String?
    ) {
        if (authMutex.isLocked) return
        button.disabled = true
        fun unlockWithError(error: String = "Signup failed!") {
            button.disabled = false
            Toast.error(error)
        }
        launch {
            authMutex.withLock {
                try {
                    val (success, error) = client.createUser(username, password, inviteCode)
                    when {
                        success != null -> routing.navigate("/")
                        error != null -> {
                            errorLabel.content = error.passwordError?.message
                                ?: error.usernameError?.message
                            unlockWithError()
                        }
                    }
                } catch (e: ClientRequestException) {
                    if (e.response.status == Forbidden) {
                        errorLabel.content = "Check your invite code"
                        unlockWithError("A valid invite code is required.")
                    } else {
                        e.printStackTrace()
                        unlockWithError(e.message ?: "")
                    }
                }
            }
        }
    }

    private val PasswordError?.message: String?
        get() = when (this) {
            PasswordError.BLANK -> "Password cannot be blank"
            PasswordError.TOO_SHORT -> "Password must be at least $PASSWORD_LENGTH_MIN characters."
            PasswordError.TOO_LONG -> "Password must be $PASSWORD_LENGTH_MAX or fewer characters."
            null -> null
        }

    private val UsernameError?.message: String?
        get() = when (this) {
            UsernameError.BLANK -> "Username cannot be blank"
            UsernameError.TOO_SHORT -> "Username must be at least $USERNAME_LENGTH_MIN characters."
            UsernameError.TOO_LONG -> "Username must be $USERNAME_LENGTH_MAX or fewer characters."
            UsernameError.ALREADY_EXISTS -> "Username already exists."
            null -> null
        }
}
