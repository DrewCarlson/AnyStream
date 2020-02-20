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
import anystream.models.api.CreateSessionError.*
import io.ktor.client.features.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.kvision.core.AlignItems
import io.kvision.core.JustifyContent
import io.kvision.form.text.TextInputType
import io.kvision.form.text.textInput
import io.kvision.html.*
import io.kvision.panel.VPanel
import io.kvision.routing.routing
import io.kvision.toast.Toast

class LoginPage(
    private val client: AnyStreamClient
) : VPanel(
    justify = JustifyContent.CENTER,
    alignItems = AlignItems.CENTER,
), CoroutineScope {

    override val coroutineContext = Default + SupervisorJob()

    private val authMutex = Mutex()

    init {
        h3("Login")

        val username = textInput {
            placeholder = "Username"
        }

        val password = textInput(type = TextInputType.PASSWORD) {
            placeholder = "Password"
        }

        val error = label()

        button("Confirm") {
            onClick {
                val user = username.value ?: ""
                val pass = password.value ?: ""
                attemptLogin(this, error, user, pass)
            }
        }

        link("Go to Signup") {
            setStyle("cursor", "pointer")
            onClick {
                routing.navigate("/signup")
            }
        }
    }

    private fun attemptLogin(button: Button, errorLabel: Label, username: String, password: String) {
        if (authMutex.isLocked) return
        button.disabled = true
        fun unlockWithError(error: String = "Login failed!") {
            Toast.error(error)
            button.disabled = false
        }
        launch {
            authMutex.withLock {
                try {
                    val (success, error) = client.login(username, password)
                    when {
                        success != null -> routing.navigate("/")
                        error != null -> {
                            errorLabel.content = when (error) {
                                USERNAME_NOT_FOUND -> "Unknown username"
                                USERNAME_INVALID -> "Not a valid username"
                                PASSWORD_INCORRECT -> "Incorrect password"
                                PASSWORD_INVALID -> "Not a valid password"
                            }
                            unlockWithError()
                        }
                    }
                } catch (e: ClientRequestException) {
                    e.printStackTrace()
                    unlockWithError(e.message ?: "")
                }
            }
        }
    }
}
