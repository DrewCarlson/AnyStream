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
package anystream.frontend

import anystream.client.AnyStreamClient
import anystream.models.tmdb.PartialMovie
import io.ktor.client.features.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.kvision.core.*
import io.kvision.core.FlexWrap
import io.kvision.form.text.TextInputType
import io.kvision.form.text.textInput
import io.kvision.html.*
import io.kvision.panel.*
import io.kvision.state.observableListOf
import io.kvision.state.observableState
import io.kvision.state.stateFlow
import io.kvision.toast.Toast
import io.kvision.utils.px

private const val SEARCH_DEBOUNCE_MS = 200L

class TmdbTab(
    private val client: AnyStreamClient
) : VPanel(), CoroutineScope {

    override val coroutineContext = Dispatchers.Default + SupervisorJob()
    private val scope: CoroutineScope = this
    private val page = MutableStateFlow(1)
    private val query = MutableStateFlow("")
    private val movies = observableListOf<PartialMovie>()

    init {
        hPanel(
            className = "tmdb-menu-bar",
            spacing = 4,
            alignItems = AlignItems.CENTER
        ) {
            textInput(TextInputType.TEXT) {
                placeholder = "Search"
                stateFlow
                    .map { it ?: "" }
                    .onEach {
                        page.value = 1
                        query.value = it
                    }
                    .launchIn(scope)
            }

            button("", "fas fa-chevron-left") {
                onClick {
                    page.value = (page.value - 1).coerceAtLeast(1)
                }
            }

            textInput(page.observableState) { currentPage ->
                width = 60.px
                value = currentPage.toString()
                stateFlow
                    .onEach { page.value = it?.toIntOrNull() ?: page.value }
                    .launchIn(scope)
            }

            button("", "fas fa-chevron-right") {
                onClick {
                    page.value += 1
                }
            }
        }

        flexPanel(
            movies,
            wrap = FlexWrap.WRAP,
            direction = FlexDirection.ROW,
            justify = JustifyContent.FLEXSTART,
            alignItems = AlignItems.STRETCH,
            className = "container-fluid"
        ) { movies ->
            movies.forEach { movie ->
                add(MovieCard(
                    title = movie.title,
                    posterPath = movie.posterPath,
                    overview = movie.overview,
                    releaseDate = movie.releaseDate,
                    isAdded = movie.isAdded,
                    onPlayClicked = {},
                    onBodyClicked = {}
                ))
            }
        }

        combineTransform(page, query) { page, query -> emit(page to query) }
            .debounce(SEARCH_DEBOUNCE_MS)
            .onStart { emit(page.value to query.value) }
            .distinctUntilChanged()
            .mapLatest { (page, query) ->
                try {
                    if (query.isBlank()) {
                        client.getTmdbPopularMovies(page = page).items
                    } else {
                        client.searchTmdbMovies(query, page).items
                    }
                } catch (e: ClientRequestException) {
                    e.printStackTrace()
                    emptyList()
                }
            }
            .onEach { newMovies ->
                movies.clear()
                movies.addAll(newMovies)
            }
            .launchIn(scope)
    }

    private fun addMovie(movie: PartialMovie, onComplete: (success: Boolean) -> Unit) {
        scope.launch {
            try {
                client.addMovieFromTmdb(movie.tmdbId)
                movies[movies.indexOf(movie)] = movie.copy(isAdded = true)
                Toast.success("Movie added")
                onComplete(true)
            } catch (e: ClientRequestException) {
                e.printStackTrace()
                when (e.response.status) {
                    HttpStatusCode.Conflict -> {
                        Toast.warning("Movie already added")
                    }
                    else -> {
                        Toast.error("Failed to add movie!")
                    }
                }
                onComplete(false)
            }
        }
    }

    private fun Container.addMovieActions(movie: PartialMovie) {
        button("") {
            val addedIcon = "fas fa-check-circle"
            val unAddedIcon = "fas fa-plus"
            size = ButtonSize.SMALL
            if (movie.isAdded) {
                icon = addedIcon
                style = ButtonStyle.OUTLINESUCCESS
            } else {
                icon = unAddedIcon
                style = ButtonStyle.OUTLINEPRIMARY
                onClick {
                    icon = ""
                    disabled = true
                    style = ButtonStyle.SUCCESS
                    val loading = div(classes = setOf("spinner-grow", "spinner-grow-sm"))
                    addMovie(movie) { success ->
                        disabled = success
                        style = if (success) ButtonStyle.SUCCESS else ButtonStyle.PRIMARY
                        icon = if (success) addedIcon else unAddedIcon
                        remove(loading)
                    }
                }
            }
        }
        button(
            "",
            icon = "fas fa-play",
            style = ButtonStyle.INFO
        ) {
            size = ButtonSize.SMALL
            onClick {
            }
        }
    }
}
