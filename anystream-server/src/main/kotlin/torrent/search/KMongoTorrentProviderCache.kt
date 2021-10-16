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
package anystream.torrent.search

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.UpdateOptions
import drewcarlson.torrentsearch.Category
import drewcarlson.torrentsearch.TorrentDescription
import drewcarlson.torrentsearch.TorrentProvider
import drewcarlson.torrentsearch.TorrentProviderCache
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.apache.commons.codec.binary.Hex
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

@Serializable
private data class CacheDoc(
    val key: String,
    val results: List<TorrentDescription>,
    @Contextual
    val createdAt: Instant = Instant.now()
)

private const val TOKEN_COLLECTION = "torrent-token-cache"
private const val RESULT_COLLECTION = "torrent-result-cache"

@Serializable
data class Token(val value: String)

class KMongoTorrentProviderCache(
    mongo: CoroutineDatabase,
    expirationMinutes: Long = 15
) : TorrentProviderCache {

    private val hash = MessageDigest.getInstance("SHA-256")
    private val tokenCollection = mongo.getCollection<Token>(TOKEN_COLLECTION)
    private val torrentCollection = mongo.getCollection<CacheDoc>(RESULT_COLLECTION)

    init {
        runBlocking {
            torrentCollection.ensureIndex(
                "{'key':1}", // CacheDoc::key
                IndexOptions()
                    .expireAfter(expirationMinutes, TimeUnit.MINUTES)
            )
        }
    }

    override fun saveToken(provider: TorrentProvider, token: String) {
        runBlocking {
            tokenCollection.updateOneById(
                provider.name,
                Token(token),
                UpdateOptions().upsert(true)
            )
        }
    }

    override fun loadToken(provider: TorrentProvider): String? {
        return runBlocking {
            tokenCollection.findOneById(provider.name)?.value
        }
    }

    override fun saveResults(
        provider: TorrentProvider,
        query: String,
        category: Category,
        results: List<TorrentDescription>
    ) {
        val key = cacheKey(provider, query, category)
        runBlocking {
            torrentCollection.insertOne(CacheDoc(key, results))
        }
    }

    override fun loadResults(provider: TorrentProvider, query: String, category: Category): List<TorrentDescription>? {
        val key = cacheKey(provider, query, category)
        return runBlocking {
            torrentCollection.findOne(CacheDoc::key eq key)?.results
        }
    }


    private fun cacheKey(provider: TorrentProvider, query: String, category: Category): String {
        val raw = "${provider.name}:$query:${category.name}"
        hash.update(raw.toByteArray())
        return Hex.encodeHexString(hash.digest())
    }
}
