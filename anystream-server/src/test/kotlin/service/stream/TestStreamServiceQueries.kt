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

class TestStreamServiceQueries : StreamServiceQueries {
    override suspend fun fetchUsersByIds(ids: List<String>): List<User> {
        return emptyList()
    }

    override suspend fun fetchPlaybackStatesByIds(ids: List<String>): List<PlaybackState> {
        return emptyList()
    }

    override suspend fun fetchMovieById(id: String): Movie? {
        return null
    }

    override suspend fun fetchEpisodeById(id: String): Pair<Episode, TvShow>? {
        return null
    }

    override suspend fun fetchMediaRef(mediaRefId: String): MediaReference? {
        return null
    }

    override suspend fun fetchPlaybackState(mediaRefId: String, userId: String): PlaybackState? {
        return null
    }

    override suspend fun insertPlaybackState(playbackState: PlaybackState): Boolean {
        return false
    }

    override suspend fun updatePlaybackState(
        mediaRefId: String,
        userId: String,
        position: Double
    ): Boolean {
        return false
    }

    override suspend fun deletePlaybackState(playbackStateId: String): Boolean {
        return false
    }
}
