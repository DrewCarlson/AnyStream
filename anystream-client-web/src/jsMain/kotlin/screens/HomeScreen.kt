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
import anystream.client.AnyStreamClient
import anystream.frontend.components.FullSizeCenteredLoader
import anystream.frontend.components.LinkedText
import anystream.frontend.components.PosterCard
import anystream.models.api.HomeResponse
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun HomeScreen(client: AnyStreamClient) {
    val homeResponse by produceState<HomeResponse?>(null) {
        value = client.getHomeData()
    }

    if (homeResponse == null) {
        FullSizeCenteredLoader()
    }

    homeResponse?.run {
        if (playbackStates.isNotEmpty()) {
            MovieRow(
                title = { Text("Continue Watching") }
            ) {
                playbackStates.forEach { state ->
                    val movie = currentlyWatchingMovies[state.id]
                    val (episode, show) = currentlyWatchingTv[state.id] ?: (null to null)
                    PosterCard(
                        title = (movie?.title ?: show?.name)?.let { title ->
                            {
                                LinkedText(url = "/media/${movie?.id ?: show?.id}") {
                                    Text(title)
                                }
                            }
                        },
                        completedPercent = state.completedPercent,
                        subtitle1 = {
                            movie?.releaseDate?.run {
                                Text(substringBefore("-"))
                            }
                            episode?.run {
                                LinkedText(url = "/media/${episode.id}") {
                                    Text(name)
                                }
                            }
                        },
                        subtitle2 = {
                            episode?.run {
                                "S$seasonNumber"
                                Div({ classes("d-flex", "flex-row") }) {
                                    tvSeasons
                                    LinkedText(
                                        tvSeasons
                                            .first { it.seasonNumber == seasonNumber }
                                            .run { "/media/$id" }
                                    ) { Text("S$seasonNumber") }
                                    Div { Text(" · ") }
                                    LinkedText(url = "/media/${episode.id}") {
                                        Text("E$number")
                                    }
                                }
                            }
                        },
                        posterPath = movie?.posterPath ?: show?.posterPath,
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

        if (recentlyAdded.isNotEmpty()) {
            MovieRow(
                title = { Text("Recently Added Movies") }
            ) {
                recentlyAdded.forEach { (movie, ref) ->
                    PosterCard(
                        title = {
                            LinkedText(url = "/media/${movie.id}") {
                                Text(movie.title)
                            }
                        },
                        subtitle1 = movie.releaseDate?.run {
                            { Text(substringBefore("-")) }
                        },
                        posterPath = movie.posterPath,
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
                        title = {
                            LinkedText(url = "/media/${show.id}") {
                                Text(show.name)
                            }
                        },
                        posterPath = show.posterPath,
                        isAdded = true,
                        onBodyClicked = {
                            BrowserRouter.navigate("/media/${show.id}")
                        }
                    )
                }
            }
        }

        if (popularMovies.isNotEmpty()) {
            MovieRow(
                title = { Text("Popular Movies") }
            ) {
                popularMovies.forEach { (movie, ref) ->
                    PosterCard(
                        title = {
                            LinkedText(url = "/media/${ref?.contentId ?: movie.id}") {
                                Text(movie.title)
                            }
                        },
                        subtitle1 = movie.releaseDate?.run {
                            { Text(substringBefore("-")) }
                        },
                        posterPath = movie.posterPath,
                        isAdded = ref != null,
                        onPlayClicked = { window.location.hash = "!play:${ref?.id}" }
                            .takeIf { ref != null },
                        onBodyClicked = {
                            BrowserRouter.navigate("/media/${movie.id}")
                        }
                    )
                }
            }
        }

        if (popularTvShows.isNotEmpty()) {
            MovieRow(
                title = { Text("Popular TV") }
            ) {
                popularTvShows.forEach { tvShow ->
                    PosterCard(
                        title = {
                            LinkedText(url = "/media/${tvShow.id}") {
                                Text(tvShow.name)
                            }
                        },
                        subtitle1 = tvShow.firstAirDate.run {
                            { Text(substringBefore("-")) }
                        },
                        posterPath = tvShow.posterPath,
                        isAdded = tvShow.isAdded,
                        /*onPlayClicked = { window.location.hash = "!play:${ref?.id}" }
                            .takeIf { ref != null },*/
                        onBodyClicked = {
                            BrowserRouter.navigate("/media/${tvShow.id}")
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
        classes("d-flex", "flex-row")
        style {
            property("overflow-x", "auto")
            property("scrollbar-width", "none")
        }
    }) {
        buildItems()
    }
}
