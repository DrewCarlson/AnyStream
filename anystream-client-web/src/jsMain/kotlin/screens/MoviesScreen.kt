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
package anystream.frontend.screens

import androidx.compose.runtime.*
import anystream.frontend.LocalAnyStreamClient
import anystream.frontend.components.*
import anystream.models.MediaReference
import anystream.models.Movie
import anystream.models.api.MoviesResponse
import app.softwork.routingcompose.Router
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun MoviesScreen() {
    val client = LocalAnyStreamClient.current
    val moviesResponse by produceState<MoviesResponse?>(null) {
        value = client.getMovies()
    }

    when (val response = moviesResponse) {
        null -> FullSizeCenteredLoader()
        else -> {
            val (movies, refs) = response
            if (movies.isEmpty()) {
                Div({ classes("d-flex", "justify-content-center", "align-items-center", "h-100") }) {
                    Text("Movies will appear here.")
                }
            } else {
                val router = Router.current
                VirtualScroller(movies) { movie ->
                    val ref = refs.find { it.contentId == movie.id }
                    MovieCard(router, movie, ref)
                }
            }
        }
    }
}

@Composable
private fun MovieCard(
    router: Router,
    movie: Movie,
    ref: MediaReference?,
) {
    PosterCard(
        title = {
            LinkedText("/media/${movie.id}", router) {
                Text(movie.title)
            }
        },
        posterPath = movie.posterPath,
        isAdded = true,
        onPlayClicked = {
            window.location.hash = "!play:${ref?.id}"
        }.takeIf { ref != null },
        onBodyClicked = {
            router.navigate("/media/${movie.id}")
        },
    )
}
