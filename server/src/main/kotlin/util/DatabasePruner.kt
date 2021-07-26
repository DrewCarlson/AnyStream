/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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

import anystream.models.*
import com.mongodb.MongoException
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.nin

class DatabasePruner(
    mongodb: CoroutineDatabase,
) {

    private val usersDb = mongodb.getCollection<User>()
    private val userCredentialsDb = mongodb.getCollection<UserCredentials>()
    private val inviteCodeDb = mongodb.getCollection<InviteCode>()
    private val moviesDb = mongodb.getCollection<Movie>()
    private val tvShowDb = mongodb.getCollection<TvShow>()
    private val episodeDb = mongodb.getCollection<Episode>()
    private val mediaRefsDb = mongodb.getCollection<MediaReference>()
    private val playbackStateDb = mongodb.getCollection<PlaybackState>()

    suspend fun prune(): Result {
        val userIds = usersDb.find().toList().map(User::id)
        val movieIds = moviesDb.find().toList().map(Movie::id)
        val showIds = tvShowDb.find().toList().map(TvShow::id)
        val mediaRefIds = mediaRefsDb.find().toList().map(MediaReference::id)
        val contentIds = movieIds + showIds

        val errors = mutableListOf<String>()
        // prune user data
        val prunedInviteCodes = try {
            inviteCodeDb.deleteMany(InviteCode::createdByUserId nin userIds).deletedCount
        } catch (e: MongoException) {
            0
        }
        val prunedUserCredentials = try {
            userCredentialsDb.deleteMany(UserCredentials::id nin userIds).deletedCount
        } catch (e: MongoException) {
            0
        }
        val prunedPlaybackStates1 = try {
            playbackStateDb.deleteMany(PlaybackState::userId nin userIds).deletedCount
        } catch (e: MongoException) {
            0
        }

        // prune media data
        val prunedMediaRefs = try {
            mediaRefsDb.deleteMany(MediaReference::contentId nin contentIds).deletedCount
        } catch (e: MongoException) {
            0
        }
        val prunedPlaybackStates2 = try {
            playbackStateDb
                .deleteMany(PlaybackState::mediaReferenceId nin mediaRefIds)
                .deletedCount
        } catch (e: MongoException) {
            0
        }
        val prunedEpisodes = try {
            episodeDb.deleteMany(Episode::showId nin showIds).deletedCount
        } catch (e: MongoException) {
            0
        }

        return Result(
            prunedInviteCodes = prunedInviteCodes,
            prunedUserCredentials = prunedUserCredentials,
            prunedPlaybackStates = prunedPlaybackStates1 + prunedPlaybackStates2,
            prunedEpisodes = prunedEpisodes,
            prunedMediaRefs = prunedMediaRefs,
            errors = errors.toList(),
        )
    }

    data class Result(
        val prunedInviteCodes: Long,
        val prunedUserCredentials: Long,
        val prunedPlaybackStates: Long,
        val prunedMediaRefs: Long,
        val prunedEpisodes: Long,
        val errors: List<String>
    )
}