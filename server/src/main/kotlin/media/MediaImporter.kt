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
import anystream.models.api.ImportMedia
import anystream.models.api.ImportMediaResult
import anystream.routes.concurrentMap
import info.movito.themoviedbapi.TmdbApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.exists
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import java.io.File
import java.util.UUID

class MediaImporter(
    tmdb: TmdbApi,
    private val processors: List<MediaImportProcessor>,
    private val mediaRefs: CoroutineCollection<MediaReference>,
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
            .find(LocalMediaReference::filePath.exists())
            .toList()
            .map(LocalMediaReference::filePath)

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
            ?.toList()
            .orEmpty()
            .asFlow()
            .concurrentMap(GlobalScope, 10) { file ->
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

        return processors
            .mapNotNull { processor ->
                if (processor.mediaKinds.contains(request.mediaKind)) {
                    processor.process(contentFile, userId, marker)
                } else null
            }
            .firstOrNull()
            ?: ImportMediaResult.ErrorNothingToImport
    }

    // Create a unique nested marker to identify import requests
    private fun marker() = MarkerFactory.getMarker(UUID.randomUUID().toString())
        .apply { add(classMarker) }
}
