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
import androidx.compose.runtime.remember
import anystream.client.AnyStreamClient
import anystream.frontend.components.PosterCard
import anystream.models.MediaReference
import anystream.models.Movie
import anystream.models.TvSeason
import anystream.models.TvShow
import anystream.models.api.MovieResponse
import anystream.models.api.TvShowResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun MediaScreen(
    client: AnyStreamClient,
    mediaId: String,
) {
    val movieState by produceState<MovieResponse?>(null) {
        value = try {
            client.getMovie(mediaId)
        } catch (e: Throwable) {
            null
        }
    }
    val showState by produceState<TvShowResponse?>(null) {
        value = try {
            client.getTvShow(mediaId)
        } catch (e: Throwable) {
            null
        }
    }

    Div({
        classes("p-4")
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
        }
    }) {
        val pjson = remember {
            Json {
                prettyPrint = true
            }
        }
        movieState?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem()
            )

            response.mediaRefs?.let { mediaRefs ->
                BaseRow({
                    Text("Media Refs")
                }) {
                    mediaRefs.forEach { ref ->
                        Div { Pre { Text(pjson.encodeToString(ref)) } }
                    }
                }
            }
        }

        showState?.let { response ->
            BaseDetailsView(
                mediaItem = response.toMediaItem()
            )

            if (response.tvShow.seasons.isNotEmpty()) {
                SeasonRow(response.tvShow.seasons)
            }
            response.mediaRefs?.let { mediaRefs ->
                BaseRow({
                    Text("Media Refs")
                }) {
                    mediaRefs.forEach { ref ->
                        Div { Pre { Text(pjson.encodeToString(ref)) } }
                    }
                }
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
                val year = releaseDate.split("-").firstOrNull { it.length == 4 }
                if (year != null) {
                    Div { H5 { Text(year) } }
                }
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
    val mediaRefs: List<MediaReference>?,
)

private fun MovieResponse.toMediaItem(): MediaItem {
    return MediaItem(
        contentTitle = movie.title,
        posterPath = movie.posterPath,
        overview = movie.overview,
        releaseDate = movie.releaseDate,
        mediaRefs = mediaRefs,
    )
}

private fun TvShowResponse.toMediaItem(): MediaItem {
    return MediaItem(
        contentTitle = tvShow.name,
        posterPath = tvShow.posterPath,
        overview = tvShow.overview,
        releaseDate = tvShow.firstAirDate,
        mediaRefs = null,
    )
}
