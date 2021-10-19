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
package anystream.media

import anystream.models.LocalMediaReference
import anystream.models.MediaReference
import anystream.models.StreamEncodingDetails
import anystream.models.api.ImportMedia
import anystream.models.api.ImportMediaResult
import anystream.models.api.ImportStreamDetailsResult
import anystream.util.concurrentMap
import com.github.kokorin.jaffree.JaffreeException
import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffprobe.FFprobe
import com.github.kokorin.jaffree.ffprobe.Stream
import com.mongodb.MongoException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.projection
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import java.io.File
import java.util.UUID

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
    private val mediaRefs: CoroutineCollection<MediaReference>,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val classMarker = MarkerFactory.getMarker(this::class.simpleName)

    // Within a specified content directory, find all content unknown to anystream
    suspend fun findUnmappedFiles(userId: String, request: ImportMedia): List<String> {
        val contentFile = File(request.contentPath)
        if (!contentFile.exists()) {
            return emptyList()
        }

        val mediaRefPaths = mediaRefs
            .withDocumentClass<LocalMediaReference>()
            .projection(
                LocalMediaReference::filePath,
                LocalMediaReference::filePath.exists()
            )
            .toList()

        return contentFile.listFiles()
            ?.toList()
            .orEmpty()
            .filter { file ->
                mediaRefPaths.none { ref ->
                    ref.startsWith(file.absolutePath)
                }
            }
            .map(File::getAbsolutePath)
    }

    suspend fun importAll(userId: String, request: ImportMedia): Flow<ImportMediaResult> {
        val marker = marker()
        logger.debug(marker, "Recursive import requested by '$userId': $request")
        val contentFile = File(request.contentPath)
        if (!contentFile.exists()) {
            logger.debug(marker, "Root content directory not found: ${contentFile.absolutePath}")
            return flowOf(ImportMediaResult.ErrorFileNotFound)
        }

        return contentFile.listFiles()
            ?.asFlow()
            ?.concurrentMap(scope, 10) { file ->
                internalImport(
                    userId,
                    request.copy(contentPath = file.absolutePath),
                    marker,
                )
            }
            ?.onCompletion { error ->
                if (error == null) {
                    logger.debug(marker, "Recursive import completed")
                } else {
                    logger.debug(marker, "Recursive import interrupted", error)
                }
            } ?: emptyFlow()
    }

    suspend fun import(userId: String, request: ImportMedia): ImportMediaResult {
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
        val mediaFilePaths = mediaRefs
            .projection(
                LocalMediaReference::id,
                LocalMediaReference::filePath,
                and(
                    MediaReference::id `in` mediaRefIds,
                    LocalMediaReference::directory eq false,
                )
            )
            .toList()
            .toMap()
        logger.debug(marker, "Removed ${mediaRefIds.size - mediaFilePaths.size} invalid item(s)")

        val results = mediaFilePaths
            .filterValues { path ->
                val extension = path?.substringAfterLast('.')?.lowercase().orEmpty()
                FFMPEG_EXTENSIONS.contains(extension)
            }
            .map { (refId, filePath) ->
                checkNotNull(refId)
                checkNotNull(filePath)
                if (File(filePath).exists()) {
                    try {
                        val streams = awaitAll(
                            ffprobe().processStreamsAsync(filePath, StreamType.VIDEO),
                            ffprobe().processStreamsAsync(filePath, StreamType.AUDIO),
                            ffprobe().processStreamsAsync(filePath, StreamType.SUBTITLE),
                        ).flatten()
                        ImportStreamDetailsResult.Success(refId, streams)
                    } catch (e: JaffreeException) {
                        logger.error(marker, "FFProbe error, failed to extract stream details", e)
                        ImportStreamDetailsResult.ProcessError(e.stackTraceToString())
                    }
                } else {
                    logger.error(marker, "Media file reference path does not exist: $refId $filePath")
                    ImportStreamDetailsResult.ErrorFileNotFound
                }
            }

        logger.debug(marker, "Processed ${results.size} item(s)")

        if (results.isEmpty()) {
            return listOf(ImportStreamDetailsResult.ErrorNothingToImport)
        }

        val successResults = results.filterIsInstance<ImportStreamDetailsResult.Success>()
        if (successResults.isEmpty()) {
            return results
        }

        val updates = successResults
            .map { (refId, streams) ->
                updateOne<MediaReference>(
                    MediaReference::id eq refId,
                    setValue(MediaReference::streams, streams)
                )
            }
        return try {
            mediaRefs.bulkWrite(updates)
            results
        } catch (e: MongoException) {
            logger.error(marker, "Failed to update stream data", e)
            listOf(ImportStreamDetailsResult.ErrorDatabaseException(e.stackTraceToString()))
        }
    }

    // Process a single media file and attempt to import missing data and references
    private suspend fun internalImport(
        userId: String,
        request: ImportMedia,
        marker: Marker,
    ): ImportMediaResult {
        val contentFile = File(request.contentPath)
        logger.debug(marker, "Importing content file: ${contentFile.absolutePath}")
        if (!contentFile.exists()) {
            logger.debug(marker, "Content file not found")
            return ImportMediaResult.ErrorFileNotFound
        }

        val result = processors
            .mapNotNull { processor ->
                if (processor.mediaKinds.contains(request.mediaKind)) {
                    processor.process(contentFile, userId, marker)
                } else null
            }
            .firstOrNull()
            ?: ImportMediaResult.ErrorNothingToImport

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
                .setShowFormat(true)
                .setSelectStreams(streamType)
                .setShowEntries("stream=index:stream_tags=language,title")
                .setInput(filePath)
                .execute()
                .streams
                .mapNotNull { it.toStreamEncodingDetails() }
        }
    }

    private fun Stream.toStreamEncodingDetails(): StreamEncodingDetails? {
        val language = getTag("language")
        val rawData = buildJsonObject {
            put("id", id)
            put("index", index)
            put("codecName", codecName)
            put("codecLongName", codecLongName)
            put("codecType", codecType.name)
            put("codecTag", codecTag)
            put("channels", channels)
            put("codedWidth", codedWidth)
            put("codedHeight", codedHeight)
            put("avgFrameRate", avgFrameRate?.toString())
            put("bitRate", bitRate)
            put("level", level)
            put("width", width)
            put("height", height)
            put("extradata", extradata)
            put("profile", profile)
            put("duration", duration)
            put("durationTs", durationTs)
            put("fieldOrder", fieldOrder)
            put("language", language)
        }
        val rawDataString = Json.encodeToString(rawData)
        return when (codecType) {
            StreamType.VIDEO -> StreamEncodingDetails.Video(
                index = index,
                codecName = codecName,
                profile = profile,
                bitRate = bitRate,
                level = level,
                height = height,
                width = width,
                language = language,
                rawProbeData = rawDataString,
            )
            StreamType.AUDIO -> StreamEncodingDetails.Audio(
                index = index,
                codecName = codecName,
                profile = profile,
                bitRate = bitRate,
                channels = channels,
                language = language,
                rawProbeData = rawDataString,
            )
            StreamType.SUBTITLE -> StreamEncodingDetails.Subtitle(
                index = index,
                codecName = codecName,
                language = language,
                rawProbeData = rawDataString,
            )
            StreamType.VIDEO_NOT_PICTURE,
            StreamType.DATA,
            StreamType.ATTACHMENT,
            null -> null
        }
    }
}
