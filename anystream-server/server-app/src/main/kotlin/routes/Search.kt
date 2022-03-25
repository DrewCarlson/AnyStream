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

import anystream.models.api.SearchResponse
import anystream.service.search.SearchService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val DEFAULT_SEARCH_RESULT_LIMIT = 3
private const val MAX_SEARCH_RESULT_LIMIT = 3
private const val QUERY = "query"
private const val LIMIT = "limit"
private const val MEDIA_KIND = "mediaKind"

fun Route.addSearchRoutes(
    searchService: SearchService
) {
    route("/search") {
        get {
            val query = call.parameters[QUERY] ?: return@get call.respond(SearchResponse())
            val limit = (call.parameters[LIMIT]?.toIntOrNull() ?: DEFAULT_SEARCH_RESULT_LIMIT)
                .coerceIn(1, MAX_SEARCH_RESULT_LIMIT)
            // val mediaKind = call.parameters[MEDIA_KIND]?.uppercase()?.run(MediaKind::valueOf)

            call.respond(searchService.search(query, limit))
        }
    }
}
