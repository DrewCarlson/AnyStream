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
package anystream.models.api

import anystream.models.CastCredit
import anystream.models.CrewCredit
import anystream.models.Genre
import anystream.models.MediaLink
import anystream.models.Movie
import anystream.models.PlaybackState
import anystream.models.ProductionCompany
import anystream.models.StreamEncoding
import kotlinx.serialization.Serializable

@Serializable
data class MovieResponse(
    val movie: Movie,
    override val mediaLinks: List<MediaLink> = emptyList(),
    override val streamEncodings: Map<String, List<StreamEncoding>> = emptyMap(),
    override val genres: List<Genre> = emptyList(),
    override val companies: List<ProductionCompany> = emptyList(),
    override val cast: List<CastCredit> = emptyList(),
    override val crew: List<CrewCredit> = emptyList(),
    val playbackState: PlaybackState? = null,
) : MediaLookupResponse {
    override val title: String = movie.title
}
