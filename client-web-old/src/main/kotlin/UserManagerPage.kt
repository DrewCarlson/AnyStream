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
import anystream.models.InviteCode
import anystream.models.Permissions
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.kvision.core.onEvent
import io.kvision.data.dataContainer
import io.kvision.form.select.Select
import io.kvision.form.text.textInput
import io.kvision.html.ButtonSize
import io.kvision.html.ButtonStyle
import io.kvision.html.button
import io.kvision.html.label
import io.kvision.panel.VPanel
import io.kvision.panel.hPanel
import io.kvision.state.observableListOf
import io.kvision.toast.Toast

class UserManagerPage(
    private val client: AnyStreamClient
) : VPanel(), CoroutineScope {

    override val coroutineContext = Default + SupervisorJob()

    private val inviteCodes = observableListOf<InviteCode>()

    init {
        launch {
            val codes = client.getInvites()
            inviteCodes.clear()
            inviteCodes.addAll(codes)
        }
        hPanel {
            val selectedPermissions = Select(
                options = Permissions.all.map { it to it },
                value = Permissions.VIEW_COLLECTION,
                multiple = true
            )
            button("", icon = "fas fa-plus") {
                size = ButtonSize.SMALL
                onClick {
                    launch {
                        val permissions = (selectedPermissions.value ?: "").split(",").toSet()
                        val inviteCode = client.createInvite(permissions)
                        inviteCodes.add(inviteCode)
                        Toast.success("Invite created")
                    }
                }
            }

            add(selectedPermissions)
        }
        dataContainer(inviteCodes, { inviteCode, _, _ ->
            hPanel {
                button("", icon = "fas fa-trash", style = ButtonStyle.DANGER) {
                    onClick {
                        launch {
                            if (client.deleteInvite(inviteCode.value)) {
                                Toast.success("Invite deleted")
                                inviteCodes.remove(inviteCode)
                            } else {
                                Toast.error("Failed to delete invite")
                            }
                        }
                    }
                }
                val inviteLink = textInput {
                    val baseUrl = window.location.run { "$protocol//$host" }
                    value = "$baseUrl/signup?inviteCode=${inviteCode.value}"
                    onEvent {
                        keypress = { it.preventDefault() }
                    }
                }

                button("", "fas fa-clipboard") {
                    onClick {
                        inviteLink.focus()
                        inviteLink.getElementJQuery()?.select()
                        document.execCommand("copy")
                    }
                }

                label("Permissions: ${inviteCode.permissions.joinToString()}")
            }
        })
    }
}
