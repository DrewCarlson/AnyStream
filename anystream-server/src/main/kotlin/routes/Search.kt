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

import anystream.models.*
import anystream.models.api.SearchResponse
import com.mongodb.MongoException
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import info.movito.themoviedbapi.TmdbApi
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.CoroutineFindPublisher

private const val DEFAULT_SEARCH_RESULT_LIMIT = 3
private const val MAX_SEARCH_RESULT_LIMIT = 3
private const val QUERY = "query"
private const val LIMIT = "limit"
private const val MEDIA_KIND = "mediaKind"

fun Route.addSearchRoutes(
    tmdb: TmdbApi,
    mongodb: CoroutineDatabase,
) {
    val moviesDb = mongodb.getCollection<Movie>()
    val tvShowDb = mongodb.getCollection<TvShow>()
    val episodeDb = mongodb.getCollection<Episode>()
    val mediaRefsDb = mongodb.getCollection<MediaReference>()
    route("/search") {
        get {
            val query = call.parameters[QUERY] ?: return@get call.respond(SearchResponse())
            val limit = (call.parameters[LIMIT]?.toIntOrNull() ?: DEFAULT_SEARCH_RESULT_LIMIT)
                .coerceIn(1, MAX_SEARCH_RESULT_LIMIT)
            val mediaKind = call.parameters[MEDIA_KIND]?.uppercase()?.run(MediaKind::valueOf)

            fun <T : Any> CoroutineCollection<T>.textSearch(): CoroutineFindPublisher<T> =
                find(text(query))
                    .limit(limit)
                    .projection(Projections.metaTextScore("score"))
                    .sort(Sorts.metaTextScore("score"))
                    .projection(Projections.exclude("score"))

            val movies = try {
                moviesDb
                    .textSearch()
                    .toList()
            } catch (e: MongoException) {
                e.printStackTrace()
                emptyList()
            }

            val tvShows = try {
                tvShowDb.textSearch().toList()
            } catch (e: MongoException) {
                e.printStackTrace()
                emptyList()
            }

            val episodes = try {
                episodeDb.textSearch().toList()
            } catch (e: MongoException) {
                e.printStackTrace()
                emptyList()
            }

            val searchIds = movies.map(Movie::id) + episodes.map(Episode::id)

            val mediaRefs = mediaRefsDb
                .find(MediaReference::contentId `in` searchIds)
                .toList()
                .associateBy(MediaReference::contentId)

            call.respond(
                SearchResponse(
                    movies = movies,
                    tvShows = tvShows,
                    episodes = episodes,
                    mediaReferences = mediaRefs,
                )
            )
        }
    }
}