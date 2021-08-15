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

import anystream.media.MediaImportProcessor
import anystream.models.*
import anystream.models.api.ImportMediaResult
import anystream.routes.concurrentMap
import com.mongodb.MongoException
import com.mongodb.MongoQueryException
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.TmdbTV
import info.movito.themoviedbapi.TmdbTvSeasons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import org.slf4j.Logger
import org.slf4j.Marker
import java.io.File
import java.time.Instant

class TvImportProcessor(
    private val tmdb: TmdbApi,
    mongodb: CoroutineDatabase,
    private val scope: CoroutineScope,
    private val logger: Logger,
) : MediaImportProcessor {

    private val tvShowDb = mongodb.getCollection<TvShow>()
    private val episodeDb = mongodb.getCollection<Episode>()
    private val mediaRefDb = mongodb.getCollection<MediaReference>()

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.TV)

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
            mediaRefDb.findOne(LocalMediaReference::filePath eq contentFile.absolutePath)
        } catch (e: MongoQueryException) {
            return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
        }
        if (existingRef != null) {
            logger.debug(marker, "Content file reference already exists")
            return ImportMediaResult.ErrorMediaRefAlreadyExists(existingRef.id)
        }

        // TODO: Improve query capabilities
        val query = contentFile.name
        val response = try {
            logger.debug(marker, "Querying provider for '$query'")
            tmdb.search.searchTv(query, "en", 1)
        } catch (e: Throwable) {
            logger.debug(marker, "Provider lookup error", e)
            return ImportMediaResult.ErrorDataProviderException(e.stackTraceToString())
        }
        logger.debug(marker, "Provider returned ${response.totalResults} results")
        if (response.results.isEmpty()) {
            return ImportMediaResult.ErrorMediaMatchNotFound(contentFile.path, query)
        }

        val tmdbShow = response.results.first().apply {
            logger.debug(marker, "Detected media as ${id}:'${name}' (${firstAirDate})")
        }

        val existingRecord = tvShowDb.findOne(TvShow::tmdbId eq tmdbShow.id)
        val (show, episodes) = existingRecord?.let { show ->
            show to episodeDb.find(Episode::showId eq show.id).toList()
        } ?: try {
            logger.debug(marker, "Show data import required")
            importShow(tmdbShow.id)
        } catch (e: MongoException) {
            logger.debug(marker, "Failed to insert new show", e)
            return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
        } catch (e: Throwable) {
            logger.debug(marker, "Data provider query failed", e)
            return ImportMediaResult.ErrorDataProviderException(e.stackTraceToString())
        }

        val mediaRefId = ObjectId.get().toString()
        try {
            mediaRefDb.insertOne(
                LocalMediaReference(
                    id = mediaRefId,
                    contentId = show.id,
                    added = Instant.now().toEpochMilli(),
                    addedByUserId = userId,
                    filePath = contentFile.absolutePath,
                    mediaKind = MediaKind.TV,
                    directory = true,
                )
            )
        } catch (e: MongoException) {
            logger.debug(marker, "Failed to create media reference", e)
            return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
        }

        val subFolders = contentFile.listFiles()?.toList().orEmpty()
        val seasonDirectories = subFolders
            .filter { it.isDirectory && it.name.startsWith("season", true) }
            .mapNotNull { file ->
                file.name
                    .split(" ")
                    .lastOrNull()
                    ?.toIntOrNull()
                    ?.let { num -> show.seasons.firstOrNull { it.seasonNumber == num } }
                    ?.let { it to file }
            }

        val seasonResults = seasonDirectories.asFlow()
            .concurrentMap(scope, 5) { (season, folder) ->
                folder.importSeason(userId, season, episodes, marker)
            }
            .toList()

        return ImportMediaResult.Success(
            mediaId = show.id,
            mediaRefId = mediaRefId,
            subresults = seasonResults,
        )
    }

    // Import show data
    private suspend fun importShow(tmdbId: Int): Pair<TvShow, List<Episode>> {
        val showId = ObjectId.get().toString()
        val tmdbShow = tmdb.tvSeries.getSeries(
            tmdbId,
            "en",
            TmdbTV.TvMethod.keywords,
            TmdbTV.TvMethod.external_ids,
            TmdbTV.TvMethod.images,
            TmdbTV.TvMethod.content_ratings,
            TmdbTV.TvMethod.credits,
        )
        val tmdbSeasons = tmdbShow.seasons
            .filter { it.seasonNumber > 0 }
            .map { season ->
                tmdb.tvSeasons.getSeason(
                    tmdbId,
                    season.seasonNumber,
                    "en",
                    TmdbTvSeasons.SeasonMethod.images,
                )
            }
        val episodes = tmdbSeasons.flatMap { season ->
            season.episodes.map { episode ->
                Episode(
                    id = ObjectId.get().toString(),
                    tmdbId = episode.id,
                    name = episode.name,
                    overview = episode.overview,
                    airDate = episode.airDate ?: "",
                    number = episode.episodeNumber,
                    seasonNumber = episode.seasonNumber,
                    showId = showId,
                    stillPath = episode.stillPath ?: ""
                )
            }
        }
        val show = TvShow(
            id = showId,
            name = tmdbShow.name,
            tmdbId = tmdbShow.id,
            overview = tmdbShow.overview,
            firstAirDate = tmdbShow.firstAirDate ?: "",
            numberOfSeasons = tmdbShow.numberOfSeasons,
            numberOfEpisodes = tmdbShow.numberOfEpisodes,
            posterPath = tmdbShow.posterPath ?: "",
            added = Instant.now().toEpochMilli(),
            seasons = tmdbSeasons.map { season ->
                TvSeason(
                    id = ObjectId.get().toString(),
                    tmdbId = season.id,
                    name = season.name,
                    overview = season.overview,
                    seasonNumber = season.seasonNumber,
                    airDate = season.airDate ?: "",
                    posterPath = season.posterPath ?: "",
                )
            }
        )

        tvShowDb.insertOne(show)
        episodeDb.insertMany(episodes)
        return show to episodes
    }

    private suspend fun File.importSeason(
        userId: String,
        season: TvSeason,
        episodes: List<Episode>,
        marker: Marker,
    ): ImportMediaResult {
        val seasonRefId = ObjectId.get().toString()
        try {
            mediaRefDb.insertOne(
                LocalMediaReference(
                    id = seasonRefId,
                    contentId = season.id,
                    added = Instant.now().toEpochMilli(),
                    addedByUserId = userId,
                    filePath = absolutePath,
                    mediaKind = MediaKind.TV,
                    directory = true,
                )
            )
        } catch (e: MongoException) {
            logger.debug(marker, "Failed to create season media ref", e)
            return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
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
                mediaRefDb.insertMany(episodeRefs)
                episodeRefs.map { ref ->
                    ImportMediaResult.Success(
                        mediaId = ref.contentId,
                        mediaRefId = ref.id,
                    )
                }
            } else emptyList()
        } catch (e: MongoException) {
            logger.debug(marker, "Error creating episode references", e)
            listOf(ImportMediaResult.ErrorDatabaseException(e.stackTraceToString()))
        }

        return ImportMediaResult.Success(
            mediaId = season.id,
            mediaRefId = seasonRefId,
            subresults = results,
        )
    }
}