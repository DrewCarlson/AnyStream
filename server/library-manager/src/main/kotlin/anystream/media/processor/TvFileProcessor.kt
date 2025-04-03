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
            ?: return emptyList()  // TODO: return no library error
        val contentRootDirectories = when {
            // directory is a library root, scan all direct children
            libraryRootIds.contains(directory.id) -> libraryDao.fetchChildDirectories(directory.id)
            // directory is child of library root, scan it
            libraryRootIds.contains(directory.parentId) -> listOf(directory)
            else -> TODO("Handle scanning from season folder")
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
            // TODO: Return real error for import failure
                ?: return MediaLinkMatchResult.NoMatchesFound(null, directory)
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

        // TODO: Update supplementary files (SUBTITLE/IMAGE)
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
                    val videoFileLinks = mediaLinkDao.findByDirectoryIdAndDescriptor(
                        seasonDirectory.id,
                        Descriptor.VIDEO,
                    )

                    videoFileLinks.mapNotNull { videoFileLink ->
                        linkEpisodeFile(videoFileLink, match, seasonMatch, show)
                    }
                }
            }

            else -> {
                logger.warn("Expected '{}' to be a season folder but parsed {}", file.absolutePathString(), result)
                emptyList()
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
}
