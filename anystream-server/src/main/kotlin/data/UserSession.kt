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
package anystream.data

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class UserSession(
    val userId: String,
    val permissions: Set<String>,
    val sessionStarted: Long = Instant.now().toEpochMilli()
) : Principal {
    companion object {
        const val KEY = "as_user_session"
    }
}
