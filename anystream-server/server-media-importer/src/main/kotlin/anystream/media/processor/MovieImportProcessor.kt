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
package anystream.media.processor

import anystream.data.MediaDbQueries
import anystream.media.MediaImportProcessor
import anystream.metadata.MetadataManager
import anystream.models.LocalMediaReference
import anystream.models.MediaKind
import anystream.models.api.*
import anystream.util.ObjectId
import org.jdbi.v3.core.JdbiException
import org.slf4j.Logger
import org.slf4j.Marker
import java.io.File
import java.time.Instant

class MovieImportProcessor(
    private val metadataManager: MetadataManager,
    private val queries: MediaDbQueries,
    private val logger: Logger,
) : MediaImportProcessor {

    private val yearRegex = "\\((\\d\\d\\d\\d)\\)".toRegex()

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE)

    override suspend fun process(
        contentFile: File,
        userId: Int,
        marker: Marker
    ): ImportMediaResult {
        val movieFile = if (contentFile.isFile) {
            logger.debug(marker, "Detected single content file")
            contentFile
        } else {
            logger.debug(marker, "Detected content directory")
            contentFile.listFiles()
                ?.toList()
                .orEmpty()
                .maxByOrNull(File::length)
                ?.also { result ->
                    logger.debug(marker, "Largest content file found: ${result.absolutePath}")
                }
        }

        if (movieFile == null) {
            logger.debug(marker, "Content file not found")
            return ImportMediaResult.ErrorMediaRefNotFound
        }
        val mediaName = if (movieFile.isDirectory) {
            contentFile.name
        } else {
            contentFile.nameWithoutExtension
        }

        val existingRef = try {
            queries.findMediaRefByFilePath(movieFile.absolutePath)
        } catch (e: JdbiException) {
            return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
        }
        if (existingRef != null) {
            logger.debug(marker, "Content file reference already exists")
            return ImportMediaResult.ErrorMediaRefAlreadyExists(existingRef.id)
        }

        val match = yearRegex.find(mediaName)
        val year = match?.value?.trim('(', ')')?.toInt() ?: 0

        logger.debug(marker, "Found content year: $year")

        // TODO: Improve query capabilities
        val query = mediaName.replace(yearRegex, "").trim()

        logger.debug(marker, "Querying provider for '$query'")
        val results = metadataManager.search(
            QueryMetadata(
                providerId = null,
                query = query,
                mediaKind = MediaKind.MOVIE,
                year = year,
                extras = null,
            )
        )
        val result = results.firstOrNull { result ->
            result is QueryMetadataResult.Success && result.results.isNotEmpty()
        }

        val movie = when (result) {
            is QueryMetadataResult.Success -> {
                val metadataMatch = result.results
                    .filterIsInstance<MetadataMatch.MovieMatch>()
                    .maxByOrNull { it.movie.title.equals(query, true) }
                    ?: result.results.first()

                if (metadataMatch.exists) {
                    (metadataMatch as MetadataMatch.MovieMatch).movie
                } else {
                    val importResults = metadataManager.importMetadata(
                        ImportMetadata(
                            contentIds = listOf(metadataMatch.contentId),
                            providerId = result.providerId,
                            mediaKind = MediaKind.MOVIE,
                        )
                    ).filterIsInstance<ImportMetadataResult.Success>()

                    if (importResults.isEmpty()) {
                        logger.debug(marker, "Provider lookup error: $results")
                        return ImportMediaResult.ErrorMediaMatchNotFound(
                            contentPath = contentFile.absolutePath,
                            query = query,
                            results = results,
                        )
                    } else {
                        (importResults.first().match as MetadataMatch.MovieMatch).movie
                    }
                }
            }
            else -> {
                logger.debug(marker, "Provider lookup error: $results")
                return ImportMediaResult.ErrorMediaMatchNotFound(
                    contentPath = contentFile.absolutePath,
                    query = query,
                    results = results,
                )
            }
        }

        val reference = LocalMediaReference(
            id = ObjectId.get().toString(),
            contentId = movie.id,
            added = Instant.now().toEpochMilli(),
            addedByUserId = userId,
            filePath = movieFile.absolutePath,
            mediaKind = MediaKind.MOVIE,
            directory = false,
        )
        return try {
            queries.insertMediaReference(reference)
            ImportMediaResult.Success(movie.id, reference)
        } catch (e: JdbiException) {
            logger.debug(marker, "Failed to create media reference", e)
            ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
        }
    }
}
