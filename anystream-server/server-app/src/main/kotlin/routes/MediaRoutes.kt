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
package anystream.routes

import anystream.data.*
import anystream.media.MediaImporter
import anystream.metadata.MetadataManager
import anystream.models.Episode
import anystream.models.MediaReference
import anystream.models.api.*
import anystream.util.isRemoteId
import anystream.util.logger
import app.moviebase.tmdb.Tmdb3
import app.moviebase.tmdb.model.AppendResponse
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import torrentsearch.TorrentSearch
import torrentsearch.models.Category
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

fun Route.addMediaManageRoutes(
    tmdb: Tmdb3,
    torrentSearch: TorrentSearch,
    importer: MediaImporter,
    queries: MediaDbQueries,
) {
    route("/media") {
        route("/{mediaId}") {
            get("/refresh-stream-details") {
                /*val mediaId = call.parameters["mediaId"] ?: ""
                val mediaRefIds = mediaRefsDb.projection(
                    MediaReference::id,
                    or(
                        MediaReference::contentId eq mediaId,
                        MediaReference::rootContentId eq mediaId,
                    )
                ).toList()
                call.respond(importer.importStreamDetails(mediaRefIds))*/
                TODO()
            }
            get("/refresh-metadata") {
                val mediaId = call.parameters["mediaId"] ?: ""
                val session = checkNotNull(call.principal<UserSession>())
                val result = queries.findMediaById(mediaId)

                if (!result.hasResult()) {
                    logger.warn("No media found for $mediaId")
                    return@get call.respond(MediaLookupResponse())
                }
                val movieResult = result.movie
                val showResult = result.tvShow
                val episodeResult = result.episode
                val seasonResult = result.season
                when {
                    movieResult != null -> {
                        val movie = try {
                            tmdb.movies.getDetails(
                                movieResult.tmdbId,
                                null,
                                listOf(
                                    AppendResponse.EXTERNAL_IDS,
                                    AppendResponse.CREDITS,
                                    AppendResponse.RELEASES_DATES,
                                    AppendResponse.IMAGES,
                                    AppendResponse.MOVIE_CREDITS,
                                )
                            )
                        } catch (e: Throwable) {
                            logger.error("Extended provider data query failed", e)
                            return@get call.respond(InternalServerError)
                        }.asMovie(mediaId, session.userId)
                        queries.updateMovie(movie)
                        val mediaRefs = queries.findMediaRefsByContentId(movie.id)
                        return@get call.respond(
                            MediaLookupResponse(
                                movie = MovieResponse(movie, mediaRefs)
                            )
                        )
                    }
                    showResult != null -> {
                        val tmdbSeries = try {
                            tmdb.show.getDetails(
                                showResult.tmdbId,
                                "en",
                                listOf(
                                    AppendResponse.EXTERNAL_IDS,
                                    AppendResponse.CREDITS,
                                    AppendResponse.RELEASES_DATES,
                                    AppendResponse.IMAGES,
                                    AppendResponse.TV_CREDITS,
                                )
                            )
                        } catch (e: Throwable) {
                            logger.error("Extended provider data query failed", e)
                            return@get call.respond(InternalServerError)
                        }
                        val tvShow = tmdbSeries.asTvShow(mediaId, 1)
                        queries.updateTvShow(tvShow.copy(added = showResult.added))
                        return@get call.respond(
                            MediaLookupResponse(
                                tvShow = TvShowResponse(tvShow, emptyList())
                            )
                        )
                    }
                    seasonResult != null -> {
                        val tvShow =
                            checkNotNull(queries.findTvShowBySeasonId(seasonResult.id))
                        val season = try {
                            tmdb.showSeasons.getDetails(
                                tvShow.tmdbId,
                                seasonResult.seasonNumber,
                                null,
                                listOf(AppendResponse.IMAGES),
                            ).asTvSeason(seasonResult.id)
                        } catch (e: Throwable) {
                            logger.error("Extended provider data query failed", e)
                            return@get call.respond(InternalServerError)
                        }
                        queries.updateTvSeason(season)
                        val episodes = queries.findEpisodesBySeason(season.id)
                        val episodeIds = episodes.map(Episode::id)
                        val mediaRefs = queries.findMediaRefsByRootContentId(tvShow.id)
                            .filter { it.contentId in episodeIds }
                            .associateBy(MediaReference::contentId)
                        return@get call.respond(
                            MediaLookupResponse(
                                season = SeasonResponse(tvShow, season, episodes, mediaRefs)
                            )
                        )
                    }
                    episodeResult != null -> {
                        val show = checkNotNull(queries.findTvShowById(episodeResult.showId))
                        val episode = try {
                            tmdb.showEpisodes.getDetails(
                                show.tmdbId,
                                episodeResult.seasonNumber,
                                episodeResult.number,
                                null,
                            ).episodes
                                .orEmpty()
                                .first()
                                .asTvEpisode(episodeResult.id, show.id, episodeResult.seasonId)
                        } catch (e: Throwable) {
                            logger.error("Extended provider data query failed", e)
                            return@get call.respond(InternalServerError)
                        }
                        queries.updateTvEpisode(episode)
                        val tvShow = checkNotNull(queries.findTvShowById(episode.showId))
                        val mediaRefs = queries.findMediaRefsByContentId(episode.id)
                        return@get call.respond(
                            MediaLookupResponse(
                                episode = EpisodeResponse(episode, tvShow, mediaRefs)
                            )
                        )
                    }
                }
            }
        }

        route("/refs") {
            get {
                call.respond(queries.findAllMediaRefs())
            }
            get("/{ref_id}") {
                val refId = call.parameters["ref_id"] ?: ""
                val ref = queries.findMediaRefById(refId)
                if (ref == null) {
                    call.respond(NotFound)
                } else {
                    call.respond(ref)
                }
            }
        }

        get("/list-files") {
            val root = call.parameters["root"]
            val showFiles = call.parameters["showFiles"]?.toBoolean() ?: false
            val folders: List<String>
            var files = emptyList<String>()
            if (root.isNullOrBlank()) {
                val (foldersList, filesList) = File.listRoots().orEmpty().partition(File::isDirectory)
                folders = foldersList.map(File::getAbsolutePath)
                files = filesList.map(File::getAbsolutePath)
            } else {
                val rootDir = FileSystems.getDefault().getPath(root)
                if (!rootDir.exists()) {
                    return@get call.respond(NotFound)
                }
                if (rootDir.isDirectory()) {
                    val (folderPaths, filePaths) = withContext(Dispatchers.IO) {
                        Files.newDirectoryStream(rootDir).use { stream ->
                            stream.partition { it.isDirectory() }
                        }
                    }
                    folders = folderPaths.map { it.absolutePathString() }
                    files = filePaths.map { it.absolutePathString() }
                } else {
                    folders = listOf(rootDir.absolutePathString())
                }
            }
            call.respond(ListFilesResponse(folders, if (showFiles) files else emptyList()))
        }

        post("/import") {
            val session = call.principal<UserSession>()!!
            val import = call.receiveOrNull<ImportMedia>()
                ?: return@post call.respond(UnprocessableEntity)
            val importAll = call.parameters["importAll"]?.toBoolean() ?: false

            if (importAll) {
                val results = importer.importAll(session.userId, import).toList()
                val mediaRefIds = results.flatMap { result ->
                    (result as? ImportMediaResult.Success)?.let { success ->
                        val nestedResults = success.subresults
                            .filterIsInstance<ImportMediaResult.Success>()
                            .flatMap(ImportMediaResult.Success::subresults)
                        (nestedResults + success.subresults + result)
                            .filterIsInstance<ImportMediaResult.Success>()
                            .map { it.mediaReference.id }
                    }.orEmpty()
                }
                if (mediaRefIds.isNotEmpty()) {
                    application.launch {
                        importer.importStreamDetails(mediaRefIds)
                    }
                }
                call.respond(results)
            } else {
                val result = importer.import(session.userId, import)
                (result as? ImportMediaResult.Success)?.also { success ->
                    val nestedResults = success.subresults
                        .filterIsInstance<ImportMediaResult.Success>()
                        .flatMap(ImportMediaResult.Success::subresults)
                    val mediaRefIds = (nestedResults + success.subresults + success)
                        .filterIsInstance<ImportMediaResult.Success>()
                        .map { it.mediaReference.id }
                    application.launch {
                        importer.importStreamDetails(mediaRefIds)
                    }
                }
                call.respond(result)
            }
        }

        post("/unmapped") {
            val import = call.receiveOrNull<ImportMedia>()
                ?: return@post call.respond(UnprocessableEntity)

            call.respond(importer.findUnmappedFiles(import))
        }

        route("/tmdb") {
            route("/{tmdb_id}") {
                get("/sources") {
                    val tmdbId = call.parameters["tmdb_id"]?.toIntOrNull()

                    if (tmdbId == null) {
                        call.respond(NotFound)
                    } else {
                        runCatching {

                            tmdb.movies.getDetails(tmdbId, null)
                        }.onSuccess { tmdbMovie ->
                            call.respond(
                                torrentSearch.search {
                                    category = Category.MOVIES
                                    limit = 100
                                    if (tmdbMovie.imdbId == null) {
                                        content = tmdbMovie.title
                                    } else {
                                        imdbId = tmdbMovie.imdbId
                                    }
                                    // TODO: API or client sort+filter
                                }.torrents()
                                    .toList()
                                    .sortedByDescending { it.seeds }
                            )
                        }.onFailure { e ->
                            logger.error("Error fetching movie from TMDB - tmdbId=$tmdbId", e)
                            call.respond(InternalServerError)
                        }
                    }
                }
            }
        }

        route("/movie/{movie_id}") {
            get("/sources") {
                /*val movieId = call.parameters["movie_id"] ?: ""

                val movie = moviesDb.findOneById(movieId)
                if (movie == null) {
                    call.respond(NotFound)
                } else {
                    call.respond(
                        torrentSearch.search(movie.title, Category.MOVIES, 100)
                            // TODO: API or client sort+filter
                            .sortedByDescending { it.seeds }
                    )
                }*/
                TODO()
            }
        }
    }
}

fun Route.addMediaViewRoutes(
    metadataManager: MetadataManager,
    queries: MediaDbQueries,
) {
    route("/media") {
        route("/{mediaId}") {
            get {
                val session = checkNotNull(call.principal<UserSession>())
                val mediaId = call.parameters["mediaId"]
                    ?: return@get call.respond(NotFound)
                val includeRefs = call.parameters["includeRefs"]?.toBoolean() ?: true
                val includePlaybackState =
                    call.parameters["includePlaybackStates"]?.toBoolean() ?: true
                val playbackStateUserId = if (includePlaybackState) session.userId else null

                return@get if (mediaId.isRemoteId) {
                    when (val queryResult = metadataManager.findByRemoteId(mediaId)) {
                        is QueryMetadataResult.Success -> {
                            if (queryResult.results.isEmpty()) {
                                return@get call.respond(MediaLookupResponse())
                            }
                            val response = when (val match = queryResult.results.first()) {
                                is MetadataMatch.MovieMatch -> {
                                    MediaLookupResponse(movie = MovieResponse(match.movie))
                                }
                                is MetadataMatch.TvShowMatch -> {
                                    val tvExtras = queryResult.extras?.asTvShowExtras()
                                    when {
                                        tvExtras?.episodeNumber != null -> MediaLookupResponse(
                                            episode = EpisodeResponse(match.episodes.first(), match.tvShow)
                                        )
                                        tvExtras?.seasonNumber != null -> MediaLookupResponse(
                                            season = SeasonResponse(match.tvShow, match.seasons.first(), match.episodes)
                                        )
                                        else -> MediaLookupResponse(
                                            tvShow = TvShowResponse(match.tvShow, match.seasons)
                                        )
                                    }
                                }
                            }
                            call.respond(response)
                        }
                        else -> call.respond(MediaLookupResponse())
                    }
                } else {
                    call.respond(
                        queries.findMediaById(
                            mediaId,
                            includeRefs = includeRefs,
                            includePlaybackStateForUser = playbackStateUserId,
                        )
                    )
                }
            }
        }

        get("/by-ref/{refId}") {
            val refId = call.parameters["refId"]
                ?: return@get call.respond(MediaLookupResponse())
            val includeRefs = call.parameters["includeRefs"]?.toBoolean() ?: false

            val mediaId = queries.findMediaIdByRefId(refId)
                ?: return@get call.respond(MediaLookupResponse())

            call.respond(
                MediaLookupResponse(
                    movie = queries.findMovieById(mediaId, includeRefs = includeRefs),
                    tvShow = queries.findShowById(mediaId, includeRefs = includeRefs),
                    episode = queries.findEpisodeById(mediaId, includeRefs = includeRefs),
                    season = queries.findSeasonById(mediaId, includeRefs = includeRefs),
                )
            )
        }
    }
}
