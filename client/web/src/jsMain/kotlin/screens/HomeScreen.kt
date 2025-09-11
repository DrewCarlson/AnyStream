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
import anystream.components.FullSizeCenteredLoader
import anystream.components.HorizontalScroller
import anystream.components.LinkedText
import anystream.components.PosterCard
import anystream.models.api.CurrentlyWatching
import anystream.models.api.HomeResponse
import anystream.models.api.Popular
import anystream.models.api.RecentlyAdded
import anystream.models.completedPercent
import anystream.playerMediaLinkId
import anystream.util.get
import app.softwork.routingcompose.Router
import kotlinx.browser.localStorage
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.max
import org.jetbrains.compose.web.attributes.min
import org.jetbrains.compose.web.attributes.step
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.overflow
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.vw
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times

private const val KEY_POSTER_SIZE_MULTIPLIER = "key_poster_size_multiplier"

@Composable
fun HomeScreen() {
    val client = get<AnyStreamClient>()
    val homeResponse by produceState<HomeResponse?>(null) {
        var i = 0
        while (value == null) {
            i = (i + 1).coerceAtMost(3)
            value = try {
                client.library.getHomeData()
            } catch (e: Throwable) {
                null
            }
            delay(i * 5.seconds)
        }
    }
    var sizeMultiplier by remember {
        mutableStateOf(localStorage.getItem(KEY_POSTER_SIZE_MULTIPLIER)?.toFloatOrNull() ?: 1f)
    }

    if (homeResponse == null) {
        FullSizeCenteredLoader()
    }

    homeResponse?.run {
        Div({ classes("flex", "justify-content-between", "align-items-center", "p-3") }) {
            Div { H4 { Text("Home") } }
            PosterSizeSelector(sizeMultiplier) {
                sizeMultiplier = it
                localStorage.setItem(KEY_POSTER_SIZE_MULTIPLIER, it.toString())
            }
        }

        Div({
            //classes("w-screen", "h-screen")
            style {
                overflow("hidden auto")
                width(100.vw)
                height(100.vh)
            }
        }) {
            if (currentlyWatching.playbackStates.isNotEmpty()) {
                currentlyWatching.ContinueWatchingRow(sizeMultiplier)
            }

            if (recentlyAdded.movies.isNotEmpty()) {
                recentlyAdded.RecentlyAddedMovies(sizeMultiplier)
            }

            if (recentlyAdded.tvShows.isNotEmpty()) {
                recentlyAdded.RecentlyAddedTv(sizeMultiplier)
            }

            if (popular.movies.isNotEmpty()) {
                popular.PopularMovies(sizeMultiplier)
            }

            if (popular.tvShows.isNotEmpty()) {
                popular.PopularTvShows(sizeMultiplier)
            }
        }
    }
}

@Composable
private fun CurrentlyWatching.ContinueWatchingRow(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Continue Watching") })
    HorizontalScroller(playbackStates, scrollbars = false) { state ->
        val movie = movies[state.id]
        val (episode, show) = tvShows[state.id] ?: (null to null)
        PosterCard(
            sizeMultiplier = sizeMultiplier,
            metadataId = movie?.id ?: show?.id,
            title = (movie?.title ?: show?.name)?.let { title ->
                {
                    LinkedText(url = "/media/${movie?.id ?: show?.id}") {
                        Text(title)
                    }
                }
            },
            completedPercent = state.completedPercent,
            subtitle1 = {
                movie?.releaseYear?.let {
                    Text(it)
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
                    Div({ classes("flex", "flex-row") }) {
                        tvSeasons
                        LinkedText(
                            tvSeasons
                                .first { it.seasonNumber == seasonNumber }
                                .run { "/media/$id" },
                        ) { Text("S$seasonNumber") }
                        Div { Text(" · ") }
                        LinkedText(url = "/media/${episode.id}") {
                            Text("E$number")
                        }
                    }
                }
            },
            isAdded = true,
            onPlayClicked = {
                playerMediaLinkId.value = state.mediaLinkId
            },
            onBodyClicked = {
                router.navigate("/media/${movie?.id ?: episode?.id}")
            },
        )
    }
}

@Composable
private fun RecentlyAdded.RecentlyAddedMovies(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Recently Added Movies") })
    HorizontalScroller(movies.toList(), scrollbars = false) { (movie, mediaLink) ->
        PosterCard(
            sizeMultiplier = sizeMultiplier,
            metadataId = movie.id,
            isAdded = true,
            onPlayClicked = {
                playerMediaLinkId.value = mediaLink?.id
            }.takeIf { mediaLink != null },
            onBodyClicked = {
                router.navigate("/media/${movie.id}")
            },
            title = {
                LinkedText(url = "/media/${movie.id}") {
                    Text(movie.title)
                }
            },
            subtitle1 = movie.releaseYear?.let {
                {
                    Text(it)
                }
            },
        )
    }
}

@Composable
private fun RecentlyAdded.RecentlyAddedTv(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Recently Added TV") })
    HorizontalScroller(tvShows, scrollbars = false) { show ->
        PosterCard(
            sizeMultiplier = sizeMultiplier,
            metadataId = show.id,
            title = {
                LinkedText(url = "/media/${show.id}") {
                    Text(show.name)
                }
            },
            isAdded = true,
            onBodyClicked = {
                router.navigate("/media/${show.id}")
            },
        )
    }
}

@Composable
private fun Popular.PopularMovies(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Popular Movies") })
    HorizontalScroller(movies.toList(), scrollbars = false) { (movie, link) ->
        PosterCard(
            sizeMultiplier = sizeMultiplier,
            metadataId = movie.id,
            isAdded = link != null,
            onPlayClicked = { playerMediaLinkId.value = link?.id }
                .takeIf { link != null },
            onBodyClicked = {
                router.navigate("/media/${movie.id}")
            },
            title = {
                LinkedText(url = "/media/${movie.id}") {
                    Text(movie.title)
                }
            },
            subtitle1 = movie.releaseYear?.let {
                {
                    Text(it)
                }
            },
        )
    }

}

@Composable
private fun Popular.PopularTvShows(sizeMultiplier: Float) {
    val router = Router.current
    MovieRow(title = { Text("Popular TV") })
    HorizontalScroller(tvShows, scrollbars = false) { tvShow ->
        PosterCard(
            sizeMultiplier = sizeMultiplier,
            metadataId = tvShow.id,
            /*onPlayClicked = { window.location.hash = "!play:${ref?.id}" }
                .takeIf { ref != null },*/
            onBodyClicked = {
                router.navigate("/media/${tvShow.id}")
            },
            title = {
                LinkedText(url = "/media/${tvShow.id}") {
                    Text(tvShow.name)
                }
            },
            subtitle1 = tvShow.releaseYear?.let {
                {
                    Text(it)
                }
            },
            isAdded = tvShow.isAdded,
        )
    }
}

@Composable
private fun MovieRow(
    title: @Composable () -> Unit,
) {
    Div {
        H4({
            classes("px-3", "pt-3", "pb-1")
        }) {
            title()
        }
    }
}

@Composable
private fun PosterSizeSelector(sizeMultiplier: Float, onInput: (Float) -> Unit) {
    Div({
        classes("flex", "align-items-center", "gap-2")
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
