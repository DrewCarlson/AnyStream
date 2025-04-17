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
import anystream.models.api.EpisodeResponse
import anystream.models.api.MediaLookupResponse
import anystream.models.api.MovieResponse
import anystream.models.api.PlaybackSessions
import anystream.util.ObjectId
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

val DEFAULT_THROTTLE_SECONDS = 120.seconds
private val DEFAULT_SEGMENT_DURATION = 6.seconds

/** The required playback seconds to store PlaybackStates. */
private val REMEMBER_STATE_THRESHOLD = 60.seconds

// HLS Spec https://datatracker.ietf.org/doc/html/rfc8216
class StreamService(
    private val queries: StreamServiceQueries,
    private val mediaFileProbe: MediaFileProbe,
    private val transcodeSessionManager: TranscodeSessionManager,
    private val transcodePath: Path,
    private val fs: FileSystem,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val hlsPlaylistFactory = HlsPlaylistFactory()

    suspend fun getPlaybackSessions(): PlaybackSessions {
        val sessionKeys = transcodeSessionManager.allSessionIds()
        val storedPlaybackStates = queries.fetchPlaybackStatesByIds(sessionKeys)
        val playbackStates = storedPlaybackStates + transcodeSessionManager.temporaryPlaybackStates()
        val userIds = playbackStates.map(PlaybackState::userId).distinct()
        val users = queries.fetchUsersByIds(userIds)
        val mediaIds = playbackStates.map(PlaybackState::metadataId).distinct()
        val mediaLookups = mediaIds.associateWith { id ->
            queries.fetchMovieById(id)?.run(::MovieResponse)
                ?: queries.fetchEpisodeById(id)?.let { (episode, show) ->
                    EpisodeResponse(episode, show)
                }
        }
        @Suppress("UNCHECKED_CAST")
        return PlaybackSessions(
            playbackStates = playbackStates,
            transcodeSessions = transcodeSessionManager.getSessionMap(),
            users = users.map(User::toPublic).associateBy(UserPublic::id),
            mediaLookups = mediaLookups.filterValues { it != null } as Map<String, MediaLookupResponse>,
        )
    }

    suspend fun getPlaybackState(
        mediaLinkId: String,
        userId: String,
        create: Boolean,
        clientCapabilities: ClientCapabilities? = null
    ): PlaybackState? {
        val state = queries.fetchPlaybackState(mediaLinkId, userId)
        val mediaLink = checkNotNull(queries.fetchMediaLink(mediaLinkId))
        val fileAndMetadataId = mediaLink.filePath?.let { filePath ->
            fs.getPath(filePath) to checkNotNull(mediaLink.metadataId)
        }
        val newState = if (create && state == null) {
            val (file, metadataId) = fileAndMetadataId ?: return null
            val runtime = mediaFileProbe.getFileDuration(file)
            PlaybackState(
                id = ObjectId.next(),
                mediaLinkId = mediaLinkId,
                position = ZERO,
                userId = userId,
                metadataId = metadataId,
                runtime = runtime,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
            )
        } else {
            checkNotNull(state)
        }
        if (create && !transcodeSessionManager.allSessionIds().contains(newState.id)) {
            val (file, _) = fileAndMetadataId ?: return null
            val output = transcodePath.resolve(newState.id).resolve(mediaLinkId)

            val transcodeDecision = determineTranscodeDecision(file, clientCapabilities)

            val mediaInfo = mediaFileProbe.probeFile(file)
            val isHevcVideo = mediaInfo?.isHlsInFmp4 ?: false
            transcodeSessionManager.startTranscode(
                token = newState.id,
                name = mediaLinkId,
                mediaFile = file,
                outputDir = output,
                runtime = newState.runtime,
                startAt = newState.position,
                stopAt = newState.position + DEFAULT_THROTTLE_SECONDS,
                segmentDuration = DEFAULT_SEGMENT_DURATION,
                transcodeDecision = transcodeDecision,
                isHlsInFmp4 = isHevcVideo,
                initialState = if (state == null) newState else null,
            )
        }
        return newState
    }

    suspend fun deletePlaybackState(playbackStateId: String): Boolean {
        return queries.deletePlaybackState(playbackStateId)
    }

    suspend fun updateStatePosition(state: PlaybackState, position: Duration): Boolean {
        if (!position.isPastThreshold()) {
            transcodeSessionManager.updateState(state.id, position)
            return false
        } else {
            transcodeSessionManager.removeTemporaryState(state.id)
        }
        if (queries.updatePlaybackState(state.id, position)) {
            return true
        }
        return queries.insertPlaybackState(
            state.copy(
                position = position,
                updatedAt = Clock.System.now(),
            ),
        )
    }

    suspend fun getPlaylist(
        mediaLinkId: String,
        token: String,
        clientCapabilities: ClientCapabilities
    ): String? {
        val mediaLink = queries.fetchMediaLink(mediaLinkId) ?: return null
        val file = mediaLink.filePath?.run(fs::getPath) ?: return null

        val runtime = mediaFileProbe.getFileDuration(file)
        val segmentDuration = DEFAULT_SEGMENT_DURATION

        val transcodeDecision = determineTranscodeDecision(file, clientCapabilities)

        val mediaInfo = mediaFileProbe.probeFile(file)
        val isHlsInFmp4 = mediaInfo?.isHlsInFmp4 ?: false

        logger.debug("Media info for {}: codec={}, isHlsInFmp4={}", mediaLinkId, mediaInfo?.videoCodec, isHlsInFmp4)

        if (!transcodeSessionManager.allSessionIds().contains(token)) {
            val output = transcodePath.resolve(token).resolve(mediaLinkId)
            transcodeSessionManager.startTranscode(
                token = token,
                name = mediaLinkId,
                mediaFile = file,
                outputDir = output,
                runtime = runtime,
                segmentDuration = segmentDuration,
                transcodeDecision = transcodeDecision,
                isHlsInFmp4 = isHlsInFmp4,
            )
        }

        return hlsPlaylistFactory.createVariantPlaylist(
            name = mediaLinkId,
            mediaFile = file,
            token = token,
            runtime = runtime,
            segmentDuration = segmentDuration,
            isHlsInFmp4 = isHlsInFmp4
        )
    }

    suspend fun getFilePathForSegment(token: String, segmentFile: String): Path? {
        return transcodeSessionManager.getFilePathForSegment(token, segmentFile)
    }

    suspend fun stopSession(token: String, deleteOutput: Boolean) {
        return transcodeSessionManager.stopSession(token, deleteOutput)
    }

    private suspend fun determineTranscodeDecision(
        file: Path,
        clientCapabilities: ClientCapabilities?
    ): TranscodeDecision {
        if (clientCapabilities == null) {
            return TranscodeDecision.FULL
        }

        val mediaInfo = mediaFileProbe.probeFile(file) ?: return TranscodeDecision.FULL
        return mediaFileProbe.determineTranscodeNeeds(
            mediaInfo,
            clientCapabilities.supportedVideoCodecs,
            clientCapabilities.supportedAudioCodecs,
            clientCapabilities.supportedContainers
        )
    }
}

fun Duration.isPastThreshold(): Boolean {
    return this > REMEMBER_STATE_THRESHOLD
}
