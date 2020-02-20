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


interface TorrentProvider {

    /** The Provider's name. */
    val name: String

    /** The Provider's base url. (ex. `https://provider.link`) */
    val baseUrl: String

    /** The Provider's path to acquire a token. */
    val tokenPath: String

    /** The Provider's path to search search data. */
    val searchPath: String

    /** Maps a url safe string of provider categories to a [Category]. */
    val categories: Map<Category, String>

    /** The result limit for search requests. */
    val resultsPerPage: Int get() = 100

    /** True if the provider is enabled. */
    val isEnabled: Boolean

    /**
     * Execute a search for the given [query] in [category], returning
     * [TorrentDescription]s for each of the Provider's entries.
     */
    suspend fun search(query: String, category: Category, limit: Int): List<TorrentDescription>

    fun enable(username: String? = null, password: String? = null, cookies: List<String> = emptyList())

    fun disable()
}
