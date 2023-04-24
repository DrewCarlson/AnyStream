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
import anystream.media.processor.file.FileNameParser
import anystream.media.processor.file.ParsedFileNameResult
import anystream.media.processor.file.TvFileNameParser
import anystream.metadata.MetadataManager
import anystream.models.*
import anystream.models.api.*
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.io.File

class TvFileProcessor(
    private val metadataManager: MetadataManager,
    private val queries: MetadataDbQueries,
) : MediaFileProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.TV)
    override val fileNameParser: FileNameParser = TvFileNameParser()

    private val yearRegex = "\\((\\d{4})\\)\$".toRegex()

    override suspend fun matchMediaLinkMetadata(mediaLink: MediaLinkDb, userId: Int) {
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
            MediaLink.Descriptor.MEDIA_DIRECTORY -> {
                // We have the tv show root directory
                val (tvShow, seasons, episodes) = findOrImportShow(mediaLink.metadataGid, contentFile)
                    ?: return // TODO: return auto match failed result
                queries.mediaLinkDao.updateMetadataIds(mediaLink.gid, tvShow.id, tvShow.gid)

                val childLinks = queries.mediaLinkDao.findByBasePath(checkNotNull(mediaLink.filePath))
                logger.debug(marker, "Loaded {} child MediaLinks.", childLinks.size)
                val (directoryLinks, fileLinks) = childLinks
                    .filter { childLink ->
                        childLink.descriptor == MediaLink.Descriptor.CHILD_DIRECTORY ||
                            childLink.descriptor == MediaLink.Descriptor.VIDEO
                    }
                    .partition { childLink ->
                        when (childLink.descriptor) {
                            MediaLink.Descriptor.CHILD_DIRECTORY -> true
                            MediaLink.Descriptor.VIDEO -> false
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
                        parentMediaLinkId = seasonLink?.id ?: mediaLink.id,
                        parentMediaLinkGid = seasonLink?.gid ?: mediaLink.gid,
                    )
                }

                val allLinks = updatedDirectoryLinks + updatedFileLinks
                logger.debug(marker, "Updating {} Media Links.", allLinks.size)
                queries.mediaLinkDao.updateMediaLinkIds(allLinks)
            }
            MediaLink.Descriptor.CHILD_DIRECTORY -> {
                // We have a season subfolder
                // TODO("Only show folder processing is supported")
                return
            }
            MediaLink.Descriptor.VIDEO -> {
                // We have an episode file
                // TODO("Only show folder processing is supported")
                return
            }
            // TODO: handle this check before process is invoked,
            //  replace mediaKinds with fun supports(mediaLink)
            else -> error("unsupported MediaLink.Descriptor (${mediaLink.descriptor})")
        }

        return //
    }

    private suspend fun findOrImportShow(metadataGid: String?, rootFolder: File): MetadataMatch.TvShowMatch? {
        val results = if (metadataGid == null) {
            logger.debug("Searching for TV Show by folder name '{}'.", rootFolder.name)
            queryShowTitle(rootFolder.name)
        } else {
            logger.debug("Searching for TV Show by metadata gid '{}'.", metadataGid)
            metadataManager.search(
                QueryMetadata(
                    providerId = null,
                    mediaKind = MediaKind.TV,
                    metadataGid = metadataGid,
                ),
            )
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
                    metadataIds = listOf(match.metadataGid),
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
                    match.metadataGid,
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
        val year = match?.value?.trim('(', ')')?.toInt()
        val query = title.replace(yearRegex, "").trim()

        val metadataQuery = QueryMetadata(
            providerId = null,
            query = query,
            mediaKind = MediaKind.TV,
            year = year,
        )
        return metadataManager.search(metadataQuery)
    }
}
