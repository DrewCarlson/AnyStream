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
import anystream.db.MediaLinkMetadataUpdate
import anystream.db.MetadataDao
import anystream.media.file.FileNameParser
import anystream.media.file.ParsedFileNameResult
import anystream.media.file.TvFileNameParser
import anystream.metadata.MetadataService
import anystream.models.*
import anystream.models.api.*
import anystream.util.concurrentMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import java.nio.file.FileSystem
import kotlin.io.path.absolutePathString

class TvFileProcessor(
    private val metadataService: MetadataService,
    private val mediaLinkDao: MediaLinkDao,
    private val libraryDao: LibraryDao,
    private val fs: FileSystem,
) : MediaFileProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.TV)
    override val fileNameParser: FileNameParser = TvFileNameParser()

    override suspend fun findMetadataMatches(directory: Directory, import: Boolean): List<MediaLinkMatchResult> {
        val libraryRootIds = libraryDao.fetchLibraryRootDirectories(directory.libraryId)
            .map(Directory::id)
            .takeIf { it.isNotEmpty() }

        if (libraryRootIds == null) {
            logger.error("No library roots found for library {}", directory.libraryId)
            return listOf(MediaLinkMatchResult.LibraryNotFound(directory.id, directory.libraryId))
        }

        val contentRootDirectories = when {
            // directory is a library root, scan all direct children
            libraryRootIds.contains(directory.id) -> libraryDao.fetchChildDirectories(directory.id)
            // directory is child of library root (show folder), scan it
            libraryRootIds.contains(directory.parentId) -> listOf(directory)
            else -> {
                // Nested directory (e.g., season folder) - walk up to find the show folder
                val showFolder = findContentRootDirectory(directory, libraryRootIds)
                if (showFolder == null) {
                    logger.error("Could not find show folder for nested directory {}", directory.filePath)
                    return listOf(MediaLinkMatchResult.NoSupportedFiles(null, directory))
                }
                logger.debug("Found show folder {} for nested directory {}", showFolder.filePath, directory.filePath)
                listOf(showFolder)
            }
        }

        return coroutineScope {
            contentRootDirectories.asFlow()
                .concurrentMap(this, concurrencyLevel = 10) { dir ->
                    findMatchesForMediaDir(dir, import = import)
                }
                .toList()
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

    private suspend fun findMatchesForMediaDir(directory: Directory, import: Boolean): MediaLinkMatchResult {
        val episodeLinks = mediaLinkDao.findByBasePathAndDescriptor(directory.filePath, Descriptor.VIDEO)
        if (episodeLinks.isEmpty()) {
            return MediaLinkMatchResult.NoSupportedFiles(null, directory)
        }

        val (tvShowName, year) = when (val result = fileNameParser.parseFileName(fs.getPath(directory.filePath))) {
            is ParsedFileNameResult.Tv.ShowFolder -> result
            else -> {
                logger.debug("Expected to find show folder but could not parse '{}' {}", directory.filePath, result)
                return MediaLinkMatchResult.FileNameParseFailed(null, directory)
            }
        }
        val results = metadataService.search(MediaKind.TV) {
            this.query = tvShowName
            this.year = year
            firstResultOnly = import
        }
        val matches = results
            .filterIsInstance<QueryMetadataResult.Success>()
            .flatMap { it.results }
            .filterIsInstance<MetadataMatch.TvShowMatch>()
        if (matches.isEmpty()) {
            logger.debug("No metadata match results '{}' (year {})", tvShowName, year)
            return MediaLinkMatchResult.NoMatchesFound(null, directory)
        }

        logger.debug("Found {} Metadata match results '{}' (year {})", matches.size, tvShowName, year)

        val matchResults = if (import) {
            // When importing, do not include unused metadata matches
            val importedMatch = importMetadataMatch(directory, matches.first())
            if (importedMatch == null) {
                logger.error("Failed to import metadata for directory '{}' with match '{}'", directory.filePath, matches.first())
                return MediaLinkMatchResult.ImportFailed(null, directory, "Metadata import failed for ${matches.first().tvShow.name}")
            }
            listOf(importedMatch)
        } else {
            matches
        }

        return MediaLinkMatchResult.Success(
            mediaLink = null,
            directory = directory,
            matches = matchResults,
            subResults = emptyList(),
        )
    }

    private suspend fun findMatchesForFile(
        mediaLink: MediaLink,
        import: Boolean,
    ): MediaLinkMatchResult {
        val filePath = requireNotNull(mediaLink.filePath)
        val file = fs.getPath(filePath)

        // Parse the episode filename to get season/episode numbers
        val episodeParseResult = when (val result = fileNameParser.parseFileName(file)) {
            is ParsedFileNameResult.Tv.EpisodeFile -> result
            else -> {
                logger.debug("Could not parse episode file '{}' - got {}", filePath, result)
                return MediaLinkMatchResult.FileNameParseFailed(mediaLink, null)
            }
        }

        // Walk up to find the show folder by looking at directory hierarchy
        val seasonDirectory = libraryDao.fetchDirectory(mediaLink.directoryId)
            ?: return MediaLinkMatchResult.NoSupportedFiles(mediaLink, null)

        // The season folder's parent should be the show folder
        val showDirectoryId = seasonDirectory.parentId
            ?: return MediaLinkMatchResult.NoSupportedFiles(mediaLink, null)
        val showDirectory = libraryDao.fetchDirectory(showDirectoryId)
            ?: return MediaLinkMatchResult.NoSupportedFiles(mediaLink, null)

        // Parse the show folder name
        val showPath = fs.getPath(showDirectory.filePath)
        val (showName, year) = when (val result = fileNameParser.parseFileName(showPath)) {
            is ParsedFileNameResult.Tv.ShowFolder -> result
            else -> {
                logger.debug("Could not parse show folder '{}' - got {}", showDirectory.filePath, result)
                return MediaLinkMatchResult.FileNameParseFailed(mediaLink, null)
            }
        }

        logger.debug("Matching episode file '{}' from show '{}' (year {}), S{}E{}",
            filePath, showName, year, episodeParseResult.seasonNumber, episodeParseResult.episodeNumber)

        // Search for the show
        val results = metadataService.search(MediaKind.TV) {
            this.query = showName
            this.year = year
            firstResultOnly = import
        }
        val matches = results
            .filterIsInstance<QueryMetadataResult.Success>()
            .flatMap { it.results }
            .filterIsInstance<MetadataMatch.TvShowMatch>()

        if (matches.isEmpty()) {
            logger.debug("No metadata match results for show '{}' (year {})", showName, year)
            return MediaLinkMatchResult.NoMatchesFound(mediaLink, null)
        }

        if (!import) {
            // Just return the matches without importing
            return MediaLinkMatchResult.Success(
                mediaLink = mediaLink,
                directory = null,
                matches = matches,
                subResults = emptyList(),
            )
        }

        // Import the show and link the specific episode
        val match = matches.first()
        val importedMatch = getOrImportMetadata(match)
        if (importedMatch == null) {
            logger.error("Failed to import metadata for show '{}' while matching episode '{}'", showName, filePath)
            return MediaLinkMatchResult.ImportFailed(mediaLink, null, "Failed to import show metadata for $showName")
        }

        // Find the episode metadata
        val seasonNumber = episodeParseResult.seasonNumber
        val episodeNumber = episodeParseResult.episodeNumber
        val episodeMetadata = importedMatch.episodes.find {
            it.seasonNumber == seasonNumber && it.number == episodeNumber
        }

        if (episodeMetadata == null) {
            logger.warn("No episode S{}E{} found in show '{}' for file '{}'",
                seasonNumber, episodeNumber, showName, filePath)
            return MediaLinkMatchResult.NoMatchesFound(mediaLink, null)
        }

        // Link the file to the episode metadata
        mediaLinkDao.updateMetadataIds(
            MediaLinkMetadataUpdate(
                mediaLinkId = checkNotNull(mediaLink.id),
                metadataId = episodeMetadata.id,
                rootMetadataId = importedMatch.tvShow.id,
            )
        )

        logger.info("Linked episode file '{}' to metadata {} (S{}E{} of '{}')",
            filePath, episodeMetadata.id, seasonNumber, episodeNumber, showName)

        return MediaLinkMatchResult.Success(
            mediaLink = mediaLink,
            directory = null,
            matches = listOf(importedMatch),
            subResults = emptyList(),
        )
    }

    override suspend fun findMetadata(mediaLink: MediaLink, remoteId: String): MetadataMatch? {
        return when (val result = metadataService.findByRemoteId(remoteId)) {
            is QueryMetadataResult.Success -> result.results.firstOrNull()
            is QueryMetadataResult.ErrorDataProviderException,
            is QueryMetadataResult.ErrorDatabaseException,
            QueryMetadataResult.ErrorProviderNotFound -> null
        }
    }

    override suspend fun importMetadataMatch(mediaLink: MediaLink, metadataMatch: MetadataMatch): MetadataMatch? {
        val match = (metadataMatch as? MetadataMatch.TvShowMatch)
            ?.let { getOrImportMetadata(it) }
            ?: return null

        when (mediaLink.descriptor) {
            Descriptor.VIDEO -> {
                // Parse the episode file to get season/episode numbers
                val filePath = requireNotNull(mediaLink.filePath)
                val file = fs.getPath(filePath)

                val episodeParseResult = when (val result = fileNameParser.parseFileName(file)) {
                    is ParsedFileNameResult.Tv.EpisodeFile -> result
                    else -> {
                        logger.error("Cannot parse episode file '{}' for import", filePath)
                        return null
                    }
                }

                // Find the episode metadata
                val seasonNumber = episodeParseResult.seasonNumber
                val episodeNumber = episodeParseResult.episodeNumber
                val episodeMetadata = match.episodes.find {
                    it.seasonNumber == seasonNumber && it.number == episodeNumber
                }

                if (episodeMetadata == null) {
                    logger.warn("No episode S{}E{} found in show '{}' for file '{}'",
                        seasonNumber, episodeNumber, match.tvShow.name, filePath)
                    return null
                }

                // Link the file to the episode metadata
                mediaLinkDao.updateMetadataIds(
                    MediaLinkMetadataUpdate(
                        mediaLinkId = checkNotNull(mediaLink.id),
                        metadataId = episodeMetadata.id,
                        rootMetadataId = match.tvShow.id,
                    )
                )

                logger.info("Imported episode file '{}' to metadata {} (S{}E{} of '{}')",
                    filePath, episodeMetadata.id, seasonNumber, episodeNumber, match.tvShow.name)

                return match
            }

            else -> {
                logger.error("Cannot import metadata for descriptor type: {}", mediaLink.descriptor)
                return null
            }
        }
    }

    private suspend fun importMetadataMatch(directory: Directory, metadataMatch: MetadataMatch): MetadataMatch? {
        val match = (metadataMatch as? MetadataMatch.TvShowMatch)
            ?.let { getOrImportMetadata(it) }
            ?: return null

        val childDirectories = libraryDao.fetchChildDirectories(directory.id)
        val mediaLinkUpdates = childDirectories
            .flatMap { childDirLink ->
                linkSeasonDirectory(childDirLink, match, match.tvShow)
            }

        mediaLinkDao.updateMetadataIds(mediaLinkUpdates)

        return match
    }

    private suspend fun linkSeasonDirectory(
        seasonDirectory: Directory,
        match: MetadataMatch.TvShowMatch,
        show: TvShow,
    ): List<MediaLinkMetadataUpdate> {
        val file = fs.getPath(checkNotNull(seasonDirectory.filePath))
        return when (val result = fileNameParser.parseFileName(file)) {
            is ParsedFileNameResult.Tv.SeasonFolder -> {
                val seasonMatch = match.seasons.find { it.seasonNumber == result.seasonNumber }
                if (seasonMatch == null) {
                    logger.warn("No season match for '{}' {}", file.absolutePathString(), result)
                    emptyList()
                } else {
                    val allMediaLinks = mediaLinkDao.findByDirectoryId(seasonDirectory.id)

                    // Link video files to their episode metadata
                    val videoUpdates = allMediaLinks
                        .filter { it.descriptor == Descriptor.VIDEO }
                        .mapNotNull { videoFileLink ->
                            linkEpisodeFile(videoFileLink, match, seasonMatch, show)
                        }

                    // Link supplementary files (SUBTITLE/IMAGE) to their episode metadata
                    val supplementaryUpdates = allMediaLinks
                        .filter { it.descriptor == Descriptor.SUBTITLE || it.descriptor == Descriptor.IMAGE }
                        .filter { it.metadataId == null }
                        .mapNotNull { supplementaryLink ->
                            linkSupplementaryFile(supplementaryLink, match, seasonMatch, show)
                        }

                    videoUpdates + supplementaryUpdates
                }
            }

            else -> {
                logger.warn("Expected '{}' to be a season folder but parsed {}", file.absolutePathString(), result)
                emptyList()
            }
        }
    }

    /**
     * Link a supplementary file (SUBTITLE/IMAGE) to episode metadata by parsing its filename.
     */
    private fun linkSupplementaryFile(
        supplementaryLink: MediaLink,
        match: MetadataMatch.TvShowMatch,
        seasonMatch: TvSeason,
        show: TvShow,
    ): MediaLinkMetadataUpdate? {
        val filePath = supplementaryLink.filePath ?: return null
        val file = fs.getPath(filePath)

        return when (val parseResult = fileNameParser.parseFileName(file)) {
            is ParsedFileNameResult.Tv.EpisodeFile -> {
                val episodeMatch = match.episodes.find {
                    it.seasonNumber == seasonMatch.seasonNumber &&
                            it.number == parseResult.episodeNumber
                }
                if (episodeMatch == null) {
                    logger.debug("No episode metadata for supplementary file '{}'", filePath)
                    null
                } else {
                    MediaLinkMetadataUpdate(
                        mediaLinkId = supplementaryLink.id,
                        metadataId = episodeMatch.id,
                        rootMetadataId = show.id,
                    )
                }
            }

            else -> {
                // Supplementary file doesn't match episode naming pattern, skip it
                logger.debug("Could not parse episode info from supplementary file '{}'", filePath)
                null
            }
        }
    }

    private fun linkEpisodeFile(
        videoFileLink: MediaLink,
        match: MetadataMatch.TvShowMatch,
        seasonMatch: TvSeason,
        show: TvShow,
    ): MediaLinkMetadataUpdate? {
        val videoFile = fs.getPath(checkNotNull(videoFileLink.filePath))
        return when (val videoParseResult = fileNameParser.parseFileName(videoFile)) {
            is ParsedFileNameResult.Tv.EpisodeFile -> {
                val episodeMatch = match.episodes.find {
                    it.seasonNumber == seasonMatch.seasonNumber &&
                            it.number == videoParseResult.episodeNumber
                }
                if (episodeMatch == null) {
                    logger.warn("No episode for show ({}-{}) found for '{}'", show.id, show.name, videoFileLink)
                    null
                } else {
                    MediaLinkMetadataUpdate(
                        mediaLinkId = videoFileLink.id,
                        metadataId = episodeMatch.id,
                        rootMetadataId = show.id,
                    )
                }
            }

            else -> {
                logger.warn(
                    "Expected '{}' to be an episode file but parsed {}",
                    videoFile.absolutePathString(),
                    videoParseResult
                )
                null
            }
        }
    }

    private suspend fun getOrImportMetadata(
        metadataMatch: MetadataMatch.TvShowMatch,
    ): MetadataMatch.TvShowMatch? {
        return if (metadataMatch.exists) {
            metadataMatch.also {
                logger.debug("Matched existing metadata for '{}'", it.tvShow.name)
            }
        } else {
            val show = metadataMatch.tvShow
            logger.debug("Importing new metadata for '{}'", show.name)
            val importResults = metadataService.importMetadata(
                ImportMetadata(
                    metadataIds = listOfNotNull(metadataMatch.remoteMetadataId),
                    providerId = metadataMatch.providerId,
                    mediaKind = MediaKind.TV,
                ),
            ).filterIsInstance<ImportMetadataResult.Success>()

            if (importResults.isEmpty()) {
                logger.error("No import results for match {}", metadataMatch.tvShow)
                return null // MediaScanResult.ErrorNothingToScan
            } else {
                importResults.single().match as MetadataMatch.TvShowMatch
            }
        }
    }

    /**
     * Walk up the directory tree to find the content root directory (show folder).
     * The content root is the directory whose parent is a library root.
     *
     * @param directory The starting directory (e.g., a season folder)
     * @param libraryRootIds The set of library root directory IDs
     * @return The content root directory, or null if not found
     */
    private suspend fun findContentRootDirectory(
        directory: Directory,
        libraryRootIds: List<String>
    ): Directory? {
        var current: Directory? = directory
        var maxDepth = 10 // Prevent infinite loops

        while (current != null && maxDepth > 0) {
            val parentId = current.parentId ?: return null

            if (libraryRootIds.contains(parentId)) {
                // Found it - current directory's parent is a library root
                return current
            }

            // Move up to the parent directory
            current = libraryDao.fetchDirectory(parentId)
            maxDepth--
        }

        return null
    }
}
