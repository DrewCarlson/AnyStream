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
package anystream.routes

import anystream.data.*
import anystream.media.MediaImporter
import anystream.metadata.MetadataManager
import anystream.models.*
import anystream.models.api.*
import anystream.util.isRemoteId
import anystream.util.logger
import drewcarlson.torrentsearch.Category
import drewcarlson.torrentsearch.TorrentSearch
import info.movito.themoviedbapi.TmdbApi
import info.movito.themoviedbapi.TmdbMovies
import info.movito.themoviedbapi.TmdbTV
import info.movito.themoviedbapi.TmdbTvSeasons
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.projection

fun Route.addMediaManageRoutes(
    tmdb: TmdbApi,
    mongodb: CoroutineDatabase,
    torrentSearch: TorrentSearch,
    importer: MediaImporter,
    queries: MediaDbQueries,
) {
    val moviesDb = mongodb.getCollection<Movie>()
    val mediaRefsDb = mongodb.getCollection<MediaReference>()
    route("/media") {
        route("/{mediaId}") {
            get("/refresh-stream-details") {
                val mediaId = call.parameters["mediaId"] ?: ""
                val mediaRefIds = mediaRefsDb.projection(
                    MediaReference::id,
                    or(
                        MediaReference::contentId eq mediaId,
                        MediaReference::rootContentId eq mediaId,
                    )
                ).toList()
                call.respond(importer.importStreamDetails(mediaRefIds))
            }
            get("/refresh-metadata") {
                val mediaId = call.parameters["mediaId"] ?: ""
                val session = checkNotNull(call.principal<UserSession>())
                val result = queries.findMediaById(mediaId)

                if (!result.hasResult()) {
                    logger.warn("No media found for $mediaId")
                    return@get call.respond(MediaLookupResponse())
                }
                when {
                    result.movie != null -> {
                        val movie = try {
                            tmdb.movies.getMovie(
                                result.movie.tmdbId,
                                null,
                                TmdbMovies.MovieMethod.images,
                                TmdbMovies.MovieMethod.release_dates,
                                TmdbMovies.MovieMethod.alternative_titles,
                                TmdbMovies.MovieMethod.keywords
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
                    result.tvShow != null -> {
                        val tmdbSeries = try {
                            tmdb.tvSeries.getSeries(
                                result.tvShow.tmdbId,
                                "en",
                                TmdbTV.TvMethod.keywords,
                                TmdbTV.TvMethod.external_ids,
                                TmdbTV.TvMethod.images,
                                TmdbTV.TvMethod.content_ratings,
                                TmdbTV.TvMethod.credits,
                            )
                        } catch (e: Throwable) {
                            logger.error("Extended provider data query failed", e)
                            return@get call.respond(InternalServerError)
                        }
                        val tvShow = tmdbSeries.asTvShow(result.tvShow.seasons, mediaId, "")
                        queries.updateTvShow(tvShow.copy(added = result.tvShow.added))
                        return@get call.respond(
                            MediaLookupResponse(
                                tvShow = TvShowResponse(tvShow, emptyList())
                            )
                        )
                    }
                    result.season != null -> {
                        val tvShow =
                            checkNotNull(queries.findTvShowBySeasonId(result.season.id))
                        val season = try {
                            tmdb.tvSeasons.getSeason(
                                tvShow.tmdbId,
                                result.season.seasonNumber,
                                null,
                                TmdbTvSeasons.SeasonMethod.images,
                            ).asTvSeason(result.season.id, "")
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
                    result.episode != null -> {
                        val show = checkNotNull(queries.findTvShowById(result.episode.showId))
                        val episode = try {
                            tmdb.tvEpisodes.getEpisode(
                                show.tmdbId,
                                result.episode.seasonNumber,
                                result.episode.number,
                                null,
                            ).asTvEpisode(result.episode.id, show.id, "")
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
                call.respond(mediaRefsDb.find().toList())
            }
            get("/{ref_id}") {
                val refId = call.parameters["ref_id"] ?: ""
                val ref = mediaRefsDb.findOneById(refId)
                if (ref == null) {
                    call.respond(NotFound)
                } else {
                    call.respond(ref)
                }
            }
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
            val session = call.principal<UserSession>()!!
            val import = call.receiveOrNull<ImportMedia>()
                ?: return@post call.respond(UnprocessableEntity)

            call.respond(importer.findUnmappedFiles(session.userId, import))
        }

        route("/tmdb") {
            route("/{tmdb_id}") {
                get("/sources") {
                    val tmdbId = call.parameters["tmdb_id"]?.toIntOrNull()

                    if (tmdbId == null) {
                        call.respond(NotFound)
                    } else {
                        runCatching {
                            tmdb.movies.getMovie(tmdbId, null)
                        }.onSuccess { tmdbMovie ->
                            call.respond(
                                torrentSearch.search(tmdbMovie.title, Category.MOVIES, 100)
                                    // TODO: API or client sort+filter
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
                val movieId = call.parameters["movie_id"] ?: ""

                val movie = moviesDb.findOneById(movieId)
                if (movie == null) {
                    call.respond(NotFound)
                } else {
                    call.respond(
                        torrentSearch.search(movie.title, Category.MOVIES, 100)
                            // TODO: API or client sort+filter
                            .sortedByDescending { it.seeds }
                    )
                }
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
                            val match = queryResult.results.first()
                            call.respond(
                                MediaLookupResponse(
                                    movie = (match as? MetadataMatch.MovieMatch)
                                        ?.run { MovieResponse(movie) },
                                    tvShow = (match as? MetadataMatch.TvShowMatch)
                                        ?.run { TvShowResponse(tvShow) },
                                )
                            )
                        }
                        else -> call.respond(MediaLookupResponse())
                    }
                } else {
                    call.respond(
                        MediaLookupResponse(
                            movie = queries.findMovieById(
                                mediaId,
                                includeRefs = includeRefs,
                                includePlaybackStateForUser = playbackStateUserId,
                            ),
                            tvShow = queries.findShowById(
                                mediaId,
                                includeRefs = includeRefs,
                                includePlaybackStateForUser = playbackStateUserId,
                            ),
                            episode = queries.findEpisodeById(
                                mediaId,
                                includeRefs = includeRefs,
                                includePlaybackStateForUser = playbackStateUserId,
                            ),
                            season = queries.findSeasonById(
                                mediaId,
                                includeRefs = includeRefs,
                                includePlaybackStateForUser = playbackStateUserId,
                            ),
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