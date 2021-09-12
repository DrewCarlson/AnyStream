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
package anystream.media.processor

import anystream.data.MediaDbQueries
import anystream.media.MediaImportProcessor
import anystream.metadata.MetadataManager
import anystream.models.*
import anystream.models.api.*
import anystream.util.concurrentMap
import com.mongodb.MongoException
import com.mongodb.MongoQueryException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.slf4j.Logger
import org.slf4j.Marker
import java.io.File
import java.time.Instant

class TvImportProcessor(
    private val metadataManager: MetadataManager,
    private val queries: MediaDbQueries,
    private val scope: CoroutineScope,
    private val logger: Logger,
) : MediaImportProcessor {

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.TV)

    private val yearRegex = "\\((\\d\\d\\d\\d)\\)".toRegex()
    private val episodeRegex = "(.*) - S([0-9]{1,2})E([0-9]{1,2}) - (.*)".toRegex()

    override suspend fun process(
        contentFile: File,
        userId: String,
        marker: Marker,
    ): ImportMediaResult {
        if (contentFile.isFile) {
            logger.debug(marker, "Detected single content file, nothing to import")
            // TODO: Identify single files as episodes or supplemental content
            return ImportMediaResult.ErrorNothingToImport
        } else if (contentFile.listFiles().isNullOrEmpty()) {
            logger.debug(marker, "Content folder is empty.")
            return ImportMediaResult.ErrorNothingToImport
        }

        val existingRef = try {
            queries.findMediaRefByFilePath(contentFile.absolutePath)
        } catch (e: MongoQueryException) {
            return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
        }

        val tvShow = if (existingRef == null) {
            // TODO: Improve query capabilities
            val match = yearRegex.find(contentFile.nameWithoutExtension)
            val year = match?.value?.trim('(', ')')?.toInt() ?: 0
            logger.debug(marker, "Found content year: $year")

            val query = contentFile.nameWithoutExtension
                .replace(yearRegex, "")
                .trim()
            logger.debug(marker, "Querying provider for '$query'")

            val queryResults = metadataManager.search(
                QueryMetadata(
                    providerId = null,
                    query = query,
                    mediaKind = MediaKind.TV,
                    year = year,
                )
            )
            val result = queryResults.firstOrNull { result ->
                result is QueryMetadataResult.Success && result.results.isNotEmpty()
            }
            when (result) {
                is QueryMetadataResult.Success -> {
                    val metadataMatch = result.results
                        .filterIsInstance<MetadataMatch.TvShowMatch>()
                        .maxByOrNull { it.tvShow.name.equals(query, true) }
                        ?: result.results.first()

                    if (metadataMatch.exists) {
                        (metadataMatch as MetadataMatch.TvShowMatch).tvShow
                    } else {
                        val importResults = metadataManager.importMetadata(
                            ImportMetadata(
                                contentIds = listOf(metadataMatch.contentId),
                                providerId = result.providerId,
                                mediaKind = MediaKind.TV,
                            )
                        ).filterIsInstance<ImportMetadataResult.Success>()

                        if (importResults.isEmpty()) {
                            logger.debug(marker, "Provider lookup error: $queryResults")
                            return ImportMediaResult.ErrorMediaMatchNotFound(
                                contentPath = contentFile.absolutePath,
                                query = query,
                                results = queryResults,
                            )
                        } else {
                            (importResults.first().match as MetadataMatch.TvShowMatch).tvShow
                        }
                    }
                }
                else -> {
                    logger.debug(marker, "Provider lookup error: $queryResults")
                    return ImportMediaResult.ErrorMediaMatchNotFound(
                        contentPath = contentFile.absolutePath,
                        query = query,
                        results = queryResults,
                    )
                }
            }
        } else {
            logger.debug(marker, "Content file reference already exists")
            // NOTE: only tv show folders can be imported, if already imported
            // we still need to find new episode files
            //return ImportMediaResult.ErrorMediaRefAlreadyExists(existingRef.id)
            checkNotNull(queries.findTvShowById(existingRef.rootContentId ?: existingRef.contentId))
        }
        val episodes = queries.findEpisodesByShow(tvShow.id)

        val mediaRef = existingRef ?: LocalMediaReference(
            id = ObjectId.get().toString(),
            contentId = tvShow.id,
            added = Instant.now().toEpochMilli(),
            addedByUserId = userId,
            filePath = contentFile.absolutePath,
            mediaKind = MediaKind.TV,
            directory = true,
        ).also { mediaRef ->
            try {
                queries.insertMediaReference(mediaRef)
            } catch (e: MongoException) {
                logger.debug(marker, "Failed to create media reference", e)
                return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
            }
        }

        val subFolders = contentFile.listFiles()?.toList().orEmpty()
        val seasonDirectories = subFolders
            .filter { it.isDirectory && it.name.startsWith("season", true) }
            .mapNotNull { file ->
                file.name
                    .split(" ")
                    .lastOrNull()
                    ?.toIntOrNull()
                    ?.let { num -> tvShow.seasons.firstOrNull { it.seasonNumber == num } }
                    ?.let { it to file }
            }

        val seasonResults = seasonDirectories.asFlow()
            .concurrentMap(scope, 5) { (season, folder) ->
                folder.importSeason(userId, season, episodes, marker)
            }
            .toList()

        return ImportMediaResult.Success(
            mediaId = tvShow.id,
            mediaReference = mediaRef,
            subresults = seasonResults,
        )
    }

    private suspend fun File.importSeason(
        userId: String,
        season: TvSeason,
        episodes: List<Episode>,
        marker: Marker,
    ): ImportMediaResult {
        val existingMediaRef = queries.findMediaRefByFilePath(absolutePath)
        val mediaRef = existingMediaRef ?: LocalMediaReference(
            id = ObjectId.get().toString(),
            contentId = season.id,
            added = Instant.now().toEpochMilli(),
            addedByUserId = userId,
            filePath = absolutePath,
            mediaKind = MediaKind.TV,
            directory = true,
        ).also { mediaRef ->
            try {
                queries.insertMediaReference(mediaRef)
            } catch (e: MongoException) {
                logger.debug(marker, "Failed to create season media ref", e)
                return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
            }
        }

        val episodeFiles = listFiles()?.toList().orEmpty()
            .sortedByDescending(File::length)
            .filter { it.isFile && it.nameWithoutExtension.matches(episodeRegex) }

        val episodeFileMatches = episodeFiles.map { episodeFile ->
            val nameParts = episodeRegex.find(episodeFile.nameWithoutExtension)!!
            val (_, seasonNumber, episodeNumber, _) = nameParts.destructured

            episodeFile to episodes.find { episode ->
                episode.seasonNumber == seasonNumber.toIntOrNull() &&
                        episode.number == episodeNumber.toIntOrNull()
            }
        }.filter { (file, _) ->
            queries.findMediaRefByFilePath(file.absolutePath) == null
        }

        val episodeRefs = episodeFileMatches
            .mapNotNull { (file, episode) ->
                episode?.let {
                    LocalMediaReference(
                        id = ObjectId.get().toString(),
                        contentId = episode.id,
                        added = Instant.now().toEpochMilli(),
                        addedByUserId = userId,
                        filePath = file.absolutePath,
                        mediaKind = MediaKind.TV,
                        directory = false,
                        rootContentId = episode.showId,
                    )
                }
            }
        val results = try {
            if (episodeRefs.isNotEmpty()) {
                queries.insertMediaReferences(episodeRefs)
                episodeRefs.map { ref ->
                    ImportMediaResult.Success(
                        mediaId = ref.contentId,
                        mediaReference = ref,
                    )
                }
            } else emptyList()
        } catch (e: MongoException) {
            logger.debug(marker, "Error creating episode references", e)
            listOf(ImportMediaResult.ErrorDatabaseException(e.stackTraceToString()))
        }

        return ImportMediaResult.Success(
            mediaId = season.id,
            mediaReference = mediaRef,
            subresults = results,
        )
    }
}