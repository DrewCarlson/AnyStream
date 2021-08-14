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
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun HomeScreen(client: AnyStreamClient) {
    val homeResponse by produceState<HomeResponse?>(null) {
        value = client.getHomeData()
    }

    homeResponse?.run {
        if (playbackStates.isNotEmpty()) {
            MovieRow(
                title = { Text("Continue Watching") }
            ) {
                playbackStates.forEach { state ->
                    val movie = currentlyWatchingMovies[state.id]
                    val (episode, show) = currentlyWatchingTv[state.id] ?: null to null
                    PosterCard(
                        mediaId = movie?.id ?: episode?.id ?: "",
                        title = movie?.title ?: show?.name ?: "",
                        subtitle1 = movie?.releaseDate?.substringBefore("-") ?: episode?.name,
                        subtitle2 = episode?.run { "S$seasonNumber · E$number" },
                        posterPath = movie?.posterPath ?: show?.posterPath,
                        overview = movie?.overview ?: episode?.overview ?: show?.overview ?: "",
                        releaseDate = movie?.releaseDate ?: episode?.airDate ?: "",
                        isAdded = true,
                        onPlayClicked = {
                            window.location.hash = "!play:${state.mediaReferenceId}"
                        },
                        onBodyClicked = {
                            BrowserRouter.navigate("/media/${movie?.id ?: episode?.id}")
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
                        mediaId = "tmdb:${movie.tmdbId}",
                        title = movie.title,
                        subtitle1 = movie.releaseDate?.substringBefore("-"),
                        posterPath = movie.posterPath,
                        overview = movie.overview,
                        releaseDate = movie.releaseDate,
                        isAdded = ref != null,
                        onPlayClicked = { window.location.hash = "!play:${ref?.id}" }
                            .takeIf { ref != null },
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
                        mediaId = movie.id,
                        title = movie.title,
                        subtitle1 = movie.releaseDate?.substringBefore("-"),
                        posterPath = movie.posterPath,
                        overview = movie.overview,
                        releaseDate = movie.releaseDate,
                        isAdded = true,
                        onPlayClicked = {
                            window.location.hash = "!play:${ref?.id}"
                        }.takeIf { ref != null },
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
                        mediaId = show.id,
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
