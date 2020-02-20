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
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

internal class PirateBayProvider(
    private val httpClient: HttpClient
) : BaseTorrentProvider() {

    override val name: String = "ThePirateBay"
    override val baseUrl: String = "https://apibay.org"
    override val tokenPath: String = ""
    override val searchPath: String = "/q.php?q={query}&cat={category}"

    override val categories = mapOf(
        Category.ALL to "",
        Category.AUDIO to "100",
        Category.MUSIC to "101",
        Category.VIDEO to "200",
        Category.MOVIES to "201",
        Category.TV to "205",
        Category.APPS to "300",
        Category.GAMES to "400",
        Category.XXX to "500",
        Category.OTHER to "600",
    )

    private val trackers = listOf(
        "udp://tracker.coppersurfer.tk:6969/announce",
        "udp://9.rarbg.to:2920/announce",
        "udp://tracker.opentrackr.org:1337",
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://tracker.leechers-paradise.org:6969/announce",
        "udp://tracker.pirateparty.gr:6969/announce",
        "udp://tracker.cyberia.is:6969/announce"
    ).map { it.encodeURLQueryComponent() }

    override suspend fun search(query: String, category: Category, limit: Int): List<TorrentDescription> {
        val categoryString = categories[category]

        if (query.isBlank() || categoryString.isNullOrBlank()) {
            return emptyList()
        }
        val response = httpClient.get<HttpResponse> {
            url {
                takeFrom(baseUrl)
                takeFrom(
                    searchPath
                        .replace("{query}", query.encodeURLQueryComponent())
                        .replace("{category}", categoryString)
                )
            }
        }

        return if (response.status == HttpStatusCode.OK) {
            val torrents = response.call.receive<JsonArray>()
            val noResults = torrents.singleOrNull()
                ?.jsonObject
                ?.get("info_hash")
                ?.jsonPrimitive
                ?.content
                ?.all { it == '0' } ?: false
            if (noResults) {
                emptyList()
            } else {
                torrents.map { element ->
                    val torrentName = element.jsonObject["name"]?.jsonPrimitive?.content ?: "<unknown>"
                    TorrentDescription(
                        provider = name,
                        magnetUrl = formatMagnet(
                            name = torrentName,
                            infoHash = checkNotNull(element.jsonObject["info_hash"]).jsonPrimitive.content
                        ),
                        title = torrentName,
                        size = element.jsonObject["size"]?.jsonPrimitive?.long ?: -1,
                        seeds = element.jsonObject["seeders"]?.jsonPrimitive?.int ?: -1,
                        peers = element.jsonObject["leechers"]?.jsonPrimitive?.int ?: -1,
                    )
                }
            }
        } else {
            emptyList()
        }
    }

    private fun formatMagnet(infoHash: String, name: String): String {
        val trackersQueryString = "&tr=${trackers.joinToString("&tr=")}"
        return "magnet:?xt=urn:btih:${infoHash}&dn=${name.encodeURLQueryComponent()}${trackersQueryString}"
    }
}
