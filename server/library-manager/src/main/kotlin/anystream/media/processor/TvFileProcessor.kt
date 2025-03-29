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
import anystream.media.file.FileNameParser
import anystream.media.file.ParsedFileNameResult
import anystream.media.file.TvFileNameParser
import anystream.metadata.MetadataManager
import anystream.models.*
import anystream.models.api.*
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

class TvFileProcessor(
    private val metadataManager: MetadataManager,
    private val mediaLinkDao: MediaLinkDao,
) : MediaFileProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.TV)
    override val fileNameParser: FileNameParser = TvFileNameParser()

    private val yearRegex = "\\((\\d{4})\\)\$".toRegex()

    override suspend fun findMetadataMatches(mediaLink: MediaLink, import: Boolean): MediaLinkMatchResult {
        return when (mediaLink.descriptor) {
            Descriptor.VIDEO,
            -> findMatchesForFile(mediaLink, import)

            Descriptor.AUDIO,
            Descriptor.SUBTITLE,
            Descriptor.IMAGE,
            -> MediaLinkMatchResult.NoSupportedFiles(mediaLink)
        }
    }

    private suspend fun findMatchesForRootDir(mediaLink: MediaLink, import: Boolean): MediaLinkMatchResult {
        val childLinks = mediaLinkDao.findByParentId(requireNotNull(mediaLink.id))
        val subResults = childLinks.mapNotNull { childLink ->
            when (childLink.descriptor) {
                Descriptor.VIDEO -> findMatchesForFile(childLink, import)
                Descriptor.AUDIO -> error("AUDIO links are not supported in television libraries")
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
            matches = emptyList(),
            subResults = subResults,
        )
    }

    private suspend fun findMatchesForMediaDir(mediaLink: MediaLink, import: Boolean): MediaLinkMatchResult {
        val path = requireNotNull(mediaLink.filePath)
        val episodeLinks = mediaLinkDao.findByBasePathAndDescriptor(path, Descriptor.VIDEO)
        if (episodeLinks.isEmpty()) {
            return MediaLinkMatchResult.NoSupportedFiles(mediaLink)
        }

        val (tvShowName, year) = when (val result = fileNameParser.parseFileName(Path(path))) {
            is ParsedFileNameResult.Tv.ShowFolder -> result
            else -> {
                logger.debug("Expected to find show folder but could not parse '{}' {}", path, result)
                return MediaLinkMatchResult.FileNameParseFailed(mediaLink)
            }
        }
        logger.debug("Querying provider for '{}' (year {})", tvShowName, year)
        val results = metadataManager.search(MediaKind.TV) {
            this.query = tvShowName
            this.year = year
        }
        val matches = results
            .filterIsInstance<QueryMetadataResult.Success>()
            .flatMap { it.results }
            .filterIsInstance<MetadataMatch.TvShowMatch>()
        if (matches.isEmpty()) {
            logger.debug("No metadata match results '{}' (year {})", tvShowName, year)
            return MediaLinkMatchResult.NoMatchesFound(mediaLink)
        }

        logger.debug("Found {} Metadata match results '{}' (year {})", matches.size, tvShowName, year)

        val metadataMatch = matches.sortedBy { scoreString(tvShowName, it.tvShow.name) }

        if (import && metadataMatch.isNotEmpty()) {
            importMetadataMatch(mediaLink, metadataMatch.first())
        }

        return MediaLinkMatchResult.Success(
            mediaLink = mediaLink,
            matches = metadataMatch,
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
        return when (val result = metadataManager.findByRemoteId(remoteId)) {
            is QueryMetadataResult.Success -> result.results.firstOrNull()
            is QueryMetadataResult.ErrorDataProviderException,
            is QueryMetadataResult.ErrorDatabaseException,
            QueryMetadataResult.ErrorProviderNotFound -> null
        }
    }

    override suspend fun importMetadataMatch(mediaLink: MediaLink, metadataMatch: MetadataMatch) {
        val match = (metadataMatch as? MetadataMatch.TvShowMatch)
            ?.let { getOrImportMetadata(it) }
            ?: return
        val show = match.tvShow

        when (mediaLink.descriptor) {
            Descriptor.VIDEO ->
                TODO("Support importing metadata for individual episode files")

            else -> error("Cannot import metadata for ${mediaLink.descriptor}")
        }
        // TODO: Update supplementary files (SUBTITLE/IMAGE)
    }

    private suspend fun linkSeasonFolder(
        childDirLink: MediaLink,
        mediaLink: MediaLink,
        match: MetadataMatch.TvShowMatch,
        show: TvShow,
    ) {
        val file = Path(checkNotNull(childDirLink.filePath))
        when (val result = fileNameParser.parseFileName(file)) {
            is ParsedFileNameResult.Tv.SeasonFolder -> {
                mediaLinkDao.updateRootMetadataIds(
                    checkNotNull(childDirLink.id),
                    checkNotNull(mediaLink.id),
                )
                val seasonMatch = match.seasons.find { it.seasonNumber == result.seasonNumber }
                if (seasonMatch != null) {
                    mediaLinkDao.updateMetadataIds(
                        checkNotNull(childDirLink.id),
                        checkNotNull(seasonMatch.id),
                    )
                    val videoFileLinks = mediaLinkDao.findByParentIdAndDescriptor(
                        checkNotNull(childDirLink.id),
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
        val videoFile = Path(checkNotNull(videoFileLink.filePath))
        when (val videoParseResult = fileNameParser.parseFileName(videoFile)) {
            is ParsedFileNameResult.Tv.EpisodeFile -> {
                val episodeMatch = match.episodes.find {
                    it.seasonNumber == seasonMatch.seasonNumber &&
                            it.number == videoParseResult.episodeNumber
                }
                if (episodeMatch != null) {
                    mediaLinkDao.updateRootMetadataIds(
                        checkNotNull(videoFileLink.id),
                        show.id,
                    )
                    mediaLinkDao.updateMetadataIds(
                        videoFileLink.id,
                        episodeMatch.id,
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
            val importResults = metadataManager.importMetadata(
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

    /*override suspend fun matchMediaLinkMetadata(mediaLink: MediaLink) {
        val marker = MarkerFactory.getMarker(mediaLink.gid)
        logger.debug(marker, "Matching metadata for {}", mediaLink)
        val contentFile = File(
            requireNotNull(mediaLink.filePath) {
                "Cannot process media link without a filePath: $mediaLink"
            },
        )
        if (contentFile.isDirectory && contentFile.listFiles().isNullOrEmpty()) {
            logger.debug(marker, "Content folder is empty.")
            return // MediaScanResult.ErrorNothingToScan
        }

        when (mediaLink.descriptor) {
            Descriptor.MEDIA_DIRECTORY -> {
                // We have the tv show root directory
                val (tvShow, seasons, episodes) = findOrImportShow(mediaLink.metadataGid, contentFile)
                    ?: return // TODO: return auto match failed result
                queries.mediaLinkDao.updateMetadataIds(mediaLink.gid, tvShow.id, tvShow.gid)

                val childLinks = queries.mediaLinkDao.findByBasePath(checkNotNull(mediaLink.filePath))
                logger.debug(marker, "Loaded {} child MediaLinks.", childLinks.size)
                val (directoryLinks, fileLinks) = childLinks
                    .filter { childLink ->
                        childLink.descriptor == Descriptor.CHILD_DIRECTORY ||
                            childLink.descriptor == Descriptor.VIDEO
                    }
                    .partition { childLink ->
                        when (childLink.descriptor) {
                            Descriptor.CHILD_DIRECTORY -> true
                            Descriptor.VIDEO -> false
                            else -> error("Unexpected descriptor ${childLink.descriptor}")
                        }
                    }

                val updatedDirectoryLinks = directoryLinks.mapNotNull { childLink ->
                    val file = File(checkNotNull(childLink.filePath))
                    when (val result = fileNameParser.parseFileName(file.name)) {
                        is ParsedFileNameResult.Tv.SeasonFolder -> {
                            val seasonNumber = result.seasonNumber
                            logger.debug(marker, "Parsed '{}' from season folder '{}'.", seasonNumber, file.name)
                            seasons.find { it.seasonNumber == seasonNumber }?.let { season ->
                                logger.debug(marker, "Linked season metadata to folder for season {}.", seasonNumber)
                                childLink.copy(
                                    metadataId = season.id,
                                    metadataGid = season.gid,
                                    rootMetadataId = tvShow.id,
                                    rootMetadataGid = tvShow.gid,
                                )
                            }
                        }
                        else -> {
                            logger.debug(marker, "Expected season folder but could not parse '{}'.", file.name)
                            null
                        }
                    }
                }
                val episodesBySeason = episodes.groupBy { it.seasonNumber }
                val updatedFileLinks = fileLinks.mapNotNull { childLink ->
                    val file = File(checkNotNull(childLink.filePath))
                    val (seasonNumber, episodeNumber) = when (val result = fileNameParser.parseFileName(file.name)) {
                        is ParsedFileNameResult.Tv.EpisodeFile -> result
                        else -> {
                            logger.debug(marker, "Expected to find episode file but could not parse '{}'", file.name)
                            return@mapNotNull null
                        }
                    }
                    logger.debug(
                        marker,
                        "Parsed season '{}' and episode '{}' from '{}'",
                        seasonNumber,
                        episodeNumber,
                        file.name,
                    )
                    val actualSeasonNumber = seasonNumber
                        ?: queries.mediaLinkDao
                            .findByFilePath(file.parent)
                            ?.run {
                                if (seasons.filter { it.seasonNumber > 0 }.size == 1) {
                                    seasons.first()
                                } else {
                                    seasons.firstOrNull { it.id == metadataId }
                                }
                            }
                            ?.seasonNumber // TODO: verify it is a season directory
                        ?: return@mapNotNull null
                    val episode = episodesBySeason[actualSeasonNumber]
                        ?.firstOrNull { it.number == episodeNumber }
                        ?: run {
                            logger.debug(marker, "Failed to find episode metadata for episode {}.", episodeNumber)
                            return@mapNotNull null
                        }
                    val season = seasons.firstOrNull { it.seasonNumber == actualSeasonNumber }
                        ?: return@mapNotNull null
                    val seasonLink = updatedDirectoryLinks.firstOrNull { it.metadataId == season.id }

                    logger.debug(
                        marker,
                        "Linked episode file {} to S{} E{} of '{}'",
                        file.name,
                        season.seasonNumber,
                        episode.number,
                        tvShow.name,
                    )
                    childLink.copy(
                        metadataId = episode.id,
                        metadataGid = episode.gid,
                        rootMetadataId = tvShow.id,
                        rootMetadataGid = tvShow.gid,
                        parentId = seasonLink?.id ?: mediaLink.id,
                        parentGid = seasonLink?.gid ?: mediaLink.gid,
                    )
                }

                val allLinks = updatedDirectoryLinks + updatedFileLinks
                logger.debug(marker, "Updating {} Media Links.", allLinks.size)
                queries.mediaLinkDao.updateMediaLinkIds(allLinks)
            }
            Descriptor.CHILD_DIRECTORY -> {
                // We have a season subfolder
                // TODO("Only show folder processing is supported")
                return
            }
            Descriptor.VIDEO -> {
                // We have an episode file
                // TODO("Only show folder processing is supported")
                return
            }
            // TODO: handle this check before process is invoked,
            //  replace mediaKinds with fun supports(mediaLink)
            else -> error("unsupported Descriptor (${mediaLink.descriptor})")
        }

        return //
    }*/

    private suspend fun findOrImportShow(metadataGid: String?, rootFolder: Path): MetadataMatch.TvShowMatch? {
        val results = if (metadataGid == null) {
            logger.debug("Searching for TV Show by folder name '{}'.", rootFolder.name)
            queryShowTitle(rootFolder.name)
        } else {
            logger.debug("Searching for TV Show by metadata gid '{}'.", metadataGid)
            metadataManager.search(MediaKind.TV) {
                this.metadataGid = metadataGid
            }
        }
        val result = results
            .filterIsInstance<QueryMetadataResult.Success>()
            .firstOrNull { it.results.isNotEmpty() }
        val match = result?.results
            ?.filterIsInstance<MetadataMatch.TvShowMatch>()
            ?.firstOrNull()
            ?: run {
                logger.debug("No match found for '{}', ignoring file.", metadataGid ?: rootFolder.name)
                return null // TODO: Return no match error result
            }

        return if (match.exists) {
            match
        } else {
            logger.debug("Importing metadata for new media.")
            val importResults = metadataManager.importMetadata(
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
        return metadataManager.search(MediaKind.TV) {
            query = title.replace(yearRegex, "").trim()
            year = match?.value?.trim('(', ')')?.toInt()
        }
    }
}
