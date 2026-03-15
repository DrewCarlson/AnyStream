/*
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

import anystream.di.ServerScope
import anystream.models.Permission
import anystream.models.api.SearchResponse
import anystream.service.search.SearchService
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.server.auth.authenticate
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.drewcarlson.ktor.permissions.withAnyPermission

private const val DEFAULT_SEARCH_RESULT_LIMIT = 3
private const val MAX_SEARCH_RESULT_LIMIT = 3
private const val QUERY = "query"
private const val LIMIT = "limit"
private const val MEDIA_KIND = "mediaKind"

@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<RoutingController>(),
)
@Inject
class SearchRoutes(
    private val searchService: SearchService,
) : RoutingController {
    override fun init(parent: Route) {
        parent.authenticate {
            withAnyPermission(Permission.ViewCollection) {
                get("/search") {
                    getSearchResults()
                }
            }
        }
    }

    suspend fun RoutingContext.getSearchResults() {
        val query = call.parameters[QUERY] ?: return call.respond(SearchResponse())
        val limit = (call.parameters[LIMIT]?.toIntOrNull() ?: DEFAULT_SEARCH_RESULT_LIMIT)
            .coerceIn(1, MAX_SEARCH_RESULT_LIMIT)
        // val mediaKind = call.parameters[MEDIA_KIND]?.uppercase()?.run(MediaKind::valueOf)

        call.respond(searchService.search(query, limit))
    }
}
