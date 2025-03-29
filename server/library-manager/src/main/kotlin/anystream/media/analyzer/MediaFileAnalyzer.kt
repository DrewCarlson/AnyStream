/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
package anystream.media.analyzer

import anystream.db.MediaLinkDao
import anystream.media.VIDEO_EXTENSIONS
import anystream.media.util.toStreamEncoding
import anystream.models.MediaLink
import anystream.models.StreamEncoding
import anystream.models.api.MediaAnalyzerResult
import com.github.kokorin.jaffree.JaffreeException
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffprobe.FFprobe
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.exists

class MediaFileAnalyzer(
    private val ffprobe: () -> FFprobe,
    private val mediaLinkDao: MediaLinkDao,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun analyzeMediaFiles(
        mediaLinkIds: List<String>,
        overwrite: Boolean = false,
    ): List<MediaAnalyzerResult> {
        val mediaLinks = mediaLinkDao.findByGids(mediaLinkIds)

        logger.debug(
            "Importing stream details for {} item(s), ignored {} invalid item(s)",
            mediaLinkIds.size,
            mediaLinkIds.size - mediaLinks.size,
        )

        return mediaLinks
            .filter { mediaLink ->
                // TODO: Support audio files
                val extension = mediaLink.filePath?.substringAfterLast('.', "")?.lowercase()
                !extension.isNullOrBlank() && VIDEO_EXTENSIONS.contains(extension)
            }
            .mapNotNull { mediaLink ->
                val mediaLinkId = checkNotNull(mediaLink.id)
                val hasDetails = mediaLinkDao.countStreamDetails(mediaLinkId) > 0
                if (!hasDetails || overwrite) {
                    val result = processMediaFileStreams(mediaLink)
                    val streamDetails = (result as? MediaAnalyzerResult.Success)?.streams.orEmpty()
                    try {
                        if (streamDetails.isNotEmpty()) {
                            mediaLinkDao.insertStreamDetails(streamDetails)
                        }
                        // TODO: restore media link streams
                        //val updatedStreams = checkNotNull(mediaLinkDao.findByGid(mediaLink.gid))
                        //     .streams
                        //    .map { it.toStreamEncodingDb() }
                        MediaAnalyzerResult.Success(mediaLink.id, emptyList())//updatedStreams)
                    } catch (e: Throwable) {
                        logger.error("Failed to update stream data", e)
                        MediaAnalyzerResult.ErrorDatabaseException(e.stackTraceToString())
                    }
                } else {
                    null
                }
            }
            .also { results -> logger.debug("Processed {} item(s)", results.size) }
            .ifEmpty { listOf(MediaAnalyzerResult.ErrorNothingToImport) }
    }

    private suspend fun processMediaFileStreams(mediaLink: MediaLink): MediaAnalyzerResult {
        if (!Path(mediaLink.filePath.orEmpty()).exists()) {
            logger.error("Media file reference path does not exist: {} {}", mediaLink.id, mediaLink.filePath)
            return MediaAnalyzerResult.ErrorFileNotFound
        }

        logger.debug("Processing media streams for {}", mediaLink)
        return try {
            val streams = awaitAll(
                ffprobe().processStreamsAsync(mediaLink, StreamType.VIDEO_NOT_PICTURE),
                ffprobe().processStreamsAsync(mediaLink, StreamType.AUDIO),
                ffprobe().processStreamsAsync(mediaLink, StreamType.SUBTITLE),
            ).flatten()
            MediaAnalyzerResult.Success(mediaLink.id, streams)
        } catch (e: JaffreeException) {
            logger.error("FFProbe error, failed to extract stream details", e)
            MediaAnalyzerResult.ProcessError(e.stackTraceToString())
        }
    }

    private fun FFprobe.processStreamsAsync(
        mediaLink: MediaLink,
        streamType: StreamType,
    ): Deferred<List<StreamEncoding>> {
        return scope.async {
            setShowStreams(true)
            setShowFormat(true)
            setSelectStreams(streamType)
            setShowEntries("stream=index:stream_tags=language,LANGUAGE,title")
            setInput(mediaLink.filePath.orEmpty())
            execute().streams.mapNotNull { stream ->
                stream.toStreamEncoding(requireNotNull(mediaLink.id))
            }
        }
    }
}