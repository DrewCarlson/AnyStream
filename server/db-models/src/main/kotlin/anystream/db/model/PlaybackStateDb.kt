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
package anystream.db.model

import anystream.models.PlaybackState
import kotlinx.datetime.Instant

/**
 * A data class representing the current playback state of a media file for a specific user. This class is used
 * as a database model to store and manage information related to the user's progress in watching or listening
 * to media content.
 *
 * @property id Unique identifier for the playback state entry.
 * @property gid Globally unique identifier for the playback state entry.
 * @property mediaLinkId Unique identifier for the associated media link entry.
 * @property metadataGid Globally unique identifier for the associated metadata entry.
 * @property userId Unique identifier for the user whose playback state is being tracked.
 * @property position Current playback position in the media file in seconds.
 * @property runtime Total runtime of the media file in seconds.
 * @property updatedAt Timestamp of when the playback state entry was last updated.
 */
data class PlaybackStateDb(
    val id: Int,
    val gid: String,
    val mediaLinkId: String,
    val metadataGid: String,
    val userId: Int,
    val position: Double,
    val runtime: Double,
    val updatedAt: Instant,
) {
    companion object {
        fun from(state: PlaybackState): PlaybackStateDb {
            return PlaybackStateDb(
                id = -1,
                gid = state.id,
                mediaLinkId = state.mediaLinkGid,
                metadataGid = state.metadataGid,
                userId = state.userId,
                position = state.position,
                runtime = state.runtime,
                updatedAt = state.updatedAt,
            )
        }
    }

    fun toStateModel(): PlaybackState {
        return PlaybackState(
            id = gid,
            mediaLinkGid = mediaLinkId,
            metadataGid = metadataGid,
            userId = userId,
            position = position,
            runtime = runtime,
            updatedAt = updatedAt,
        )
    }
}
