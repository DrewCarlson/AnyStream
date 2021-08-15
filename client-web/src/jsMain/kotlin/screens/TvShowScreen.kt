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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import anystream.client.AnyStreamClient
import anystream.frontend.components.LinkedText
import anystream.frontend.components.PosterCard
import anystream.models.api.TvShowsResponse
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun TvShowScreen(client: AnyStreamClient) {
    val showResponse by produceState(TvShowsResponse(emptyList(), emptyList())) {
        value = client.getTvShows()
    }
    Div({
        style {
            classes("p-4")
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            flexWrap(FlexWrap.Wrap)
        }
    }) {
        val (shows, refs) = showResponse
        if (shows.isNotEmpty()) {
            shows.forEach { show ->
                val ref = refs.find { it.contentId == show.id }
                PosterCard(
                    title = {
                        LinkedText(url = "/media/${show.id}") {
                            Text(show.name)
                        }
                    },
                    posterPath = show.posterPath,
                    isAdded = true,
                    onPlayClicked = {
                        window.location.hash = "!play:${ref?.id}"
                    }.takeIf { ref != null },
                    onBodyClicked = {
                        BrowserRouter.navigate("/media/${show.id}")
                    }
                )
            }
        }
    }
}