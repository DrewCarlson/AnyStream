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
package anystream.frontend.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import anystream.models.api.SearchResponse
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H5
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Text

@Composable
fun SearchResultsList(
    searchResponse: SearchResponse,
) {
    Div({
        classes("d-flex", "flex-column", "rounded", "shadow", "py-3")
        style {
            overflowY("scroll")
            backgroundColor(rgb(28, 28, 28))
            backgroundImage("url('../images/noise.webp')")
            backgroundRepeat("repeat")
            width(320.px)
        }
    }) {
        if (searchResponse.movies.isNotEmpty()) {
            SectionTitle("Movies")
        }
        Div({ classes("d-flex", "flex-column", "flex-wrap", "w-100") }) {
            searchResponse.movies.forEach { movie ->
                SearchResultItem(
                    mediaId = movie.id,
                    posterPath = movie.posterPath,
                    title = movie.title,
                    subtitle = movie.releaseDate
                        ?.split("-")
                        ?.first() ?: "",
                )
            }
        }

        if (searchResponse.tvShows.isNotEmpty()) {
            SectionTitle("TV Shows")
        }
        Div({ classes("d-flex", "flex-column", "flex-wrap", "w-100") }) {
            searchResponse.tvShows.forEach { (show, seasonNumber) ->
                SearchResultItem(
                    mediaId = show.id,
                    posterPath = show.posterPath,
                    title = show.name,
                    subtitle = buildString {
                        append(seasonNumber)
                        if (seasonNumber > 1) {
                            append(" seasons")
                        } else {
                            append(" season")
                        }
                    }
                )
            }
        }

        if (searchResponse.episodes.isNotEmpty()) {
            SectionTitle("Episodes")
        }
        Div({ classes("d-flex", "flex-column", "flex-wrap", "w-100") }) {
            searchResponse.episodes.forEach { (episode, show) ->
                SearchResultItem(
                    mediaId = episode.id,
                    posterPath = episode.stillPath,
                    title = episode.name,
                    subtitle = show.name,
                    wide = true,
                )
            }
        }

        if (!searchResponse.hasResult()) {
            Div({ classes("text-center", "w-100") }) {
                H5 { Text("No results") }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Div({
        classes("mx-3")
    }) {
        H5 { Text(title) }
    }
}

@Composable
private fun SearchResultItem(
    mediaId: String,
    posterPath: String?,
    title: String,
    subtitle: String,
    wide: Boolean = false,
) {
    val router = Router.current
    val (posterHeight, posterWidth) = remember(wide) {
        if (wide) 28.px to 42.px else 42.px to 28.px
    }

    Div({
        classes("d-flex", "flex-row", "align-items-center", "px-3", "py-1")
        style {
            cursor("pointer")
        }
        onClick {
            router.navigate("/media/$mediaId")
            searchQuery.value = null
        }
    }) {
        Div({
            style {
                backgroundColor(Color.darkgray)
                height(posterHeight)
                width(posterWidth)
            }
        }) {
            Img(
                src = "https://image.tmdb.org/t/p/w300$posterPath",
                attrs = {
                    classes("h-100", "w-100")
                    attr("loading", "lazy")
                }
            )
        }

        Div({ classes("d-flex", "flex-column", "px-2") }) {
            Div {
                Text(title)
            }
            Div {
                Text(subtitle)
            }
        }
    }
}
