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
import anystream.models.TvShow
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
import io.kvision.form.text.textInput
import io.kvision.html.*
import io.kvision.modal.Modal
import io.kvision.panel.*
import io.kvision.routing.routing
import io.kvision.state.observableListOf
import io.kvision.state.stateFlow
import io.kvision.toast.Toast

class TvTab(
    private val client: AnyStreamClient
) : VPanel(), CoroutineScope {


    override val coroutineContext = Default + SupervisorJob()
    private val scope: CoroutineScope = this
    private val shows = observableListOf<TvShow>()
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
        }

        flexPanel(
            shows,
            wrap = FlexWrap.WRAP,
            direction = FlexDirection.ROW,
            justify = JustifyContent.FLEXSTART,
            alignItems = AlignItems.STRETCH,
            className = "container-fluid"
        ) { movies ->
            movies.forEach { movie ->
                val download = downloads.find { it.contentId == movie.id }
                add(MovieCard(
                    title = movie.name,
                    posterPath = movie.posterPath,
                    overview = movie.overview,
                    releaseDate = movie.firstAirDate,
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
            val response = client.getTvShows()
            //downloads = response.mediaReferences
            shows.clear()
            shows.addAll(response)
        }
    }

    private fun Container.addMovieActions(show: TvShow, download: MediaReference?) {
        button("", "fas fa-trash", style = ButtonStyle.OUTLINEDANGER) {
            size = ButtonSize.SMALL
            onClick { showDeleteMovie(show) }
        }
        button("", "fas fa-search", style = ButtonStyle.OUTLINESECONDARY) {
            size = ButtonSize.SMALL
            onClick { searchForTorrents(show) }
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

    private fun showDeleteMovie(movie: TvShow) {
        val deleteFilesBox = CheckBox(false, label = "Delete Files")
        val modal = Modal("Confirm Delete")
        modal.add(Label("Are you sure you would like to delete \"${movie.name}\"?"))
        modal.add(deleteFilesBox)
        modal.addButton(Button("Cancel", style = ButtonStyle.SECONDARY).apply {
            onClick { modal.hide() }
        })
        modal.addButton(Button("Confirm", style = ButtonStyle.DANGER).apply {
            onClick {
                modal.hide()
                scope.launch {
                    try {
                        //client.deleteMovie(movie.id)
                        //shows.remove(movie)
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
                            mediaKind = MediaKind.valueOf(type.value ?: "TV")
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

    private fun searchForTorrents(show: TvShow) {
        TorrentSearchResultsModal(client, show.id, show.name) {
            client.getTvShowSources(show.id)
        }
    }
}
