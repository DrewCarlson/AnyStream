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
package anystream.screens

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.components.*
import anystream.models.MediaLink
import anystream.models.Movie
import anystream.models.api.MoviesResponse
import anystream.playerMediaLinkId
import anystream.util.get
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun MoviesScreen() {
    val client = get<AnyStreamClient>()
    val moviesResponse by produceState<MoviesResponse?>(null) {
        value = client.getMovies()
    }

    when (val response = moviesResponse) {
        null -> FullSizeCenteredLoader()
        else -> {
            val (movies, mediaLinks) = response
            if (movies.isEmpty()) {
                Div({ classes("d-flex", "justify-content-center", "align-items-center", "h-100") }) {
                    Text("Movies will appear here.")
                }
            } else {
                val router = Router.current
                VerticalGridScroller(movies) { movie ->
                    MovieCard(router, movie, mediaLinks[movie.id])
                }
            }
        }
    }
}

@Composable
private fun MovieCard(
    router: Router,
    movie: Movie,
    link: MediaLink?,
) {
    PosterCard(
        title = {
            LinkedText("/media/${movie.id}", router) {
                Text(movie.title)
            }
        },
        metadataId = movie.id,
        isAdded = true,
        onPlayClicked = {
            playerMediaLinkId.value = link?.id
        }.takeIf { link != null },
        onBodyClicked = {
            router.navigate("/media/${movie.id}")
        },
    )
}
