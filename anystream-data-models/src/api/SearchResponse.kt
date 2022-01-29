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

import anystream.models.Episode
import anystream.models.MediaReference
import anystream.models.Movie
import anystream.models.TvShow
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val movies: List<Movie> = emptyList(),
    val tvShows: List<TvShowResult> = emptyList(),
    val episodes: List<EpisodeResult> = emptyList(),
    val mediaReferences: Map<String, MediaReference> = emptyMap(),
) {
    fun hasResult(): Boolean =
        movies.isNotEmpty() || tvShows.isNotEmpty() || episodes.isNotEmpty()

    @Serializable
    data class TvShowResult(
        val tvShow: TvShow,
        val seasonCount: Int,
    )

    @Serializable
    data class EpisodeResult(
        val episode: Episode,
        val tvShow: TvShow,
    )
}
