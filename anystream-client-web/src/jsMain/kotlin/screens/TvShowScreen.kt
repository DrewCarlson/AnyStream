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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import anystream.frontend.LocalAnyStreamClient
import anystream.frontend.components.*
import anystream.models.MediaReference
import anystream.models.TvShow
import anystream.models.api.TvShowsResponse
import app.softwork.routingcompose.Router
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun TvShowScreen() {
    val client = LocalAnyStreamClient.current
    val showResponse by produceState<TvShowsResponse?>(null) {
        value = client.getTvShows()
    }

    when (val response = showResponse) {
        null -> FullSizeCenteredLoader()
        else -> {
            val (shows, refs) = response
            if (shows.isEmpty()) {
                Div({ classes("d-flex", "justify-content-center", "align-items-center", "h-100") }) {
                    Text("TV Shows will appear here.")
                }
            } else {
                val router = Router.current
                VirtualScroller(shows) { show ->
                    val ref = refs.find { it.contentId == show.id }
                    TvShowCard(router, show, ref)
                }
            }
        }
    }
}

@Composable
fun TvShowCard(
    router: Router,
    show: TvShow,
    ref: MediaReference?,
) {
    PosterCard(
        title = {
            LinkedText("/media/${show.id}", router) {
                Text(show.name)
            }
        },
        posterPath = show.posterPath,
        isAdded = true,
        onPlayClicked = {
            window.location.hash = "!play:${ref?.id}"
        }.takeIf { ref != null },
        onBodyClicked = {
            router.navigate("/media/${show.id}")
        }
    )
}
