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
package drewcarlson.torrentsearch

import drewcarlson.torrentsearch.providers.LibreProvider
import drewcarlson.torrentsearch.providers.PirateBayProvider
import drewcarlson.torrentsearch.providers.RarbgProvider
import io.ktor.client.HttpClient
import io.ktor.client.features.*
import io.ktor.client.features.cookies.AcceptAllCookiesStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.take

class TorrentSearch(
    private val providerCache: TorrentProviderCache? = null,
    httpClient: HttpClient = HttpClient(),
    vararg providers: TorrentProvider
) {

    private val http = httpClient.config {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }

        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
    }

    private val providers = listOf(
        RarbgProvider(http, providerCache),
        PirateBayProvider(http),
        LibreProvider()
    ) + providers

    /**
     * Search all enabled providers with [query] and [category].
     *
     * All results are merged into a single list. [limit] is used
     * when possible to limit the result count from each provider.
     */
    suspend fun search(query: String, category: Category, limit: Int): List<TorrentDescription> {
        return searchFlow(query, category, limit).reduce { acc, next -> acc + next }
    }

    /**
     * Search all enabled providers with [query] and [category],
     * emitting each set of results as the providers respond.
     *
     * [limit] is used when possible to limit the result count
     * from each provider.
     */
    fun searchFlow(query: String, category: Category, limit: Int): Flow<List<TorrentDescription>> {
        return providers
            .filter(TorrentProvider::isEnabled)
            .map { provider ->
                println("Searching '${provider.name}' for '$query'")
                flow {
                    try {
                        emit(provider.search(query, category, limit))
                    } catch (e: ClientRequestException) {
                        println("Search failed for '${provider.name}'")
                        e.printStackTrace()
                    }
                }.onEach { results ->
                    if (results.isNotEmpty()) {
                        providerCache?.saveResults(provider, query, category, results)
                    }
                }.onStart {
                    val cacheResult = providerCache?.loadResults(provider, query, category)
                    if (cacheResult != null) {
                        emit(cacheResult)
                    }
                }.take(1)
            }
            .merge()
            .flowOn(Dispatchers.Default)
    }

    /**
     * Returns a list of enabled providers.
     */
    fun enabledProviders() = providers.filter(TorrentProvider::isEnabled).toList()

    /**
     * Returns a list of available providers.
     */
    fun availableProviders() = providers.toList()

    /**
     * Enable the provider [name] with the included credentials and [cookies].
     */
    fun enableProvider(name: String, username: String?, password: String?, cookies: List<String>) {
        providers.singleOrNull { it.name == name }
            ?.enable(username, password, cookies)
    }

    /**
     * Disable the provider [name].
     */
    fun disableProvider(name: String) {
        providers.singleOrNull { it.name == name }?.disable()
    }
}
