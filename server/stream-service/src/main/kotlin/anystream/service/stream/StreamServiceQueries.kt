/*
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
import kotlin.time.Duration

interface StreamServiceQueries {
    suspend fun fetchUsersByIds(ids: List<UserId>): List<User>

    suspend fun fetchPlaybackStatesByIds(ids: List<PlaybackStateId>): List<PlaybackState>

    suspend fun fetchMovieById(id: MetadataId): Movie?

    suspend fun fetchEpisodeById(id: MetadataId): Pair<Episode, TvShow>?

    suspend fun fetchMediaLink(mediaLinkId: MediaLinkId): MediaLink?

    suspend fun fetchPlaybackState(
        mediaLinkId: MediaLinkId,
        userId: UserId,
    ): PlaybackState?

    suspend fun fetchPlaybackStateById(id: PlaybackStateId): PlaybackState?

    suspend fun insertPlaybackState(playbackState: PlaybackState): Boolean

    suspend fun updatePlaybackState(
        stateId: PlaybackStateId,
        position: Duration,
    ): Boolean

    suspend fun deletePlaybackState(playbackStateId: PlaybackStateId): Boolean
}
