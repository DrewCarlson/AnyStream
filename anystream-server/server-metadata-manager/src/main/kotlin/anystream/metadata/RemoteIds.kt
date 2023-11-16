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

import app.moviebase.tmdb.model.*

val String.isRemoteId: Boolean get() = split(':').run { size == 3 || size == 4 }
fun TmdbMovie.toRemoteId(): String = "tmdb:movie:$id"
fun TmdbShow.toRemoteId(): String = "tmdb:tv:$id"
fun TmdbSeason.toRemoteId(showId: Int): String = "tmdb:tv:$showId-$seasonNumber"
fun TmdbEpisode.toRemoteId(showId: Int): String = "tmdb:tv:$showId-$seasonNumber-$episodeNumber"
fun TmdbMovieDetail.toRemoteId(): String = "tmdb:movie:$id"
fun TmdbShowDetail.toRemoteId(): String = "tmdb:tv:$id"
fun TmdbSeasonDetail.toRemoteId(showId: Int): String = "tmdb:tv:$showId-$seasonNumber"
