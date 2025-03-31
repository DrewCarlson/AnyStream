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

import anystream.db.LibraryDao
import anystream.db.MediaLinkDao
import anystream.media.VIDEO_EXTENSIONS
import anystream.media.file.FileNameParser
import anystream.media.file.MovieFileNameParser
import anystream.media.file.ParsedFileNameResult
import anystream.metadata.MetadataManager
import anystream.models.*
import anystream.models.api.*
import org.slf4j.LoggerFactory
import kotlin.io.path.*

class MovieFileProcessor(
    private val metadataManager: MetadataManager,
    private val mediaLinkDao: MediaLinkDao,
    private val libraryDao: LibraryDao,
) : MediaFileProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE)
    override val fileNameParser: FileNameParser = MovieFileNameParser()

    override suspend fun findMetadataMatches(directory: Directory, import: Boolean): List<MediaLinkMatchResult> {
        val library = libraryDao.fetchLibraryForDirectory(directory.id)
            ?: return emptyList()  // TODO: return no library error
        val contentRootDirectories = when {
            directory.id == library.id -> libraryDao.fetchChildDirectories(directory.id)
            directory.parentId == library.id -> listOf(directory)
            else -> libraryDao.fetchDirectory(directory.id)
                ?.takeIf {
                    // TODO: provide feedback regarding nested content folders being unsupported
                    it.parentId == library.id
                }
                ?.run(::listOfNotNull)
                ?: return emptyList() // TODO: return no directory error
        }

        return contentRootDirectories.map { dir ->
            findMatchesForMediaDir(dir, import = import)
        }
    }

    override suspend fun findMetadataMatches(mediaLink: MediaLink, import: Boolean): MediaLinkMatchResult {
        return when (mediaLink.descriptor) {
            Descriptor.VIDEO,
            -> findMatchesForFile(mediaLink, import)

            Descriptor.AUDIO,
            Descriptor.SUBTITLE,
            Descriptor.IMAGE,
            -> MediaLinkMatchResult.NoSupportedFiles(mediaLink, null)
        }
    }

    override suspend fun importMetadataMatch(mediaLink: MediaLink, metadataMatch: MetadataMatch) {
        val movie = (metadataMatch as? MetadataMatch.MovieMatch)
            ?.let { getOrImportMetadata(it) }
            ?: return

        mediaLinkDao.updateMetadataIds(
            mediaLinkId = checkNotNull(mediaLink.id),
            metadataId = movie.id
        )

        // TODO: Update supplementary files (SUBTITLE/IMAGE)
    }

    override suspend fun findMetadata(mediaLink: MediaLink, remoteId: String): MetadataMatch? {
        return when (val result =  metadataManager.findByRemoteId(remoteId)) {
            is QueryMetadataResult.Success -> result.results.firstOrNull()
            is QueryMetadataResult.ErrorDataProviderException,
            is QueryMetadataResult.ErrorDatabaseException,
            QueryMetadataResult.ErrorProviderNotFound -> null
        }
    }

    private suspend fun findMatchesForRootDir(mediaLink: MediaLink, import: Boolean): MediaLinkMatchResult {
        val childLinks = mediaLinkDao.findByParentId(requireNotNull(mediaLink.id))
        val subResults = childLinks.mapNotNull { childLink ->
            when (childLink.descriptor) {
                Descriptor.VIDEO -> findMatchesForFile(childLink, import)
                Descriptor.AUDIO -> error("AUDIO links are not supported in movie libraries")
                Descriptor.SUBTITLE,
                Descriptor.IMAGE,
                -> {
                    // Ignored, supplementary files will be handled by the VIDEO file matching process.
                    null
                }
            }
        }
        return MediaLinkMatchResult.Success(
            mediaLink = mediaLink,
            directory = null,
            matches = emptyList(),
            subResults = subResults,
        )
    }

    private suspend fun findMatchesForMediaDir(directory: Directory, import: Boolean): MediaLinkMatchResult {
        val path = requireNotNull(directory.filePath)
        val childLinks = mediaLinkDao.findByBasePathAndDescriptor(path, Descriptor.VIDEO)
        val movieFile = childLinks.firstOrNull()
            ?: return MediaLinkMatchResult.NoSupportedFiles(
                mediaLink = null,
                directory = directory,
            )

        return when (val subMatch = findMatchesForFile(movieFile, import)) {
            is MediaLinkMatchResult.FileNameParseFailed ->
                MediaLinkMatchResult.FileNameParseFailed(
                    mediaLink = null,
                    directory = directory,
                )

            is MediaLinkMatchResult.NoMatchesFound ->
                MediaLinkMatchResult.NoMatchesFound(
                    mediaLink = null,
                    directory = directory,
                )

            is MediaLinkMatchResult.NoSupportedFiles ->
                MediaLinkMatchResult.NoSupportedFiles(
                    mediaLink = null,
                    directory = directory,
                )

            is MediaLinkMatchResult.Success -> {
                MediaLinkMatchResult.Success(
                    mediaLink = null,
                    directory = directory,
                    matches = subMatch.matches,
                    subResults = listOf(subMatch),
                )
            }
        }
    }

    private suspend fun findMatchesForFile(mediaLink: MediaLink, import: Boolean): MediaLinkMatchResult {
        val movieFile = Path(requireNotNull(mediaLink.filePath))
        if (!VIDEO_EXTENSIONS.contains(movieFile.extension)) {
            return MediaLinkMatchResult.NoSupportedFiles(mediaLink, null)
        }
        val (movieName, year) = when (val result = fileNameParser.parseFileName(movieFile)) {
            is ParsedFileNameResult.MovieFile -> result
            else -> {
                logger.debug("Expected to find movie file but could not parse '{}'", movieFile)
                return MediaLinkMatchResult.FileNameParseFailed(mediaLink, null)
            }
        }
        logger.debug("Querying provider for '{}' (year {})", movieName, year)

        val results = metadataManager.search(MediaKind.MOVIE) {
            this.query = movieName
            this.year = year
        }
        val matches = results
            .filterIsInstance<QueryMetadataResult.Success>()
            .flatMap { it.results }
            .filterIsInstance<MetadataMatch.MovieMatch>()
        if (matches.isEmpty()) {
            logger.debug("No metadata match results")
            return MediaLinkMatchResult.NoMatchesFound(mediaLink, null)
        }
        val metadataMatch = matches.sortedBy { scoreString(movieName, it.movie.title) }

        if (import && metadataMatch.isNotEmpty()) {
            importMetadataMatch(mediaLink, metadataMatch.first())
        }

        return MediaLinkMatchResult.Success(
            mediaLink = mediaLink,
            directory = null,
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
                    metadataIds = listOfNotNull(metadataMatch.remoteMetadataId),
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
