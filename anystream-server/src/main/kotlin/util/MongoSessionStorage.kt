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
package anystream.util

import anystream.data.UserSession
import anystream.json
import com.mongodb.MongoQueryException
import com.mongodb.client.model.UpdateOptions
import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import org.slf4j.Logger
import org.slf4j.MarkerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.NoSuchElementException
import kotlin.text.Charsets.UTF_8

@Serializable
private class SessionData(
    val id: String,
    val data: ByteArray,
)

class MongoSessionStorage(
    mongodb: CoroutineDatabase,
    private val logger: Logger,
) : SimplifiedSessionStorage() {

    private val marker = MarkerFactory.getMarker(this::class.simpleName)

    private val sessions = ConcurrentHashMap<String, ByteArray>()
    private val sessionCollection = mongodb.getCollection<SessionData>()
    private val updateOptions = UpdateOptions().upsert(true)

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
            logger.trace(marker, "Deleting session $id")
            sessions.remove(id)
            sessionCollection.deleteOne(SessionData::id eq id)
        } else {
            logger.debug(marker, "Writing session $id, ${data.toString(UTF_8)}")
            sessions[id] = data
            try {
                sessionCollection.updateOne(
                    SessionData::id eq id,
                    SessionData(id, data),
                    updateOptions
                )
            } catch (e: MongoQueryException) {
                logger.trace(marker, "Failed to write session data", e)
            }
        }
    }

    override suspend fun read(id: String): ByteArray? {
        logger.debug(marker, "Looking for session $id")
        return (sessions[id] ?: sessionCollection.findOne(SessionData::id eq id)?.data).also {
            logger.debug(marker, "Found session $id")
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
            provider(reader(autoFlush = true) {
                write(id, channel.readRemaining().readBytes())
            }.channel)
        }
    }
}
