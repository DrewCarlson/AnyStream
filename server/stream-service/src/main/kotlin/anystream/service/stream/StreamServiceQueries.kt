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
package anystream.service.stream

import anystream.models.*

interface StreamServiceQueries {

    suspend fun fetchUsersByIds(ids: List<Int>): List<User>
    suspend fun fetchPlaybackStatesByIds(ids: List<String>): List<PlaybackState>
    suspend fun fetchMovieById(id: String): Movie?
    suspend fun fetchEpisodeById(id: String): Pair<Episode, TvShow>?
    fun fetchMediaLink(mediaLinkId: String): MediaLink?
    fun fetchPlaybackState(mediaLinkId: String, userId: Int): PlaybackState?

    fun fetchPlaybackStateById(id: String): PlaybackState?

    fun insertPlaybackState(playbackState: PlaybackState): Boolean
    fun updatePlaybackState(stateId: String, position: Double): Boolean
    fun deletePlaybackState(playbackStateId: String): Boolean
}
