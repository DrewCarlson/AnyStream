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

import anystream.data.MetadataDbQueries
import anystream.db.model.MediaLinkDb
import anystream.media.MediaFileProcessor
import anystream.metadata.MetadataManager
import anystream.models.MediaKind
import anystream.models.api.*
import org.jdbi.v3.core.JdbiException
import org.slf4j.LoggerFactory
import java.io.File

class MovieFileProcessor(
    private val metadataManager: MetadataManager,
    private val queries: MetadataDbQueries,
) : MediaFileProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val yearRegex = "\\s\\((\\d{4})\\)\$".toRegex()

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE)

    override suspend fun matchMediaLinkMetadata(mediaLink: MediaLinkDb, userId: Int) {
        val contentFile = File(requireNotNull(mediaLink.filePath))
        val movieFile = if (contentFile.isFile) {
            logger.debug("Detected single content file")
            contentFile
        } else {
            logger.debug("Detected content directory")
            contentFile.listFiles()
                ?.toList()
                .orEmpty()
                .maxByOrNull(File::length)
                ?.also { result ->
                    logger.debug("Largest content file found: {}", result.absolutePath)
                }
        }

        if (movieFile == null) {
            logger.debug("Content file not found")
            return // MediaScanResult.ErrorMediaScanLinkNotFound
        }
        val mediaName = if (movieFile.isDirectory) {
            contentFile.name
        } else {
            movieFile.nameWithoutExtension
        }

        val existingRef = try {
            queries.findMediaRefByFilePath(movieFile.absolutePath)
        } catch (e: JdbiException) {
            return // MediaScanResult.ErrorDatabaseException(e.stackTraceToString())
        }
        if (existingRef != null) {
            logger.debug("Content file reference already exists")
            // return // MediaScanResult.ErrorNothingToScan
        }

        val match = yearRegex.find(mediaName)
        val year = match?.groupValues?.lastOrNull()?.toIntOrNull() ?: 0

        logger.debug("Found content year: {}", year)

        // TODO: Improve query capabilities
        val query = mediaName.replace(yearRegex, "").trim()

        logger.debug("Querying provider for '{}'", query)
        val results = metadataManager.search(
            QueryMetadata(
                providerId = null,
                query = query,
                mediaKind = MediaKind.MOVIE,
                year = year,
                extras = null,
            ),
        )
        val result = results.firstOrNull { result ->
            result is QueryMetadataResult.Success && result.results.isNotEmpty()
        }

        val movie = when (result) {
            is QueryMetadataResult.Success -> {
                logger.debug("Query successful with {} results", results.size)
                val metadataMatch = result.results
                    .filterIsInstance<MetadataMatch.MovieMatch>()
                    .maxByOrNull { it.movie.title.equals(query, true) }
                    ?: result.results.first()

                if (metadataMatch.exists) {
                    (metadataMatch as MetadataMatch.MovieMatch).movie.also {
                        logger.debug("Matched existing metadata for '{}'", it.title)
                    }
                } else {
                    val movie = (metadataMatch as MetadataMatch.MovieMatch).movie
                    logger.debug("Importing new metadata for '{}'", movie.title)
                    val importResults = metadataManager.importMetadata(
                        ImportMetadata(
                            metadataIds = listOf(metadataMatch.metadataGid),
                            providerId = result.providerId,
                            mediaKind = MediaKind.MOVIE,
                        ),
                    ).filterIsInstance<ImportMetadataResult.Success>()

                    if (importResults.isEmpty()) {
                        logger.debug("Provider lookup error: {}", results)
                        return // MediaScanResult.ErrorNothingToScan
                    } else {
                        (importResults.first().match as MetadataMatch.MovieMatch).movie
                    }
                }
            }
            else -> {
                logger.debug("Provider lookup error: {}", results)
                return // MediaScanResult.ErrorNothingToScan
            }
        }

        queries.mediaLinkDao.updateMetadataIds(checkNotNull(mediaLink.id), movie.id, movie.gid)
        queries.mediaLinkDao.updateMetadataIds(checkNotNull(existingRef).id, movie.id, movie.gid)

        return // MediaScanResult.ErrorDatabaseException("")
    }
}
