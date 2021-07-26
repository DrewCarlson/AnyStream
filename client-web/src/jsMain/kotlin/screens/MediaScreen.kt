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
import anystream.frontend.components.PosterCard
import anystream.models.Movie
import anystream.models.TvSeason
import anystream.models.TvShow
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun MediaScreen(
    client: AnyStreamClient,
    mediaId: String,
) {
    val movieState by produceState<Movie?>(null) {
        value = try { client.getMovie(mediaId) } catch (e: Throwable) { null }
    }
    val showState by produceState<TvShow?>(null) {
        value = try { client.getTvShow(mediaId) } catch (e: Throwable) { null }
    }

    Div({
        classes("p-4")
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
        }
    }) {
        movieState?.let { movie ->
            BaseDetailsView(
                mediaItem = movie.toMediaItem()
            )
        }

        showState?.let { show ->
            BaseDetailsView(
                mediaItem = show.toMediaItem()
            )

            if (show.seasons.isNotEmpty()) {
                SeasonRow(show.seasons)
            }
        }
    }
}

@Composable
private fun BaseDetailsView(
    mediaItem: MediaItem,
) {
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
        }
    }) {
        Div({
            style {
                flexShrink(0)
            }
        }) {
            PosterCard(
                title = mediaItem.contentTitle,
                posterPath = mediaItem.posterPath,
                overview = mediaItem.overview,
                releaseDate = mediaItem.releaseDate,
                isAdded = true,
            )
        }
        Div({
            classes("p-4")
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                flexGrow(1)
            }
        }) {
            Div { H3 { Text(mediaItem.contentTitle) } }
            mediaItem.releaseDate?.also { releaseDate ->
                val year = releaseDate.split("-").first { it.length == 4 }
                Div { H5 { Text(year) } }
            }
            Div { Text(mediaItem.overview) }
        }
    }
}

@Composable
private fun SeasonRow(seasons: List<TvSeason>) {
    BaseRow(
        title = { Text("Seasons") },
        wrap = true
    ) {
        seasons
            .filter { it.seasonNumber != 0 }
            .forEach { season ->
            PosterCard(
                title = season.name,
                posterPath = season.posterPath,
                overview = season.overview,
                releaseDate = season.airDate,
                isAdded = true,
            )
        }
    }
}
@Composable
private fun BaseRow(
    title: @Composable () -> Unit,
    wrap: Boolean = false,
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
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Row)
            if (wrap) {
                flexWrap(FlexWrap.Wrap)
            } else {
                property("overflow-x", "auto")
                property("scrollbar-width", "none")
            }
        }
    }) {
        buildItems()
    }
}

private data class MediaItem(
    val contentTitle: String,
    val posterPath: String?,
    val overview: String,
    val releaseDate: String?,
)

private fun Movie.toMediaItem(): MediaItem {
    return MediaItem(
        contentTitle = title,
        posterPath = posterPath,
        overview = overview,
        releaseDate = releaseDate,
    )
}

private fun TvShow.toMediaItem(): MediaItem {
    return MediaItem(
        contentTitle = name,
        posterPath = posterPath,
        overview = overview,
        releaseDate = firstAirDate
    )
}
