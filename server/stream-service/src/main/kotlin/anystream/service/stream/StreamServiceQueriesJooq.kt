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

import anystream.db.pojos.*
import anystream.db.tables.references.MEDIA_LINK
import anystream.db.tables.references.METADATA
import anystream.db.tables.references.PLAYBACK_STATE
import anystream.db.tables.references.USER
import anystream.db.util.intoType
import anystream.models.*
import kotlinx.coroutines.future.await
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

class StreamServiceQueriesJooq(
    private val db: DSLContext,
) : StreamServiceQueries {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun fetchUsersByIds(ids: List<String>): List<User> {
        if (ids.isEmpty()) return emptyList()
        return try {
            db.fetch(USER, USER.ID.`in`(ids)).intoType()
        } catch (e: Throwable) {
            logger.error("Failed to load Users ids=$ids", e)
            emptyList()
        }
    }

    override suspend fun fetchPlaybackStatesByIds(ids: List<String>): List<PlaybackState> {
        if (ids.isEmpty()) return emptyList()
        return try {
            db.fetch(PLAYBACK_STATE, PLAYBACK_STATE.ID.`in`(ids)).intoType()
        } catch (e: Throwable) {
            logger.error("Failed to load PlaybackStates ids=${ids.joinToString()}", e)
            emptyList()
        }
    }

    override suspend fun fetchMovieById(id: String): Movie? {
        return try {
            db.fetchOne(
                METADATA,
                METADATA.ID.eq(id),
                METADATA.MEDIA_TYPE.eq(MediaType.MOVIE)
            )?.intoType<Metadata>()
                ?.toMovieModel()
        } catch (e: Throwable) {
            logger.error("Failed to load Movie id='$id'", e)
            null
        }
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

    override fun fetchMediaLink(mediaLinkId: String): MediaLink? {
        return try {
            db.fetchOne(MEDIA_LINK, MEDIA_LINK.ID.eq(mediaLinkId))
                ?.intoType()
        } catch (e: Throwable) {
            logger.error("Failed to find MediaReference '$mediaLinkId'", e)
            null
        }
    }

    override fun fetchPlaybackStateById(id: String): PlaybackState? {
        return try {
            db.fetchOne(PLAYBACK_STATE, PLAYBACK_STATE.ID.eq(id))
                ?.intoType()
        } catch (e: Throwable) {
            logger.error("Failed to load PlaybackState id='$id'", e)
            null
        }
    }

    override fun fetchPlaybackState(mediaLinkId: String, userId: String): PlaybackState? {
        return try {
            db.fetchOne(
                PLAYBACK_STATE,
                PLAYBACK_STATE.USER_ID.eq(userId),
                PLAYBACK_STATE.MEDIA_LINK_ID.eq(mediaLinkId)
            )?.intoType()
        } catch (e: Throwable) {
            logger.error("Failed to load PlaybackState, mediaLinkId='$mediaLinkId' userId='$userId'", e)
            null
        }
    }

    override fun insertPlaybackState(playbackState: PlaybackState): Boolean {
        return try {
            db.newRecord(PLAYBACK_STATE, playbackState)
                .apply { from(playbackState) }
                .store()
            true
        } catch (e: Throwable) {
            logger.error("Failed to update PlaybackState, id='${playbackState.id}'", e)
            false
        }
    }

    override fun updatePlaybackState(stateId: String, position: Double): Boolean {
        return try {
            db.update(PLAYBACK_STATE)
                .set(PLAYBACK_STATE.POSITION, position)
                .where(PLAYBACK_STATE.ID.eq(stateId))
                .execute()
            true
        } catch (e: Throwable) {
            logger.error("Failed to update PlaybackState position", e)
            false
        }
    }

    override fun deletePlaybackState(playbackStateId: String): Boolean {
        return try {
            db.deleteFrom(PLAYBACK_STATE)
                .where(PLAYBACK_STATE.ID.eq(playbackStateId))
                .execute()
            true
        } catch (e: Throwable) {
            logger.error("Failed to delete PlaybackState, id='$playbackStateId'", e)
            false
        }
    }
}
