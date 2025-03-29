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

import anystream.db.MediaLinkDao
import anystream.db.MetadataDao
import anystream.db.PlaybackStatesDao
import anystream.db.UserDao
import anystream.db.tables.references.METADATA
import anystream.db.tables.references.PLAYBACK_STATE
import anystream.db.util.awaitFirstOrNullInto
import anystream.models.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

class StreamServiceQueriesJooq(
    private val db: DSLContext,
    private val userDao: UserDao,
    private val playbackStatesDao: PlaybackStatesDao,
    private val metadataDao: MetadataDao,
    private val mediaLinkDao: MediaLinkDao,
) : StreamServiceQueries {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun fetchUsersByIds(ids: List<String>): List<User> {
        if (ids.isEmpty()) return emptyList()
        return userDao.fetchUsers(ids)
    }

    override suspend fun fetchPlaybackStatesByIds(ids: List<String>): List<PlaybackState> {
        if (ids.isEmpty()) return emptyList()
        return playbackStatesDao.fetchByIds(ids)
    }

    override suspend fun fetchMovieById(id: String): Movie? {
        return metadataDao.findByIdAndType(id, MediaType.MOVIE)?.toMovieModel()
    }

    override suspend fun fetchEpisodeById(id: String): Pair<Episode, TvShow>? {
        return try {
            val episodeAlias = METADATA.`as`("episode")
            val showAlias = METADATA.`as`("show")
            val (episode, show) = db.select()
                .from(episodeAlias)
                .join(showAlias)
                .on(episodeAlias.ID.eq(showAlias.ROOT_ID))
                .where(
                    episodeAlias.ID.eq(id),
                    episodeAlias.MEDIA_TYPE.eq(MediaType.TV_EPISODE),
                    showAlias.MEDIA_TYPE.eq(MediaType.TV_SHOW)
                )
                .fetchAsync()
                .await()
                .into(Metadata::class.java)

            Pair(
                episode.toTvEpisodeModel(),
                show.toTvShowModel()
            )
        } catch (e: Throwable) {
            logger.error("Failed to load Movie id='$id'", e)
            null
        }
    }

    override suspend fun fetchMediaLink(mediaLinkId: String): MediaLink? {
        return mediaLinkDao.findById(mediaLinkId)
    }

    override suspend fun fetchPlaybackStateById(id: String): PlaybackState? {
        return playbackStatesDao.fetchById(id)
    }

    override suspend fun fetchPlaybackState(mediaLinkId: String, userId: String): PlaybackState? {
        return try {
            db.selectFrom(PLAYBACK_STATE)
                .where(
                    PLAYBACK_STATE.USER_ID.eq(userId),
                    PLAYBACK_STATE.MEDIA_LINK_ID.eq(mediaLinkId)
                )
                .awaitFirstOrNullInto()
        } catch (e: Throwable) {
            logger.error("Failed to load PlaybackState, mediaLinkId='$mediaLinkId' userId='$userId'", e)
            null
        }
    }

    override suspend fun insertPlaybackState(playbackState: PlaybackState): Boolean {
        return playbackStatesDao.insert(playbackState)
    }

    override suspend fun updatePlaybackState(stateId: String, position: Double): Boolean {
        return try {
            db.update(PLAYBACK_STATE)
                .set(PLAYBACK_STATE.POSITION, position)
                .where(PLAYBACK_STATE.ID.eq(stateId))
                .awaitFirstOrNull() == 1
        } catch (e: Throwable) {
            logger.error("Failed to update PlaybackState position", e)
            false
        }
    }

    override suspend fun deletePlaybackState(playbackStateId: String): Boolean {
        return try {
            db.deleteFrom(PLAYBACK_STATE)
                .where(PLAYBACK_STATE.ID.eq(playbackStateId))
                .awaitFirstOrNull()
            true
        } catch (e: Throwable) {
            logger.error("Failed to delete PlaybackState, id='$playbackStateId'", e)
            false
        }
    }
}
