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
package drewcarlson.torrentsearch.providers

import drewcarlson.torrentsearch.Category
import drewcarlson.torrentsearch.TorrentDescription
import drewcarlson.torrentsearch.TorrentProviderCache
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.encodeURLParameter
import io.ktor.http.takeFrom
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

private const val API_REQUEST_DELAY = 3000L

internal class RarbgProvider(
    private val httpClient: HttpClient,
    private val providerCache: TorrentProviderCache? = null,
    prefetchToken: Boolean = true,
) : BaseTorrentProvider() {
    override val name = "Rarbg"
    override val baseUrl = "https://torrentapi.org"
    override val tokenPath = "/pubapi_v2.php?get_token=get_token&app_id=TorrentSearch"
    override val searchPath =
        "/pubapi_v2.php?app_id=TorrentSearch&search_string={query}&category={category}&mode=search&format=json_extended&sort=seeders&limit={limit}&token={token}"
    override val categories = mapOf(
        Category.ALL to "1;4;14;15;16;17;21;22;42;18;19;41;27;28;29;30;31;32;40;23;24;25;26;33;34;43;44;45;46;47;48;49;50;51;52",
        Category.MOVIES to "14;17;42;44;45;46;47;48;50;51;52",
        Category.XXX to "1;4",
        Category.GAMES to "1;27;28;29;30;31;32;40",
        Category.TV to "1;18;41;49",
        Category.MUSIC to "1;23;24;25;26",
        Category.APPS to "1;33;34;43",
        Category.BOOKS to "35"
    )

    private val mutex = Mutex()
    private var token: String? = null

    init {
        if (prefetchToken) {
            // Prefetch token
            launch { readToken() }
        }
    }

    override suspend fun search(query: String, category: Category, limit: Int): List<TorrentDescription> {
        val categoryString = categories[category]

        if (query.isBlank() || categoryString.isNullOrBlank()) {
            return emptyList()
        }

        val result = fetchSearchResults(
            encodedQuery = query.encodeURLParameter(),
            categoryString = categoryString,
            tokenString = readToken() ?: "",
            limit = limit
        )

        launch {
            mutex.withLock { delay(API_REQUEST_DELAY) }
        }

        val errorCode = result["error_code"]?.jsonPrimitive?.intOrNull
        return if (errorCode == null) {
             result["torrent_results"]!!
                 .jsonArray
                 .map { it.asTorrentDescription() }
        } else {
            // TODO: Handle error codes
            //   - 20: No results
            emptyList()
        }
    }

    private suspend fun fetchSearchResults(
        encodedQuery: String,
        categoryString: String,
        tokenString: String,
        limit: Int
    ): JsonObject = mutex.withLock {
        httpClient.get {
            url {
                takeFrom(baseUrl)
                takeFrom(
                    searchPath
                        .replace("{query}", encodedQuery)
                        .replace("{category}", categoryString)
                        .replace("{token}", tokenString)
                        .replace("{limit}", limit.toString())
                )
            }
        }
    }

    private suspend fun fetchToken(): String {
        return httpClient.get<JsonObject> {
            url {
                takeFrom(baseUrl)
                takeFrom(tokenPath)
            }
        }["token"]!!
            .jsonPrimitive
            .content
    }

    internal suspend fun readToken(): String? {
        if (token == null) {
            token = mutex.withLock {
                token ?: providerCache?.loadToken(this) ?: try {
                    fetchToken().also { token ->
                        providerCache?.saveToken(this, token)
                        delay(API_REQUEST_DELAY)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        return token
    }

    private fun JsonElement.asTorrentDescription(): TorrentDescription {
        return TorrentDescription(
            provider = name,
            magnetUrl = jsonObject["download"]!!.jsonPrimitive.content,
            title = jsonObject["title"]!!.jsonPrimitive.content,
            seeds = jsonObject["seeders"]!!.jsonPrimitive.int,
            peers = jsonObject["leechers"]!!.jsonPrimitive.int,
            size = jsonObject["size"]!!.jsonPrimitive.long
        )
    }
}
