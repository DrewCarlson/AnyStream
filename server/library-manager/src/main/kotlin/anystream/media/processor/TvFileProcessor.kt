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
import anystream.media.file.FileNameParser
import anystream.media.file.ParsedFileNameResult
import anystream.media.file.TvFileNameParser
import anystream.metadata.MetadataService
import anystream.models.*
import anystream.models.api.*
import org.slf4j.LoggerFactory
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

class TvFileProcessor(
    private val metadataService: MetadataService,
    private val mediaLinkDao: MediaLinkDao,
    private val libraryDao: LibraryDao,
    private val fs: FileSystem,
) : MediaFileProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.TV)
    override val fileNameParser: FileNameParser = TvFileNameParser()

    private val yearRegex = "\\((\\d{4})\\)\$".toRegex()

    override suspend fun findMetadataMatches(directory: Directory, import: Boolean): List<MediaLinkMatchResult> {
        val libraryRootIds = libraryDao.fetchLibraryRootDirectories(directory.libraryId)
            .map(Directory::id)
            .takeIf { it.isNotEmpty() }
            ?: return emptyList()  // TODO: return no library error
        val contentRootDirectories = when {
            // directory is a library root, scan all direct children
            libraryRootIds.contains(directory.id) -> libraryDao.fetchChildDirectories(directory.id)
            // directory is child of library root, scan it
            libraryRootIds.contains(directory.parentId) -> listOf(directory)
            else -> TODO("Handle scanning from season folder")
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

    private suspend fun findMatchesForRootDir(directory: Directory, import: Boolean): MediaLinkMatchResult {
        val childLinks = mediaLinkDao.findByBasePath(directory.filePath)
        val subResults = childLinks.mapNotNull { childLink ->
            when (childLink.descriptor) {
                Descriptor.VIDEO -> findMatchesForFile(childLink, import)
                Descriptor.AUDIO -> error("AUDIO media links are not supported in television libraries")
                Descriptor.SUBTITLE,
                Descriptor.IMAGE,
                    -> {
                    // Ignored, supplementary files will be handled by the VIDEO file matching process.
                    null
                }
            }
        }
        return MediaLinkMatchResult.Success(
            mediaLink = checkNotNull(childLinks.firstOrNull()), //TODO: fix match result type
            directory = directory,
            matches = emptyList(),
            subResults = subResults,
        )
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
        logger.debug("Querying provider for '{}' (year {})", tvShowName, year)
        val results = metadataService.search(MediaKind.TV) {
            this.query = tvShowName
            this.year = year
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

        val sortedMatches = matches.sortedBy { scoreString(tvShowName, it.tvShow.name) }
        val matchResults = if (import && sortedMatches.isNotEmpty()) {
            // When importing, do not include unused metadata matches
            val match = sortedMatches.first()
            val importedMatch = importMetadataMatch(directory, match)
                // TODO: Return real error for import failure
                ?: return MediaLinkMatchResult.NoMatchesFound(null, directory)
            listOf(importedMatch)
        } else {
            sortedMatches
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
        TODO("Support matching metadata for individual episode files")
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
            Descriptor.VIDEO ->
                TODO("Support importing metadata for individual episode files")

            else -> error("Cannot import metadata for ${mediaLink.descriptor}")
        }
        // TODO: Update supplementary files (SUBTITLE/IMAGE)
        return match
    }

    private suspend fun importMetadataMatch(directory: Directory, metadataMatch: MetadataMatch): MetadataMatch? {
        val match = (metadataMatch as? MetadataMatch.TvShowMatch)
            ?.let { getOrImportMetadata(it) }
            ?: return null
        val show = match.tvShow
        // TODO: Update supplementary files (SUBTITLE/IMAGE)

        val childDirLinks = libraryDao.fetchChildDirectories(directory.id)
        childDirLinks.forEach { childDirLink ->
            linkSeasonDirectory(childDirLink, match, show)
        }

        return match
    }

    private suspend fun linkSeasonDirectory(
        seasonDirectory: Directory,
        match: MetadataMatch.TvShowMatch,
        show: TvShow,
    ) {
        val file = Path(checkNotNull(seasonDirectory.filePath))
        when (val result = fileNameParser.parseFileName(file)) {
            is ParsedFileNameResult.Tv.SeasonFolder -> {
                val seasonMatch = match.seasons.find { it.seasonNumber == result.seasonNumber }
                if (seasonMatch != null) {
                    mediaLinkDao.updateMetadataIds(
                        mediaLinkId = checkNotNull(seasonDirectory.id),
                        metadataId = checkNotNull(seasonMatch.id),
                    )
                    val videoFileLinks = mediaLinkDao.findByDirectoryIdAndDescriptor(
                        checkNotNull(seasonDirectory.id),
                        Descriptor.VIDEO,
                    )

                    videoFileLinks.forEach { videoFileLink ->
                        linkEpisodeFile(videoFileLink, match, seasonMatch, show, file, result)
                    }
                }
            }

            else -> {
                logger.warn("Expected '{}' to be a season folder but parsed {}", file.absolutePathString(), result)
            }
        }
    }

    private suspend fun linkEpisodeFile(
        videoFileLink: MediaLink,
        match: MetadataMatch.TvShowMatch,
        seasonMatch: TvSeason,
        show: TvShow,
        file: Path,
        result: ParsedFileNameResult,
    ) {
        val videoFile = fs.getPath(checkNotNull(videoFileLink.filePath))
        when (val videoParseResult = fileNameParser.parseFileName(videoFile)) {
            is ParsedFileNameResult.Tv.EpisodeFile -> {
                val episodeMatch = match.episodes.find {
                    it.seasonNumber == seasonMatch.seasonNumber &&
                            it.number == videoParseResult.episodeNumber
                }
                if (episodeMatch != null) {
                    mediaLinkDao.updateRootMetadataIds(
                        mediaLinkId = checkNotNull(videoFileLink.id),
                        rootMetadataId = show.id
                    )
                    mediaLinkDao.updateMetadataIds(
                        mediaLinkId = videoFileLink.id,
                        metadataId = episodeMatch.id,
                    )
                }
            }

            else -> {
                logger.warn("Expected '{}' to be an episode file but parsed {}", file.absolutePathString(), result)
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
                importResults.first().match as MetadataMatch.TvShowMatch
            }
        }
    }

    private suspend fun findOrImportShow(metadataId: String?, rootFolder: Path): MetadataMatch.TvShowMatch? {
        val results = if (metadataId == null) {
            logger.debug("Searching for TV Show by folder name '{}'.", rootFolder.name)
            queryShowTitle(rootFolder.name)
        } else {
            logger.debug("Searching for TV Show by metadata id '{}'.", metadataId)
            metadataService.search(MediaKind.TV) {
                this.metadataId = metadataId
            }
        }
        val result = results
            .filterIsInstance<QueryMetadataResult.Success>()
            .firstOrNull { it.results.isNotEmpty() }
        val match = result?.results
            ?.filterIsInstance<MetadataMatch.TvShowMatch>()
            ?.firstOrNull()
            ?: run {
                logger.debug("No match found for '{}', ignoring file.", metadataId ?: rootFolder.name)
                return null // TODO: Return no match error result
            }

        return if (match.exists) {
            match
        } else {
            logger.debug("Importing metadata for new media.")
            val importResults = metadataService.importMetadata(
                ImportMetadata(
                    metadataIds = listOfNotNull(match.remoteMetadataId),
                    providerId = result.providerId,
                    mediaKind = MediaKind.TV,
                ),
            )
            val successResult = importResults
                .filterIsInstance<ImportMetadataResult.Success>()
                .firstOrNull()

            if (importResults.isEmpty() || successResult == null) {
                logger.debug(
                    "Failed to import metadata for '{}' on '{}', ignoring file.",
                    match.remoteMetadataId,
                    result.providerId,
                )
                return null // TODO: Return metadata import error result
            } else {
                successResult.match as MetadataMatch.TvShowMatch
            }
        }
    }

    private suspend fun queryShowTitle(title: String): List<QueryMetadataResult> {
        val match = yearRegex.find(title)
        return metadataService.search(MediaKind.TV) {
            query = title.replace(yearRegex, "").trim()
            year = match?.value?.trim('(', ')')?.toInt()
        }
    }
}
