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

import anystream.data.asMovie
import anystream.media.MediaImportProcessor
import anystream.models.LocalMediaReference
import anystream.models.MediaReference
import anystream.models.MediaKind
import anystream.models.Movie
import anystream.models.api.ImportMediaResult
import com.mongodb.MongoException
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.TmdbMovies
import org.bson.types.ObjectId
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import org.slf4j.Logger
import org.slf4j.Marker
import java.io.File
import java.time.Instant

class MovieImportProcessor(
    private val tmdb: TmdbApi,
    mongodb: CoroutineDatabase,
    private val logger: Logger,
) : MediaImportProcessor {

    private val moviesDb = mongodb.getCollection<Movie>()
    private val mediaRefs = mongodb.getCollection<MediaReference>()
    private val yearRegex = "\\((\\d\\d\\d\\d)\\)".toRegex()

    override val mediaKinds: List<MediaKind> = listOf(MediaKind.MOVIE)

    override suspend fun process(
        contentFile: File,
        userId: String,
        marker: Marker
    ): ImportMediaResult {
        val movieFile = if (contentFile.isFile) {
            logger.debug(marker, "Detected single content file")
            contentFile
        } else {
            logger.debug(marker, "Detected content directory")
            contentFile.listFiles()
                ?.toList()
                .orEmpty()
                .maxByOrNull(File::length)
                ?.also { result ->
                    logger.debug(marker, "Largest content file found: ${result.absolutePath}")
                }
        }

        if (movieFile == null) {
            logger.debug(marker, "Content file not found")
            return ImportMediaResult.ErrorMediaRefNotFound
        }

        val existingRef = try {
            mediaRefs.findOne(LocalMediaReference::filePath eq movieFile.absolutePath)
        } catch (e: MongoException) {
            return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
        }
        if (existingRef != null) {
            logger.debug(marker, "Content file reference already exists")
            return ImportMediaResult.ErrorMediaRefAlreadyExists(existingRef.id)
        }

        val match = yearRegex.find(movieFile.nameWithoutExtension)
        val year = match?.value?.trim('(', ')')?.toInt() ?: 0

        logger.debug(marker, "Found content year: $year")

        // TODO: Improve query capabilities
        val query = movieFile.nameWithoutExtension
            .replace(yearRegex, "")
            .trim()
        val response = try {
            logger.debug(marker, "Querying provider for '$query'")
            tmdb.search.searchMovie(query, year, null, false, 0)
        } catch (e: Throwable) {
            logger.debug(marker, "Provider lookup error", e)
            return ImportMediaResult.ErrorDataProviderException(e.stackTraceToString())
        }
        logger.debug(marker, "Provider returned ${response.totalResults} results")
        if (response.results.isEmpty()) {
            return ImportMediaResult.ErrorMediaMatchNotFound(contentFile.absolutePath, query)
        }

        val tmdbMovie = response.results.first().apply {
            logger.debug(marker, "Detected media as ${id}:'${title}' (${releaseDate})")
        }

        val existingRecord = moviesDb.findOne(Movie::tmdbId eq tmdbMovie.id)
        return if (existingRecord == null) {
            logger.debug(marker, "Movie data import required")
            movieFile.importMovie(userId, tmdbMovie.id, marker)
        } else {
            logger.debug(marker, "Movie data exists at ${existingRecord.id}, creating media ref")
            try {
                val reference = LocalMediaReference(
                    id = ObjectId.get().toString(),
                    contentId = existingRecord.id,
                    added = Instant.now().toEpochMilli(),
                    addedByUserId = userId,
                    filePath = movieFile.absolutePath,
                    mediaKind = MediaKind.MOVIE,
                    directory = false,
                )
                mediaRefs.insertOne(reference)
                ImportMediaResult.Success(existingRecord.id, reference)
            } catch (e: MongoException) {
                logger.debug(marker, "Failed to create media reference", e)
                ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
            }
        }
    }

    // Import movie data and create media ref
    private suspend fun File.importMovie(
        userId: String,
        tmdbId: Int,
        marker: Marker
    ): ImportMediaResult {
        val movie = try {
            tmdb.movies.getMovie(
                tmdbId,
                null,
                TmdbMovies.MovieMethod.images,
                TmdbMovies.MovieMethod.release_dates,
                TmdbMovies.MovieMethod.alternative_titles,
                TmdbMovies.MovieMethod.keywords
            )
        } catch (e: Throwable) {
            logger.debug(marker, "Extended provider data query failed", e)
            return ImportMediaResult.ErrorDataProviderException(e.stackTraceToString())
        }
        val movieId = ObjectId.get().toString()
        try {
            val reference = LocalMediaReference(
                id = ObjectId.get().toString(),
                contentId = movieId,
                added = Instant.now().toEpochMilli(),
                addedByUserId = userId,
                filePath = absolutePath,
                mediaKind = MediaKind.MOVIE,
                directory = false,
            )
            moviesDb.insertOne(movie.asMovie(movieId, userId))
            mediaRefs.insertOne(reference)
            logger.debug(
                marker,
                "Movie and media ref created movieId=$movieId, mediaRefId=${reference.id}"
            )
            return ImportMediaResult.Success(movieId, reference)
        } catch (e: MongoException) {
            logger.debug(marker, "Movie or media ref creation failed", e)
            return ImportMediaResult.ErrorDatabaseException(e.stackTraceToString())
        }
    }
}