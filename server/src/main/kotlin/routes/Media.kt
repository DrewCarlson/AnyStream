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
import anystream.models.*
import anystream.models.api.*
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
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase

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
            route("/metadata") {
                get("/refresh") {
                    val mediaId = call.parameters["mediaId"] ?: ""
                    val session = checkNotNull(call.principal<UserSession>())
                    val result = queries.findMediaById(mediaId)

                    if (!result.hasResult()) {
                        logger.warn("No media found for $mediaId")
                        return@get call.respond(NotFound)
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
                call.respond(importer.importAll(session.userId, import).toList())
            } else {
                call.respond(importer.import(session.userId, import))
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
    tmdb: TmdbApi,
    queries: MediaDbQueries,
) {
    route("/media") {
        route("/{mediaId}") {
            get {
                val mediaId = call.parameters["mediaId"]
                    ?: return@get call.respond(NotFound)

                if (mediaId.contains(':')) {
                    val (provider, kind, remoteId) = try {
                        mediaId.split(':')
                    } catch (e: IndexOutOfBoundsException) {
                        return@get call.respond(NotFound)
                    }

                    when (provider.lowercase()) {
                        "tmdb" -> {
                            when (MediaKind.valueOf(kind.uppercase())) {
                                MediaKind.MOVIE -> {
                                    val movie = tmdb.movies.getMovie(
                                        remoteId.toInt(),
                                        null,
                                        TmdbMovies.MovieMethod.images,
                                        TmdbMovies.MovieMethod.release_dates,
                                        TmdbMovies.MovieMethod.alternative_titles,
                                        TmdbMovies.MovieMethod.keywords
                                    ).asMovie(mediaId, "")

                                    call.respond(
                                        MediaLookupResponse(
                                            movie = MovieResponse(movie, emptyList())
                                        )
                                    )
                                }
                                MediaKind.TV -> {
                                    val tmdbSeries = tmdb.tvSeries.getSeries(
                                        remoteId.toInt(),
                                        null,
                                        TmdbTV.TvMethod.keywords,
                                        TmdbTV.TvMethod.external_ids,
                                        TmdbTV.TvMethod.images,
                                        TmdbTV.TvMethod.content_ratings,
                                        TmdbTV.TvMethod.credits,
                                    )
                                    val tmdbSeasons = tmdbSeries.seasons
                                        .filter { it.seasonNumber > 0 }
                                        .map { season ->
                                            tmdb.tvSeasons.getSeason(
                                                tmdbSeries.id,
                                                season.seasonNumber,
                                                null,
                                                TmdbTvSeasons.SeasonMethod.images,
                                            )
                                        }
                                    val (show, _) = tmdbSeries.asTvShow(
                                        tmdbSeasons = tmdbSeasons,
                                        id = mediaId,
                                        userId = ""
                                    ) { id -> "tmdb:tv:$id" }
                                    call.respond(
                                        MediaLookupResponse(
                                            tvShow = TvShowResponse(show, emptyList())
                                        )
                                    )
                                }
                                else -> return@get call.respond(NotFound)
                            }
                        }
                        else -> return@get call.respond(NotFound)
                    }
                } else {
                    call.respond(
                        MediaLookupResponse(
                            movie = queries.findMovieAndMediaRefs(mediaId),
                            tvShow = queries.findShowAndMediaRefs(mediaId),
                            episode = queries.findEpisodeAndMediaRefs(mediaId),
                            season = queries.findSeasonAndMediaRefs(mediaId),
                        )
                    )
                }
            }
        }
    }
}