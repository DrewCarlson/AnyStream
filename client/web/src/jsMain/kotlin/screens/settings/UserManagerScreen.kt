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
package anystream.screens.settings

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.models.InviteCode
import anystream.models.Permission
import anystream.models.UserPublic
import anystream.util.koinGet
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.get

@Composable
fun UserManagerScreen() {
    val client = koinGet<AnyStreamClient>()
    val users by produceState(emptyList<UserPublic>()) {
        value = client.user.getUsers()
    }
    Div({ classes("d-flex", "flex-column", "pt-2", "ps-2") }) {
        Div { H3 { Text("Users") } }
        Div({
            classes("d-flex", "flex-row", "align-items-center", "pb-2")
            style {
                gap(12.px)
            }
        }) {
            Button({
                classes("btn", "btn-primary")
                attr("data-bs-toggle", "offcanvas")
                attr("data-bs-target", "#inviteCodeCanvas")
                attr("aria-controls", "inviteCodeCanvas")
            }) {
                Text("Manage Invites")
            }
        }
        Div({
            classes("d-flex", "flex-column", "py-1")
            style {
                gap(10.px)
            }
        }) {
            users.forEach { user ->
                UserRow(user)
            }
        }
    }
    InviteCodeDialog(users)
}

@Composable
private fun UserRow(user: UserPublic) {
    Div({
        classes("d-flex", "flex-column", "p-3", "rounded")
        style {
            backgroundColor(rgba(0, 0, 0, 0.2))
            width(300.px)
        }
    }) {
        Div { Text(user.displayName) }
    }
}

@Composable
private fun InviteCodeDialog(users: List<UserPublic>) {
    val client = koinGet<AnyStreamClient>()
    val scope = rememberCoroutineScope()
    var inviteCodesState by remember {
        mutableStateOf<List<InviteCode>>(emptyList())
    }
    scope.launch { inviteCodesState = client.user.getInvites() }
    val createInviteCode: (Set<Permission>) -> Unit = { permissions ->
        scope.launch {
            inviteCodesState = inviteCodesState + client.user.createInvite(permissions)
        }
    }
    val deleteInviteCode = { inviteCode: InviteCode ->
        scope.launch { client.user.deleteInvite(inviteCode.secret) }
        inviteCodesState = inviteCodesState - inviteCode
    }
    val inviteCodeMap by derivedStateOf {
        if (users.isEmpty()) {
            emptyMap()
        } else {
            inviteCodesState.groupBy { inviteCode ->
                users.firstOrNull { it.id == inviteCode.createdByUserId }
            }
        }
    }
    Div({
        id("inviteCodeCanvas")
        attr("aria-labelledby", "inviteCodeCanvasLabel")
        classes("offcanvas", "offcanvas-start", "bg-dark", "w-auto")
        tabIndex(-1)
    }) {
        Div({
            classes("offcanvas-header")
        }) {
            H4({
                id("inviteCodeCanvasLabel")
                classes("offcanvas-header")
            }) {
                Text("Invite Codes")
            }
            Button({
                classes("btn-close", "text-reset")
                attr("data-bs-dismiss", "offcanvas")
                attr("aria-label", "Close")
            })
        }
        Div({ classes("d-flex", "flex-column") }) {
            CreateInviteCodeGroup(createInviteCode)
            InviteCodeTable(inviteCodeMap, deleteInviteCode)
        }
    }
}

@Composable
private fun CreateInviteCodeGroup(createInviteCode: (Set<Permission>) -> Unit) {
    var selectedPermissions by remember {
        mutableStateOf(setOf<Permission>(Permission.ViewCollection))
    }
    Div({
        classes("input-group", "px-3")
    }) {
        Button({
            classes("btn", "btn-primary")
            onClick { createInviteCode(selectedPermissions) }
        }) {
            I({ classes("bi", "bi-plus") })
            Text("Create Invite")
        }
        Select({
            classes("form-select")
            multiple()
            attr("aria-label", "Invite code permissions")
            onChange {
                (it.nativeEvent.target as? HTMLSelectElement)?.run {
                    selectedPermissions = List(selectedOptions.length) { i ->
                        Json.decodeFromString<Permission>((selectedOptions[i] as HTMLOptionElement).value)
                    }.toSet()
                }
            }
        }) {
            Permission.all.forEach { permission ->
                Option(Json.encodeToString(permission), {
                    if (selectedPermissions.contains(permission)) {
                        selected()
                    }
                }) {
                    Text(permission.toString())
                }
            }
        }
    }
}

@Composable
private fun InviteCodeTable(
    inviteCodeMap: Map<UserPublic?, List<InviteCode>>,
    deleteInviteCode: (InviteCode) -> Unit,
) {
    Div({
        classes("offcanvas-body")
    }) {
        Div({
            classes("table-responsive")
        }) {
            Table({
                classes("table", "table-dark", "table-striped", "table-hover")
            }) {
                Thead { InviteCodeHeaderRow() }
                Tbody {
                    inviteCodeMap.forEach { (user, inviteCodes) ->
                        inviteCodes.forEach { inviteCode ->
                            InviteCodeRow(user, inviteCode, deleteInviteCode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteCodeHeaderRow() {
    Tr {
        Th({ scope(Scope.Col) }) { }
        Th({ scope(Scope.Col) }) { Text("Creator") }
        Th({ scope(Scope.Col) }) { Text("Permissions") }
        Th({ scope(Scope.Col) }) { Text("Link") }
    }
}

@Composable
private fun InviteCodeRow(
    user: UserPublic?,
    inviteCode: InviteCode,
    deleteInviteCode: (InviteCode) -> Unit,
) {
    var focusAndCopy: (() -> Unit)? = null
    Tr {
        Th({ scope(Scope.Row) }) {
            Button({
                classes("btn", "btn-danger", "btn-small")
                onClick { deleteInviteCode(inviteCode) }
            }) {
                I({ classes("bi", "bi-x-circle") })
            }
        }
        Td { Text(user?.displayName ?: "<deleted>") }
        Td {
            Select({
                disabled()
                multiple()
            }) {
                inviteCode.permissions.forEach { permission ->
                    Option(Json.encodeToString(permission)) {
                        Text(permission.toString())
                    }
                }
            }
        }
        Td {
            val base = "${window.location.protocol}//${window.location.host}"
            val url = "$base/signup?inviteCode=${inviteCode.secret}"
            Input(InputType.Text) {
                value(url)
                classes("visually-hidden")
                onInput { it.nativeEvent.preventDefault() }
                ref { el ->
                    focusAndCopy = {
                        el.focus()
                        el.select()
                        document.execCommand("copy")
                    }
                    onDispose {
                        focusAndCopy = null
                    }
                }
            }
            Button({
                classes("btn", "btn-info", "btn-small")
                onClick { focusAndCopy?.invoke() }
            }) {
                I({ classes("bi", "bi-clipboard") })
            }
        }
    }
}
