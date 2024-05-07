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
package anystream.client

import anystream.models.Permission
import anystream.models.User
import anystream.models.UserPublic
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val STORAGE_KEY = "SESSION_TOKEN"
private const val PERMISSIONS_KEY = "PERMISSIONS_KEY"
private const val USER_KEY = "USER_KEY"
private const val SERVER_URL_KEY = "SERVER_URL_KEY"

private val json = Json {
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    useAlternativeNames = false
}

interface SessionDataStore {
    fun write(key: String, value: String)
    fun read(key: String): String?
    fun remove(key: String)

    companion object : SessionDataStore {
        private val data = mutableMapOf<String, String>()

        override fun read(key: String): String? = data[key]

        override fun write(key: String, value: String) {
            data[key] = value
        }

        override fun remove(key: String) {
            data.remove(key)
        }
    }
}

class SessionManager(private val dataStore: SessionDataStore) {
    private val user = MutableSharedFlow<UserPublic?>(1, 0, DROP_OLDEST)
    private val token = MutableSharedFlow<String?>(1, 0, DROP_OLDEST)
    private val permissions = MutableSharedFlow<Set<Permission>?>(1, 0, DROP_OLDEST)

    val userFlow: Flow<UserPublic?> = user
    val tokenFlow: Flow<String?> = token
    val permissionsFlow: Flow<Set<Permission>?> = permissions

    init {
        fetchUser()
        fetchToken()
        fetchPermissions()
    }

    fun writeServerUrl(serverUrl: String) {
        dataStore.write(SERVER_URL_KEY, serverUrl)
    }

    fun writeUser(user: UserPublic) {
        dataStore.write(USER_KEY, json.encodeToString(user))
        this.user.tryEmit(user)
    }

    fun writeToken(token: String) {
        dataStore.write(STORAGE_KEY, token)
        this.token.tryEmit(token)
    }

    fun writePermissions(permissions: Set<Permission>) {
        dataStore.write(PERMISSIONS_KEY, json.encodeToString(permissions))
        this.permissions.tryEmit(permissions)
    }

    fun fetchServerUrl(): String? {
        return dataStore.read(SERVER_URL_KEY)
    }

    fun fetchUser(): UserPublic? {
        return user.replayCache.singleOrNull()
            ?: dataStore.read(USER_KEY)?.let { data ->
                json.decodeFromString<UserPublic>(data)
                    .also(user::tryEmit)
            }
    }

    fun fetchToken(): String? {
        return token.replayCache.singleOrNull()
            ?: dataStore.read(STORAGE_KEY).also(token::tryEmit)
    }

    fun fetchPermissions(): Set<Permission>? {
        return permissions.replayCache.singleOrNull()
            ?: dataStore.read(PERMISSIONS_KEY)
                ?.let { json.decodeFromString<List<Permission>>(it) }
                ?.toSet()
                ?.also(permissions::tryEmit)
    }

    fun clear() {
        dataStore.remove(USER_KEY)
        dataStore.remove(STORAGE_KEY)
        dataStore.remove(PERMISSIONS_KEY)
        user.tryEmit(null)
        token.tryEmit(null)
        permissions.tryEmit(null)
    }
}
