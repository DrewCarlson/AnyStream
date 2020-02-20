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
import anystream.models.MediaReference
import anystream.models.MediaKind
import anystream.models.Movie
import anystream.models.api.ImportMedia
import io.ktor.client.features.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import io.kvision.core.*
import io.kvision.core.FlexWrap
import io.kvision.form.check.CheckBox
import io.kvision.form.check.checkBox
import io.kvision.form.check.radioGroup
import io.kvision.form.text.textArea
import io.kvision.form.text.textInput
import io.kvision.html.*
import io.kvision.modal.Modal
import io.kvision.panel.*
import io.kvision.routing.routing
import io.kvision.state.observableListOf
import io.kvision.state.stateFlow
import io.kvision.toast.Toast

class MoviesTab(
    private val client: AnyStreamClient
) : VPanel(), CoroutineScope {


    override val coroutineContext = Default + SupervisorJob()
    private val scope: CoroutineScope = this
    private val movies = observableListOf<Movie>()
    private var downloads = emptyList<MediaReference>()

    init {
        hPanel(
            className = "tmdb-menu-bar",
            spacing = 4,
            alignItems = AlignItems.CENTER
        ) {
            button("", icon = "fas fa-file-import") {
                title = "Import"
                onClick { importMovie() }
            }
            button("", icon = "fas fa-exclamation-triangle") {
                title = "Find Unmapped"
                onClick { findUnmappedFiles() }
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
                val download = downloads.find { it.contentId == movie.id }
                add(MovieCard(
                    title = movie.title,
                    posterPath = movie.posterPath,
                    overview = movie.overview,
                    releaseDate = movie.releaseDate,
                    isAdded = true,
                    onPlayClicked = {
                        download?.id?.let {
                            routing.navigate("/play/$it")
                        }
                    },
                    onBodyClicked = {
                        download?.id?.let {
                            routing.navigate("/play/$it")
                        }
                    }
                ))
            }
        }
        updateMovies()
    }

    private fun updateMovies() {
        scope.launch {
            val response = client.getMovies()
            downloads = response.mediaReferences
            movies.clear()
            movies.addAll(response.movies)
        }
    }

    private fun Container.addMovieActions(movie: Movie, download: MediaReference?) {
        button("", "fas fa-trash", style = ButtonStyle.OUTLINEDANGER) {
            size = ButtonSize.SMALL
            onClick { showDeleteMovie(movie) }
        }
        button("", "fas fa-search", style = ButtonStyle.OUTLINESECONDARY) {
            size = ButtonSize.SMALL
            onClick { searchForTorrents(movie) }
        }
        button(
            "",
            icon = "fas fa-play",
            style = ButtonStyle.INFO
        ) {
            size = ButtonSize.SMALL
            onClick {
                download?.id?.let {
                    routing.navigate("/play/$it")
                }
            }
        }
    }

    private fun showDeleteMovie(movie: Movie) {
        val deleteFilesBox = CheckBox(false, label = "Delete Files")
        val modal = Modal("Confirm Delete")
        modal.add(Label("Are you sure you would like to delete \"${movie.title}\"?"))
        modal.add(deleteFilesBox)
        modal.addButton(Button("Cancel", style = ButtonStyle.SECONDARY).apply {
            onClick { modal.hide() }
        })
        modal.addButton(Button("Confirm", style = ButtonStyle.DANGER).apply {
            onClick {
                modal.hide()
                scope.launch {
                    try {
                        client.deleteMovie(movie.id)
                        movies.remove(movie)
                        Toast.success("Movie deleted")
                    } catch (e: ClientRequestException) {
                        e.printStackTrace()
                        Toast.error("Failed to delete movie")
                    }
                }
            }
        })
        modal.show()
    }

    private fun importMovie() {
        val modal = Modal("Import")
        modal.add(Label("Please select the root folder of the media."))
        modal.addButton(Button("Cancel", style = ButtonStyle.SECONDARY).apply {
            onClick { modal.hide() }
        })
        val pathInput = modal.textInput {
            placeholder = "Content Path"
        }
        val importAll = modal.checkBox(false) {
            label = "Import all in directory"
        }
        val type = modal.radioGroup(
            listOf("MOVIE" to "MOVIE", "TV" to "TV"),
            label = "Media type"
        )
        modal.addButton(Button("Confirm", style = ButtonStyle.PRIMARY).apply {
            disabled = true
            pathInput.stateFlow
                .onEach { disabled = it.isNullOrBlank() }
                .launchIn(scope)
            onClick {
                val path = checkNotNull(pathInput.value)
                scope.launch {
                    try {
                        val request = ImportMedia(
                            contentPath = path,
                            mediaKind = MediaKind.valueOf(type.value ?: "MOVIE")
                        )
                        client.importMedia(request, importAll.value)
                        modal.hide()
                        Toast.success("Media imported")
                    } catch (e: ClientRequestException) {
                        if (e.response.status == HttpStatusCode.NotFound) {
                            Toast.warning("The content path does not exist")
                        } else {
                            e.printStackTrace()
                            Toast.error("Failed to import media")
                        }
                    }
                }
            }
        })
        modal.show()
    }

    private fun findUnmappedFiles() {
        val modal = Modal("Find Unmapped Movies")
        modal.add(Label("Please select a folder to scan."))
        modal.addButton(Button("Cancel", style = ButtonStyle.SECONDARY).apply {
            onClick { modal.hide() }
        })
        val pathInput = modal.textInput {
            placeholder = "Content Path"
        }
        modal.addButton(Button("Confirm", style = ButtonStyle.PRIMARY).apply {
            disabled = true
            pathInput.stateFlow
                .onEach { disabled = it.isNullOrBlank() }
                .launchIn(scope)
            onClick {
                val path = checkNotNull(pathInput.value)
                scope.launch {
                    try {
                        val request = ImportMedia(
                            contentPath = path,
                            mediaKind = MediaKind.MOVIE
                        )
                        val unmapped = client.unmappedMedia(request)
                        modal.hide()
                        val outputModal = Modal("Unmapped Movies")
                        outputModal.textArea {
                            value = unmapped.joinToString("\n")
                        }
                        outputModal.show()
                    } catch (e: ClientRequestException) {
                        if (e.response.status == HttpStatusCode.NotFound) {
                            Toast.warning("The content path does not exist")
                        } else {
                            e.printStackTrace()
                            Toast.error("Failed searching for unmapped files")
                        }
                    }
                }
            }
        })
        modal.show()
    }

    private fun searchForTorrents(movie: Movie) {
        TorrentSearchResultsModal(client, movie.id, movie.title) {
            client.getMovieSources(movie.id)
        }
    }
}
