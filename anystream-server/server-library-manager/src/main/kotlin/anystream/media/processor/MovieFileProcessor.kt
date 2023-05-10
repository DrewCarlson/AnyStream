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

import anystream.db.MediaLinkDao
import anystream.db.model.MediaLinkDb
import anystream.media.VIDEO_EXTENSIONS
import anystream.media.file.FileNameParser
import anystream.media.file.MovieFileNameParser
import anystream.media.file.ParsedFileNameResult
import anystream.metadata.MetadataManager
import anystream.models.MediaKind
import anystream.models.MediaLink
import anystream.models.Movie
import anystream.models.api.*
import org.slf4j.LoggerFactory
import java.io.File

class MovieFileProcessor(
    private val metadataManager: MetadataManager,
    private val mediaLinkDao: MediaLinkDao,
) : MediaFileProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE)
    override val fileNameParser: FileNameParser = MovieFileNameParser()

    override suspend fun findMetadataMatches(mediaLink: MediaLinkDb, import: Boolean): MediaLinkMatchResult {
        return when (mediaLink.descriptor) {
            MediaLink.Descriptor.ROOT_DIRECTORY -> findMatchesForRootDir(mediaLink, import)
            MediaLink.Descriptor.MEDIA_DIRECTORY,
            MediaLink.Descriptor.CHILD_DIRECTORY,
            -> findMatchesForMediaDir(mediaLink, import)

            MediaLink.Descriptor.VIDEO,
            -> findMatchesForFile(mediaLink, import)

            MediaLink.Descriptor.AUDIO,
            MediaLink.Descriptor.SUBTITLE,
            MediaLink.Descriptor.IMAGE,
            -> MediaLinkMatchResult.NoSupportedFiles(mediaLink.toModel())
        }
    }

    override suspend fun importMetadataMatch(mediaLink: MediaLinkDb, metadataMatch: MetadataMatch) {
        val movie = (metadataMatch as? MetadataMatch.MovieMatch)
            ?.let { getOrImportMetadata(it) }
            ?: return
        val parentGid = mediaLink.parentMediaLinkGid
        if (!parentGid.isNullOrBlank()) {
            val parentDescriptor = mediaLinkDao.descriptorForGid(parentGid)
            if (parentDescriptor == MediaLink.Descriptor.MEDIA_DIRECTORY) {
                mediaLinkDao.updateMetadataIds(parentGid, movie.id, movie.gid)
            }
        }

        if (mediaLink.descriptor == MediaLink.Descriptor.MEDIA_DIRECTORY) {
            val childLinks = mediaLinkDao.findByParentId(requireNotNull(mediaLink.id))
            childLinks.forEach { link ->
                mediaLinkDao.updateMetadataIds(checkNotNull(link.id), movie.id, movie.gid)
            }
        }

        mediaLinkDao.updateMetadataIds(checkNotNull(mediaLink.id), movie.id, movie.gid)
        // TODO: Update supplementary files (SUBTITLE/IMAGE)
    }

    private suspend fun findMatchesForRootDir(mediaLink: MediaLinkDb, import: Boolean): MediaLinkMatchResult {
        val childLinks = mediaLinkDao.findByParentId(requireNotNull(mediaLink.id))
        val subResults = childLinks.mapNotNull { childLink ->
            when (childLink.descriptor) {
                MediaLink.Descriptor.MEDIA_DIRECTORY -> findMatchesForMediaDir(childLink, import)
                MediaLink.Descriptor.VIDEO -> findMatchesForFile(childLink, import)
                MediaLink.Descriptor.ROOT_DIRECTORY -> error("ROOT_DIRECTORY links must not have a parent")
                MediaLink.Descriptor.CHILD_DIRECTORY -> error("CHILD_DIRECTORY links must have a MEDIA_DIRECTORY parent")
                MediaLink.Descriptor.AUDIO -> error("AUDIO links are not supported in movie libraries")
                MediaLink.Descriptor.SUBTITLE,
                MediaLink.Descriptor.IMAGE,
                -> {
                    // Ignored, supplementary files will be handled by the VIDEO file matching process.
                    null
                }
            }
        }
        return MediaLinkMatchResult.Success(
            mediaLink = mediaLink.toModel(),
            matches = emptyList(),
            subResults = subResults,
        )
    }

    private suspend fun findMatchesForMediaDir(mediaLink: MediaLinkDb, import: Boolean): MediaLinkMatchResult {
        val path = requireNotNull(mediaLink.filePath)
        val childLinks = mediaLinkDao.findByBasePathAndDescriptor(path, MediaLink.Descriptor.VIDEO)
        val movieFile = childLinks.firstOrNull() ?: return MediaLinkMatchResult.NoSupportedFiles(mediaLink.toModel())

        return when (val subMatch = findMatchesForFile(movieFile, import)) {
            is MediaLinkMatchResult.FileNameParseFailed ->
                MediaLinkMatchResult.FileNameParseFailed(mediaLink.toModel())

            is MediaLinkMatchResult.NoMatchesFound ->
                MediaLinkMatchResult.NoMatchesFound(mediaLink.toModel())

            is MediaLinkMatchResult.NoSupportedFiles ->
                MediaLinkMatchResult.NoSupportedFiles(mediaLink.toModel())

            is MediaLinkMatchResult.Success -> {
                MediaLinkMatchResult.Success(
                    mediaLink.toModel(),
                    matches = subMatch.matches,
                    subResults = listOf(subMatch),
                )
            }
        }
    }

    private suspend fun findMatchesForFile(mediaLink: MediaLinkDb, import: Boolean): MediaLinkMatchResult {
        val movieFile = File(requireNotNull(mediaLink.filePath))
        if (!VIDEO_EXTENSIONS.contains(movieFile.extension)) {
            return MediaLinkMatchResult.NoSupportedFiles(mediaLink.toModel())
        }
        val mediaName = movieFile.nameWithoutExtension
        val (movieName, year) = when (val result = fileNameParser.parseFileName(mediaName)) {
            is ParsedFileNameResult.MovieFile -> result
            else -> {
                logger.debug("Expected to find movie file but could not parse '{}'", mediaName)
                return MediaLinkMatchResult.FileNameParseFailed(mediaLink.toModel())
            }
        }
        logger.debug("Querying provider for '{}' (year {})", movieName, year)
        val query = QueryMetadata(
            providerId = null,
            query = movieName,
            mediaKind = MediaKind.MOVIE,
            year = year,
            extras = null,
        )
        val results = metadataManager.search(query)
        val matches = results
            .filterIsInstance<QueryMetadataResult.Success>()
            .flatMap { it.results }
            .filterIsInstance<MetadataMatch.MovieMatch>()
        if (matches.isEmpty()) {
            logger.debug("No metadata match results for '{}'", query)
            return MediaLinkMatchResult.NoMatchesFound(mediaLink.toModel())
        }
        val metadataMatch = matches.sortedBy { scoreString(movieName, it.movie.title) }

        if (import && metadataMatch.isNotEmpty()) {
            importMetadataMatch(mediaLink, metadataMatch.first())
        }

        return MediaLinkMatchResult.Success(
            mediaLink = mediaLink.toModel(),
            matches = metadataMatch,
            subResults = emptyList(),
        )
    }

    private suspend fun getOrImportMetadata(
        metadataMatch: MetadataMatch.MovieMatch,
    ): Movie? {
        return if (metadataMatch.exists) {
            metadataMatch.movie.also {
                logger.debug("Matched existing metadata for '{}'", it.title)
            }
        } else {
            val movie = metadataMatch.movie
            logger.debug("Importing new metadata for '{}'", movie.title)
            val importResults = metadataManager.importMetadata(
                ImportMetadata(
                    metadataIds = listOfNotNull(metadataMatch.metadataGid),
                    providerId = metadataMatch.providerId,
                    mediaKind = MediaKind.MOVIE,
                ),
            ).filterIsInstance<ImportMetadataResult.Success>()

            if (importResults.isEmpty()) {
                logger.error("No import results for match {}", metadataMatch.movie)
                return null // MediaScanResult.ErrorNothingToScan
            } else {
                (importResults.first().match as MetadataMatch.MovieMatch).movie
            }
        }
    }
}
