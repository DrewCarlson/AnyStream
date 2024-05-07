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
package anystream.util

import anystream.data.UserSession
import anystream.db.SessionsDao
import anystream.db.tables.references.SESSION
import anystream.json
import io.ktor.server.sessions.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.encodeToString
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.NoSuchElementException

class SqlSessionStorage(
    private val db: DSLContext,
    private val sessionsDao: SessionsDao
) : SessionStorage {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val sessionsCache = ConcurrentHashMap<String, String>()

    object Serializer : SessionSerializer<UserSession> {
        override fun serialize(session: UserSession): String {
            return json.encodeToString(session)
        }

        override fun deserialize(text: String): UserSession {
            return json.decodeFromString(text)
        }
    }

    override suspend fun write(id: String, value: String) {
        logger.trace("Writing session {}, {}", id, value)
        sessionsCache[id] = value
        try {
            sessionsDao.insertOrUpdate(id, Serializer.deserialize(value).userId, value)
        } catch (e: Throwable) {
            logger.trace("Failed to write session data", e)
        }
    }

    override suspend fun read(id: String): String {
        logger.trace("Looking for session {}", id)
        return (sessionsCache[id] ?: findSession(id)).also {
            logger.trace("Found session {}", id)
        } ?: throw NoSuchElementException()
    }

    override suspend fun invalidate(id: String) {
        logger.trace("Deleting session {}", id)
        sessionsCache.remove(id)
        try {
            db.deleteFrom(SESSION)
                .where(SESSION.ID.eq(id))
                .executeAsync()
                .await()
        } catch (e: Throwable) {
            logger.error("Failed to delete session '$id' from database", e)
        }
    }

    private fun findSession(id: String): String? {
        return try {
            db.selectFrom(SESSION)
                .where(SESSION.ID.eq(id))
                .fetchOne(SESSION.DATA)
        } catch (e: Throwable) {
            logger.trace("Failed to find session data", e)
            null
        }
    }

    suspend fun readSession(id: String): UserSession {
        return read(id).run(Serializer::deserialize)
    }
}
