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
package anystream.torrent.search

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class TorrentDescription2(
    val provider: String,
    val magnetUrl: String,
    val title: String,
    val size: Long,
    val seeds: Int,
    val peers: Int,
) {
    @Transient
    val hash: String = magnetUrl
        .substringAfter("xt=urn:btih:")
        .substringBefore("&")
}
