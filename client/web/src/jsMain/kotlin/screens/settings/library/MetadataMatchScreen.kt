/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.screens.settings.library

import androidx.compose.runtime.*
import anystream.LocalAnyStreamClient
import anystream.client.AnyStreamClient
import anystream.components.LoadingIndicator
import anystream.models.api.MediaLinkMatchResult
import anystream.models.api.MediaLinkResponse
import anystream.models.api.MetadataMatch
import anystream.util.get
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*


@Composable
fun MetadataMatchScreen(
    mediaLinkId: String?,
    onLoadingStatChanged: (isLoading: Boolean) -> Unit,
    closeScreen: () -> Unit,
) {
    val client = get<AnyStreamClient>()
    val scope = rememberCoroutineScope()
    val mediaLinkResponse by produceState<MediaLinkResponse?>(null, mediaLinkId) {
        value = if (mediaLinkId == null) {
            null
        } else {
            client.library.findMediaLink(mediaLinkId, includeMetadata = true)
        }
    }
    val matches by produceState<List<MediaLinkMatchResult>>(emptyList(), mediaLinkId) {
        value = if (mediaLinkId == null) {
            emptyList()
        } else {
            client.library.matchesFor(mediaLinkId)
        }
    }

    Div({ classes("flex", "flex-col", "w-full", "h-full", "align-items-center", "gap-2") }) {
        Div({ classes("w-full") }) {
            Text("Location: ${mediaLinkResponse?.mediaLink?.filePath}")
        }
        if (mediaLinkId != null && matches.isEmpty()) {
            LoadingIndicator()
        } else {
            Div({
                classes("flex", "flex-col", "w-full")
                style {
                    overflowY("scroll")
                }
            }) {
                matches.forEach { result ->
                    MatchResultContainer(
                        result,
                        mediaLinkResponse?.mediaLink?.metadataId,
                        onMatchSelected = { match ->
                            scope.launch {
                                onLoadingStatChanged(true)
                                client.library.matchFor(mediaLinkResponse?.mediaLink?.id!!, match.remoteId)
                                onLoadingStatChanged(false)
                                closeScreen()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchResultContainer(
    result: MediaLinkMatchResult,
    currentMetadataId: String?,
    onMatchSelected: (MetadataMatch) -> Unit
) {
    when (result) {
        is MediaLinkMatchResult.Success -> {
            MatchListTable(result) { match ->
                MatchListRow(
                    match,
                    onMatchSelected.takeUnless {
                        match.exists && match.metadataId == currentMetadataId
                    }
                )
            }
        }

        is MediaLinkMatchResult.FileNameParseFailed,
        is MediaLinkMatchResult.NoMatchesFound,
        is MediaLinkMatchResult.NoSupportedFiles -> {
            Div { Text("Response: $result") }
        }
    }
}

@Composable
private fun MatchListTable(
    result: MediaLinkMatchResult.Success,
    buildRow: @Composable (MetadataMatch) -> Unit,
) {
    Table({
        classes("table", "table-striped")
    }) {
        Tbody {
            result.matches.forEach { match ->
                buildRow(match)
            }
        }
    }
}

@Composable
private fun MatchListRow(
    match: MetadataMatch,
    onMatchSelected: ((MetadataMatch) -> Unit)? = null
) {
    Tr({
        style {
            if (onMatchSelected != null) {
                cursor("pointer")
            }
        }
    }) {
        Td {
            when (match) {
                is MetadataMatch.MovieMatch -> {
                    MovieMatchResult(match, onMatchSelected)
                }

                is MetadataMatch.TvShowMatch -> {
                    TvShowMatchResult(match, onMatchSelected)
                }
            }
        }
    }
}

@Composable
private fun MovieMatchResult(
    match: MetadataMatch.MovieMatch,
    onClick: ((match: MetadataMatch) -> Unit)? = null
) {
    val client = LocalAnyStreamClient.current
    MatchResultContainer(
        title = match.movie.title,
        year = match.movie.releaseYear.orEmpty(),
        overview = match.movie.overview,
        posterUrl = client.images.buildImageUrl("poster", match.movie.id, 300),
        onClick = if (onClick == null) {
            null
        } else {
            { onClick(match) }
        }
    )
}

@Composable
private fun TvShowMatchResult(
    match: MetadataMatch.TvShowMatch,
    onClick: ((match: MetadataMatch) -> Unit)? = null
) {
    val client = LocalAnyStreamClient.current
    MatchResultContainer(
        title = match.tvShow.name,
        year = match.tvShow.releaseYear.orEmpty(),
        overview = match.tvShow.overview,
        posterUrl = client.images.buildImageUrl("poster", match.tvShow.id, 300),
        onClick = if (onClick == null) {
            null
        } else {
            { onClick(match) }
        }
    )
}

@Composable
private fun MatchResultContainer(
    title: String,
    year: String,
    overview: String,
    posterUrl: String,
    onClick: (() -> Unit)? = null,
) {
    val isSelected = remember(onClick) { onClick == null }
    Div({
        classes("flex", "flex-col")
        if (onClick != null) {
            onClick { onClick() }
        }
    }) {
        Div({
            classes("flex", "flex-row", "m-1", "gap-3")
            style {
                justifyContent(JustifyContent.SpaceBetween)
            }
        }) {
            Div {
                if (isSelected) {
                    I({ classes("bi", "bi-check-circle-fill") })
                    Text(" ")
                }
                Text(title)
            }
            Div { Text(year) }
        }
        Div({
            classes("flex", "flex-row", "m-1", "gap-3")
            style {
                justifyContent(JustifyContent.SpaceBetween)
            }
        }) {
            Div {
                Img(src = posterUrl) {
                    style {
                        width(150.px)
                    }
                }
            }
            Div { Text(overview) }
        }
    }
}
