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
package anystream.media

import anystream.db.MediaReferencesDao
import anystream.db.model.StreamEncodingDetailsDb
import anystream.models.StreamEncodingDetails
import anystream.models.api.ImportMedia
import anystream.models.api.ImportMediaResult
import anystream.models.api.ImportStreamDetailsResult
import anystream.util.concurrentMap
import com.github.kokorin.jaffree.JaffreeException
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffprobe.FFprobe
import com.github.kokorin.jaffree.ffprobe.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import org.jdbi.v3.core.JdbiException
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import java.io.File
import java.util.*

private val FFMPEG_EXTENSIONS = listOf(
    "webm", "mpg", "mp2", "mpeg", "mov", "mkv",
    "avi", "m4a", "m4p", "mp4", "ogg",
    //
    "3gp", "aac", "flac", "ogg", "mp3", "opus",
    "wav",
)

class MediaImporter(
    private val ffprobe: () -> FFprobe,
    private val processors: List<MediaImportProcessor>,
    private val mediaRefsDao: MediaReferencesDao,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val classMarker = MarkerFactory.getMarker(this::class.simpleName)

    // Within a specified content directory, find all content unknown to anystream
    fun findUnmappedFiles(request: ImportMedia): List<String> {
        val contentFile = File(request.contentPath)
        if (!contentFile.exists()) {
            return emptyList()
        }

        val mediaRefPaths = mediaRefsDao.findAllFilePaths()
        return contentFile.listFiles()
            .orEmpty()
            .map(File::getAbsolutePath)
            .filter { filePath ->
                mediaRefPaths.none { ref ->
                    ref.startsWith(filePath)
                }
            }
    }

    suspend fun importAll(userId: Int, request: ImportMedia): Flow<ImportMediaResult> {
        val marker = marker()
        logger.debug(marker, "Recursive import requested by '$userId': $request")
        val contentFile = File(request.contentPath)
        if (!contentFile.exists()) {
            logger.debug(marker, "Root content directory not found: ${contentFile.absolutePath}")
            return flowOf(ImportMediaResult.ErrorFileNotFound)
        }

        return contentFile.listFiles()
            .orEmpty()
            .asFlow()
            .concurrentMap(scope, 3) { file ->
                internalImport(
                    userId,
                    request.copy(contentPath = file.absolutePath),
                    marker,
                )
            }
            .onCompletion { error ->
                if (error == null) {
                    logger.debug(marker, "Recursive import completed")
                } else {
                    logger.debug(marker, "Recursive import interrupted", error)
                }
            }
    }

    suspend fun import(userId: Int, request: ImportMedia): ImportMediaResult {
        val marker = marker()
        logger.debug(marker, "Import requested by '$userId': $request")

        val contentFile = File(request.contentPath)
        if (!contentFile.exists()) {
            logger.debug(marker, "Content file not found: ${contentFile.absolutePath}")
            return ImportMediaResult.ErrorFileNotFound
        }

        return internalImport(userId, request, marker)
    }

    suspend fun importStreamDetails(mediaRefIds: List<String>): List<ImportStreamDetailsResult> {
        val marker = marker()
        logger.debug(marker, "Importing stream details for ${mediaRefIds.size} item(s)")

        val mediaRefs = mediaRefsDao.findByGids(mediaRefIds)
        logger.debug(marker, "Removed ${mediaRefIds.size - mediaRefs.size} invalid item(s)")

        val results = mediaRefs
            .filter { mediaRef ->
                val extension = mediaRef.filePath?.substringAfterLast('.', "")?.lowercase()
                !extension.isNullOrBlank() && FFMPEG_EXTENSIONS.contains(extension)
            }.associateWith { mediaRef ->
                processMediaFileStreams(mediaRef.gid, mediaRef.filePath.orEmpty(), marker)
            }

        logger.debug(marker, "Processed ${results.size} item(s)")

        if (results.isEmpty()) {
            return listOf(ImportStreamDetailsResult.ErrorNothingToImport)
        }

        val successResults = results.filterValues { it is ImportStreamDetailsResult.Success }
        if (successResults.isEmpty()) {
            return results.values.toList()
        }

        return try {
            successResults.forEach { (mediaRef, result) ->
                (result as ImportStreamDetailsResult.Success).streams
                    .map { stream -> mediaRefsDao.insertStreamDetails(StreamEncodingDetailsDb.fromModel(stream)) }
                    .map { streamId -> mediaRefsDao.insertStreamLink(mediaRef.id, streamId) }
            }
            results.values.toList()
        } catch (e: JdbiException) {
            logger.error(marker, "Failed to update stream data", e)
            listOf(ImportStreamDetailsResult.ErrorDatabaseException(e.stackTraceToString()))
        }
    }

    private suspend fun processMediaFileStreams(
        mediaRefId: String,
        filePath: String,
        marker: Marker
    ): ImportStreamDetailsResult {
        return if (File(filePath).exists()) {
            try {
                val streams = awaitAll(
                    ffprobe().processStreamsAsync(filePath, StreamType.VIDEO_NOT_PICTURE),
                    ffprobe().processStreamsAsync(filePath, StreamType.AUDIO),
                    ffprobe().processStreamsAsync(filePath, StreamType.SUBTITLE),
                ).flatten()
                ImportStreamDetailsResult.Success(mediaRefId, streams)
            } catch (e: JaffreeException) {
                logger.error(marker, "FFProbe error, failed to extract stream details", e)
                ImportStreamDetailsResult.ProcessError(e.stackTraceToString())
            }
        } else {
            logger.error(marker, "Media file reference path does not exist: $mediaRefId $filePath")
            ImportStreamDetailsResult.ErrorFileNotFound
        }
    }

    // Process a single media file and attempt to import missing data and references
    private suspend fun internalImport(
        userId: Int,
        request: ImportMedia,
        marker: Marker,
    ): ImportMediaResult {
        val contentFile = File(request.contentPath)
        logger.debug(marker, "Importing content file: ${contentFile.absolutePath}")
        if (!contentFile.exists()) {
            logger.debug(marker, "Content file not found")
            return ImportMediaResult.ErrorFileNotFound
        }

        val result = processors.firstNotNullOfOrNull { processor ->
            if (processor.mediaKinds.contains(request.mediaKind)) {
                processor.process(contentFile, userId, marker)
            } else null
        } ?: ImportMediaResult.ErrorNothingToImport

        return result
    }

    // Create a unique nested marker to identify import requests
    private fun marker() = MarkerFactory.getMarker(UUID.randomUUID().toString())
        .apply { add(classMarker) }

    private fun FFprobe.processStreamsAsync(
        filePath: String,
        streamType: StreamType,
    ): Deferred<List<StreamEncodingDetails>> {
        return scope.async {
            setShowStreams(true)
            setShowFormat(true)
            setSelectStreams(streamType)
            setShowEntries("stream=index:stream_tags=language,LANGUAGE,title")
            setInput(filePath)
            execute().streams.mapNotNull { it.toStreamEncodingDetails() }
        }
    }

    private fun Stream.toStreamEncodingDetails(): StreamEncodingDetails? {
        val title = getTag("title")
        val language = getTag("language") ?: getTag("LANGUAGE")
        return when (codecType) {
            StreamType.VIDEO,
            StreamType.VIDEO_NOT_PICTURE -> StreamEncodingDetails.Video(
                id = -1,
                index = index,
                codecName = codecName,
                profile = profile,
                bitRate = bitRate,
                level = level,
                height = height,
                width = width,
                language = language,
                title = title,
            )
            StreamType.AUDIO -> StreamEncodingDetails.Audio(
                id = -1,
                index = index,
                codecName = codecName,
                profile = profile,
                bitRate = bitRate,
                channels = channels,
                language = language,
                title = title,
            )
            StreamType.SUBTITLE -> StreamEncodingDetails.Subtitle(
                id = -1,
                index = index,
                codecName = codecName,
                language = language,
                title = title,
            )
            StreamType.DATA,
            StreamType.ATTACHMENT,
            null -> null
        }
    }
}
