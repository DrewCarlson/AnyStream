/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.client.api

import anystream.client.AnyStreamClientException
import anystream.models.InviteCode
import anystream.models.Permission
import anystream.models.UpdateUserBody
import anystream.models.UserPublic
import anystream.models.api.CreateSessionBody
import anystream.models.api.CreateSessionResponse
import anystream.models.api.CreateUserBody
import anystream.models.api.CreateUserResponse
import anystream.models.api.PairingMessage
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import io.ktor.serialization.WebsocketDeserializeException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class UserApiClient(
    private val core: AnyStreamApiCore,
) {

    val authenticated: Flow<Boolean> = core.sessionManager.tokenFlow.map { it != null }
    val permissions: Flow<Set<Permission>?> = core.sessionManager.permissionsFlow
    val user: Flow<UserPublic?> = core.sessionManager.userFlow
    val token: String?
        get() = core.sessionManager.fetchToken()


    fun isAuthenticated(): Boolean {
        return core.sessionManager.fetchToken() != null
    }

    fun userPermissions(): Set<Permission> {
        return core.sessionManager.fetchPermissions() ?: emptySet()
    }

    fun hasPermission(permission: Permission): Boolean {
        return userPermissions().run { contains(Permission.Global) || contains(permission) }
    }

    fun authedUser(): UserPublic? {
        return core.sessionManager.fetchUser()
    }

    suspend fun createUser(
        username: String,
        password: String,
        inviteCode: String?,
        rememberUser: Boolean = true,
    ): CreateUserResponse = core.http.post("/api/users") {
        contentType(ContentType.Application.Json)
        parameter("createSession", rememberUser)
        setBody(CreateUserBody(username, password, inviteCode))
    }.bodyOrThrow<CreateUserResponse>().also { response ->
        if (rememberUser && response is CreateUserResponse.Success) {
            core.sessionManager.writeUser(response.user)
            core.sessionManager.writePermissions(response.permissions)
        }
    }

    suspend fun getUser(id: String): UserPublic {
        return core.http.get("/api/users/$id").bodyOrThrow()
    }

    suspend fun getUsers(): List<UserPublic> {
        return core.http.get("/api/users").bodyOrThrow()
    }

    suspend fun updateUser(
        userId: Int,
        displayName: String,
        password: String?,
        currentPassword: String?,
    ) {
        core.http.put("/api/users/$userId") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateUserBody(
                    displayName = displayName,
                    password = password,
                    currentPassword = currentPassword,
                ),
            )
        }.orThrow()
    }

    suspend fun deleteUser(id: String) {
        core.http.delete("/api/users/$id").orThrow()
    }

    suspend fun login(
        username: String,
        password: String,
        pairing: Boolean = false,
    ): CreateSessionResponse {
        return core.http.post("/api/users/session") {
            contentType(ContentType.Application.Json)
            setBody(CreateSessionBody(username, password))
        }.bodyOrThrow<CreateSessionResponse>().also { response ->
            if (!pairing && response is CreateSessionResponse.Success) {
                core.sessionManager.writeUser(response.user)
                core.sessionManager.writePermissions(response.permissions)
            }
        }
    }

    suspend fun completeOauth(token: String) {
        val response = core.http.get("/api/users/session") {
            header(AnyStreamApiCore.SESSION_KEY, token)
        }
        if (response.status == OK) {
            val body = response.body<CreateSessionResponse.Success>()
            core.sessionManager.writeToken(token)
            core.sessionManager.writeUser(body.user)
            core.sessionManager.writePermissions(body.permissions)
        }
    }

    suspend fun logout() {
        val token = core.sessionManager.fetchToken() ?: return
        core.sessionManager.clear()
        core.http.delete("/api/users/session") {
            header(AnyStreamApiCore.SESSION_KEY, token)
        }.orThrow()
    }

    suspend fun createPairedSession(pairingCode: String, secret: String): CreateSessionResponse {
        val response = core.http.post("/api/users/session/paired") {
            parameter("pairingCode", pairingCode)
            parameter("secret", secret)
        }.bodyOrThrow<CreateSessionResponse>()

        (response as? CreateSessionResponse.Success)?.run {
            core.sessionManager.writeUser(user)
            core.sessionManager.writePermissions(permissions)
        }

        return response
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun createPairingSession(): Flow<PairingMessage> = flow {
        core.http.wss(path = "/api/ws/users/pair") {
            while (!incoming.isClosedForReceive) {
                try {
                    emit(receiveDeserialized())
                } catch (e: WebsocketDeserializeException) {
                    // ignored
                } catch (e: ClosedReceiveChannelException) {
                    // ignored
                }
            }
        }
    }

    suspend fun fetchAuthTypes(): List<String> {
        return core.http.get("/api/users/auth-types").bodyOrThrow()
    }


    suspend fun getInvites(): List<InviteCode> {
        return core.http.get("/api/users/invite").bodyOrThrow()
    }

    suspend fun createInvite(permissions: Set<Permission>): InviteCode {
        return core.http.post("/api/users/invite") {
            contentType(ContentType.Application.Json)
            setBody(permissions)
        }.bodyOrThrow()
    }

    suspend fun deleteInvite(id: String): Boolean {
        return try {
            core.http.delete("/api/users/invite/$id").orThrow()
            true
        } catch (e: AnyStreamClientException) {
            if (e.response?.status == NotFound) false else throw e
        }
    }
}
