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
import anystream.json
import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.jdbi.v3.core.JdbiException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.NoSuchElementException
import kotlin.text.Charsets.UTF_8

class SqlSessionStorage(
    private val sessionsDao: SessionsDao,
) : SimplifiedSessionStorage() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val sessionsCache = ConcurrentHashMap<String, ByteArray>()

    object Serializer : SessionSerializer<UserSession> {
        override fun serialize(session: UserSession): String {
            return json.encodeToString(session)
        }

        override fun deserialize(text: String): UserSession {
            return json.decodeFromString(text)
        }
    }

    override suspend fun write(id: String, data: ByteArray?) {
        if (data == null) {
            logger.trace("Deleting session '$id'")
            sessionsCache.remove(id)
            try {
                sessionsDao.delete(id)
            } catch (e: JdbiException) {
                logger.error("Failed to delete session '$id' from database", e)
            }
        } else {
            logger.debug("Writing session '$id', ${data.toString(UTF_8)}")
            sessionsCache[id] = data
            try {
                sessionsDao.insertOrUpdate(id, data.toString(UTF_8))
            } catch (e: JdbiException) {
                logger.trace("Failed to write session data", e)
            }
        }
    }

    override suspend fun read(id: String): ByteArray? {
        logger.debug("Looking for session '$id'")
        return (sessionsCache[id] ?: findSession(id)).also {
            logger.debug("Found session $id")
        }
    }

    private fun findSession(id: String): ByteArray? {
        return try {
            sessionsDao.find(id)?.toByteArray()
        } catch (e: JdbiException) {
            logger.trace("Failed to find session data", e)
            null
        }
    }

    suspend fun readSession(id: String): UserSession? {
        return read(id)?.decodeToString()?.run(Serializer::deserialize)
    }
}

abstract class SimplifiedSessionStorage : SessionStorage {
    abstract suspend fun read(id: String): ByteArray?
    abstract suspend fun write(id: String, data: ByteArray?)

    override suspend fun invalidate(id: String) {
        write(id, null)
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        val data = read(id) ?: throw NoSuchElementException("Session $id not found")
        return consumer(ByteReadChannel(data))
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        return coroutineScope {
            provider(
                reader(autoFlush = true) {
                    write(id, channel.readRemaining().readBytes())
                }.channel
            )
        }
    }
}
