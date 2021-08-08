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
package anystream.frontend.screens

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.frontend.components.PosterCard
import anystream.models.api.HomeResponse
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun HomeScreen(client: AnyStreamClient) {
    val homeResponse by produceState<HomeResponse?>(null) {
        value = client.getHomeData()
    }

    homeResponse?.run {
        if (currentlyWatching.isNotEmpty()) {
            MovieRow(
                title = { Text("Continue Watching") }
            ) {
                currentlyWatching.forEach { (movie, state) ->
                    PosterCard(
                        title = movie.title,
                        posterPath = movie.posterPath,
                        overview = movie.overview,
                        releaseDate = movie.releaseDate,
                        isAdded = true,
                        onBodyClicked = {
                            BrowserRouter.navigate("/media/${movie.id}")
                        }
                    )
                }
            }
        }

        if (popularMovies.isNotEmpty()) {
            MovieRow(
                title = { Text("Popular") }
            ) {
                popularMovies.forEach { (movie, ref) ->
                    PosterCard(
                        title = movie.title,
                        posterPath = movie.posterPath,
                        overview = movie.overview,
                        releaseDate = movie.releaseDate,
                        isAdded = ref != null,
                        onBodyClicked = {
                            if (ref == null) {
                                BrowserRouter.navigate("/media/tmdb:${movie.tmdbId}")
                            } else {
                                BrowserRouter.navigate("/media/${ref.contentId}")
                            }
                        }
                    )
                }
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            MovieRow(
                title = { Text("Recently Added Movies") }
            ) {
                recentlyAdded.forEach { (movie, ref) ->
                    PosterCard(
                        title = movie.title,
                        posterPath = movie.posterPath,
                        overview = movie.overview,
                        releaseDate = movie.releaseDate,
                        isAdded = true,
                        onBodyClicked = {
                            BrowserRouter.navigate("/media/${movie.id}")
                        }
                    )
                }
            }
        }

        if (recentlyAddedTv.isNotEmpty()) {
            MovieRow(
                title = { Text("Recently Added TV") }
            ) {
                recentlyAddedTv.forEach { show ->
                    PosterCard(
                        title = show.name,
                        posterPath = show.posterPath,
                        overview = show.overview,
                        releaseDate = show.firstAirDate,
                        isAdded = true,
                        onBodyClicked = {
                            BrowserRouter.navigate("/media/${show.id}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieRow(
    title: @Composable () -> Unit,
    buildItems: @Composable () -> Unit,
) {
    Div {
        H4({
            classes("px-3", "pt-3", "pb-1")
        }) {
            title()
        }
    }
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            property("overflow-x", "auto")
            property("scrollbar-width", "none")
        }
    }) {
        buildItems()
    }
}
