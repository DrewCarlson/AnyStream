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
import com.mongodb.MongoException
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.slf4j.LoggerFactory
import java.time.Instant

class StreamServiceQueriesMongo(
    mongodb: CoroutineDatabase
) : StreamServiceQueries {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val usersDb = mongodb.getCollection<User>()
    private val playbackStateDb = mongodb.getCollection<PlaybackState>()
    private val moviesDb = mongodb.getCollection<Movie>()
    private val episodeDb = mongodb.getCollection<Episode>()
    private val tvShowDb = mongodb.getCollection<TvShow>()
    private val mediaRefs = mongodb.getCollection<MediaReference>()

    override suspend fun fetchUsersByIds(ids: List<String>): List<User> {
        return try {
            usersDb.find(User::id `in` ids).toList()
        } catch (e: MongoException) {
            logger.error("Failed to load Users ids=$ids", e)
            emptyList()
        }
    }

    override suspend fun fetchPlaybackStatesByIds(ids: List<String>): List<PlaybackState> {
        return try {
            playbackStateDb.find(PlaybackState::id `in` ids).toList()
        } catch (e: MongoException) {
            logger.error("Failed to load PlaybackStates ids=${ids.joinToString()}", e)
            emptyList()
        }
    }

    override suspend fun fetchMovieById(id: String): Movie? {
        return try {
            moviesDb.findOneById(id)
        } catch (e: MongoException) {
            logger.error("Failed to load Movie id='$id'", e)
            null
        }
    }

    override suspend fun fetchEpisodeById(id: String): Pair<Episode, TvShow>? {
        return try {
            val episode = episodeDb.findOneById(id) ?: return null
            val tvShow = tvShowDb.findOneById(episode.showId) ?: return null
            Pair(episode, tvShow)
        } catch (e: MongoException) {
            logger.error("Failed to load Movie id='$id'", e)
            null
        }
    }

    override suspend fun fetchMediaRef(mediaRefId: String): MediaReference? {
        return try {
            mediaRefs.findOneById(mediaRefId)
        } catch (e: MongoException) {
            logger.error("Failed to find MediaReference '$mediaRefId'")
            null
        }
    }

    override suspend fun fetchPlaybackState(mediaRefId: String, userId: String): PlaybackState? {
        return try {
            playbackStateDb.findOne(
                PlaybackState::userId eq userId,
                PlaybackState::mediaReferenceId eq mediaRefId,
            )
        } catch (e: MongoException) {
            logger.error("Failed to load PlaybackState, mediaRefId='$mediaRefId' userId='$userId'")
            null
        }
    }

    override suspend fun insertPlaybackState(playbackState: PlaybackState): Boolean {
        return try {
            playbackStateDb.insertOne(playbackState)
            true
        } catch (e: MongoException) {
            logger.error("Failed to update PlaybackState, id='${playbackState.id}'", e)
            false
        }
    }

    override suspend fun updatePlaybackState(
        mediaRefId: String,
        userId: String,
        position: Double
    ): Boolean {
        return try {
            playbackStateDb.updateOne(
                filter = and(
                    PlaybackState::userId eq userId,
                    PlaybackState::mediaReferenceId eq mediaRefId,
                ),
                update = combine(
                    setValue(PlaybackState::position, position),
                    setValue(PlaybackState::updatedAt, Instant.now().toEpochMilli()),
                )
            )
            true
        } catch (e: MongoException) {
            logger.error("Failed to update PlaybackState position", e)
            false
        }
    }

    override suspend fun deletePlaybackState(playbackStateId: String): Boolean {
        return try {
            playbackStateDb.deleteOneById(playbackStateId).deletedCount > 0
        } catch (e: MongoException) {
            logger.error("Failed to delete PlaybackState, id='$playbackStateId'", e)
            false
        }
    }
}
