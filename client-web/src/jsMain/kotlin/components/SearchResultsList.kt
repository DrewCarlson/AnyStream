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
package anystream.frontend.components

import androidx.compose.runtime.Composable
import anystream.frontend.searchQuery
import anystream.frontend.searchWindowPosition
import anystream.models.api.SearchResponse
import app.softwork.routingcompose.BrowserRouter
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Text


@Composable
fun SearchResultsList(
    searchResponse: SearchResponse,
) {
    val (height, width) = searchWindowPosition.value
    Div({
        classes("h-100", "w-100")
        style {
            position(Position.Absolute)
            property("z-index", 1)
        }
        onClick {
            searchQuery.value = null
        }
    }) {
        Div({
            classes("w-50", "p-4")
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                overflowY("auto")
                backgroundColor(rgba(0, 0, 0, .9))
                position(Position.Absolute)
                top(height.px)
                left(width.px)
                height((window.innerHeight - height).px)
                property("z-index", 1)
            }
        }) {
            if (searchResponse.movies.isNotEmpty()) {
                Div { H3 { Text("Movies") } }
            }
            Div({
                classes("w-100")
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Row)
                    flexWrap(FlexWrap.Wrap)
                }
            }) {
                searchResponse.movies.forEach { movie ->
                    Div {
                        PosterCard(
                            title = {
                                LinkedText(url = "/media/${movie.id}") {
                                    Text(movie.title)
                                }
                            },
                            posterPath = movie.posterPath,
                            onPlayClicked = searchResponse.mediaReferences[movie.id]
                                ?.let { mediaRef ->
                                    {
                                        searchQuery.value = null
                                        window.location.hash = "!play:${mediaRef.id}"
                                    }
                                },
                            onBodyClicked = {
                                searchQuery.value = null
                                BrowserRouter.navigate("/media/${movie.id}")
                            },
                        )
                    }
                }
            }

            if (searchResponse.tvShows.isNotEmpty()) {
                Div { H3 { Text("TV Shows") } }
            }
            Div({
                classes("w-100")
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Row)
                    flexWrap(FlexWrap.Wrap)
                }
            }) {
                searchResponse.tvShows.forEach { show ->
                    Div {
                        PosterCard(
                            title = {
                                LinkedText(url = "/media/${show.id}") {
                                    Text(show.name)
                                }
                            },
                            posterPath = show.posterPath,
                            onPlayClicked = searchResponse.mediaReferences[show.id]
                                ?.let { mediaRef ->
                                    {
                                        searchQuery.value = null
                                        window.location.hash = "!play:${mediaRef.id}"
                                    }
                                },
                            onBodyClicked = {
                                searchQuery.value = null
                                BrowserRouter.navigate("/media/${show.id}")
                            },
                        )
                    }
                }
            }

            if (searchResponse.episodes.isNotEmpty()) {
                Div { H3 { Text("Episodes") } }
            }
            Div({
                classes("w-100")
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Row)
                    flexWrap(FlexWrap.Wrap)
                }
            }) {
                searchResponse.episodes.forEach { episode ->
                    Div {
                        PosterCard(
                            title = { Text(episode.name) },
                            posterPath = episode.stillPath,
                            wide = true,
                            onPlayClicked = searchResponse.mediaReferences[episode.id]
                                ?.let { mediaRef ->
                                    {
                                        searchQuery.value = null
                                        window.location.hash = "!play:${mediaRef.id}"
                                    }
                                },
                            onBodyClicked = {
                                searchQuery.value = null
                                BrowserRouter.navigate("/media/${episode.id}")
                            },
                        )
                    }
                }
            }
        }
    }
}
