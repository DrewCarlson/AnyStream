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
import anystream.LocalAnyStreamClient
import anystream.components.FullSizeCenteredLoader
import anystream.components.LinkedText
import anystream.components.PosterCard
import anystream.models.api.HomeResponse
import app.softwork.routingcompose.Router
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.max
import org.jetbrains.compose.web.attributes.min
import org.jetbrains.compose.web.attributes.step
import org.jetbrains.compose.web.css.overflow
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.*

private const val KEY_POSTER_SIZE_MULTIPLIER = "key_poster_size_multiplier"

@Composable
fun HomeScreen() {
    val client = LocalAnyStreamClient.current
    val homeResponse by produceState<HomeResponse?>(null) {
        var i = 0
        while (value == null) {
            i = (i + 1).coerceAtMost(3)
            value = try {
                client.getHomeData()
            } catch (e: Throwable) {
                null
            }
            delay(i * 5000L)
        }
    }
    var sizeMultiplier by remember {
        mutableStateOf(localStorage.getItem(KEY_POSTER_SIZE_MULTIPLIER)?.toFloatOrNull() ?: 1f)
    }

    if (homeResponse == null) {
        FullSizeCenteredLoader()
    }

    homeResponse?.run {
        Div({ classes("d-flex", "justify-content-between", "align-items-center", "p-3") }) {
            Div { H4 { Text("Home") } }
            PosterSizeSelector(sizeMultiplier) {
                sizeMultiplier = it
                localStorage.setItem(KEY_POSTER_SIZE_MULTIPLIER, it.toString())
            }
        }

        Div({
            classes("vh-100")
            style {
                overflow("hidden auto")
            }
        }) {
            if (playbackStates.isNotEmpty()) {
                ContinueWatchingRow(sizeMultiplier)
            }

            if (recentlyAdded.isNotEmpty()) {
                RecentlyAddedMovies(sizeMultiplier)
            }

            if (recentlyAddedTv.isNotEmpty()) {
                RecentlyAddedTv(sizeMultiplier)
            }

            if (popularMovies.isNotEmpty()) {
                PopularMovies(sizeMultiplier)
            }

            if (popularTvShows.isNotEmpty()) {
                PopularTvShows(sizeMultiplier)
            }
        }
    }
}

@Composable
private fun HomeResponse.ContinueWatchingRow(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Continue Watching") }) {
        playbackStates.forEach { state ->
            val movie = currentlyWatchingMovies[state.id]
            val (episode, show) = currentlyWatchingTv[state.id] ?: (null to null)
            PosterCard(
                sizeMultiplier = sizeMultiplier,
                title = (movie?.title ?: show?.name)?.let { title ->
                    {
                        LinkedText(url = "/media/${movie?.gid ?: show?.gid}") {
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
                        LinkedText(url = "/media/${episode.gid}") {
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
                                    .run { "/media/$gid" }
                            ) { Text("S$seasonNumber") }
                            Div { Text(" · ") }
                            LinkedText(url = "/media/${episode.gid}") {
                                Text("E$number")
                            }
                        }
                    }
                },
                posterPath = movie?.posterPath ?: show?.posterPath,
                isAdded = true,
                onPlayClicked = {
                    window.location.hash = "!play:${state.mediaLinkGid}"
                },
                onBodyClicked = {
                    router.navigate("/media/${movie?.gid ?: episode?.gid}")
                }
            )
        }
    }
}

@Composable
private fun HomeResponse.RecentlyAddedMovies(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Recently Added Movies") }) {
        recentlyAdded.forEach { (movie, mediaLink) ->
            PosterCard(
                sizeMultiplier = sizeMultiplier,
                title = {
                    LinkedText(url = "/media/${movie.gid}") {
                        Text(movie.title)
                    }
                },
                subtitle1 = movie.releaseDate?.run {
                    { Text(substringBefore("-")) }
                },
                posterPath = movie.posterPath,
                isAdded = true,
                onPlayClicked = {
                    window.location.hash = "!play:${mediaLink?.gid}"
                }.takeIf { mediaLink != null },
                onBodyClicked = {
                    router.navigate("/media/${movie.gid}")
                }
            )
        }
    }
}

@Composable
private fun HomeResponse.RecentlyAddedTv(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Recently Added TV") }) {
        recentlyAddedTv.forEach { show ->
            PosterCard(
                sizeMultiplier = sizeMultiplier,
                title = {
                    LinkedText(url = "/media/${show.gid}") {
                        Text(show.name)
                    }
                },
                posterPath = show.posterPath,
                isAdded = true,
                onBodyClicked = {
                    router.navigate("/media/${show.gid}")
                }
            )
        }
    }
}

@Composable
private fun HomeResponse.PopularMovies(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Popular Movies") }) {
        popularMovies.forEach { (movie, link) ->
            PosterCard(
                sizeMultiplier = sizeMultiplier,
                title = {
                    LinkedText(url = "/media/${link?.metadataGid ?: movie.gid}") {
                        Text(movie.title)
                    }
                },
                subtitle1 = movie.releaseDate?.run {
                    { Text(substringBefore("-")) }
                },
                posterPath = movie.posterPath,
                isAdded = link != null,
                onPlayClicked = { window.location.hash = "!play:${link?.gid}" }
                    .takeIf { link != null },
                onBodyClicked = {
                    router.navigate("/media/${movie.gid}")
                }
            )
        }
    }
}

@Composable
private fun HomeResponse.PopularTvShows(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Popular TV") }) {
        popularTvShows.forEach { tvShow ->
            PosterCard(
                sizeMultiplier = sizeMultiplier,
                title = {
                    LinkedText(url = "/media/${tvShow.gid}") {
                        Text(tvShow.name)
                    }
                },
                subtitle1 = tvShow.firstAirDate?.run {
                    { Text(substringBefore("-")) }
                },
                posterPath = tvShow.posterPath,
                isAdded = tvShow.isAdded,
                /*onPlayClicked = { window.location.hash = "!play:${ref?.id}" }
                    .takeIf { ref != null },*/
                onBodyClicked = {
                    router.navigate("/media/${tvShow.gid}")
                }
            )
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

@Composable
private fun PosterSizeSelector(sizeMultiplier: Float, onInput: (Float) -> Unit) {
    Div({
        classes("d-flex", "align-items-center", "gap-2")
        style { width(120.px) }
    }) {
        Input(InputType.Range) {
            classes("form-range")
            value(sizeMultiplier)
            min("0.8")
            max("1.2")
            step(0.01)
            onInput {
                it.value?.toFloat()?.takeUnless(Float::isNaN)?.run(onInput)
            }
        }
        I({ classes("bi", "bi-grid-3x3-gap-fill") })
    }
}
