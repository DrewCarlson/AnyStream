package anystream.screens.settings.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import anystream.client.AnyStreamClient
import anystream.models.Movie
import anystream.models.TvShow
import anystream.models.api.MediaLinkMatchResult
import anystream.models.api.MediaLinkResponse
import anystream.models.api.MetadataMatch
import anystream.util.get
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*


@Composable
fun MetadataMatchScreen(
    mediaLinkGid: String?,
    onLoadingStatChanged: (isLoading: Boolean) -> Unit,
    closeScreen: () -> Unit,
) {
    val client = get<AnyStreamClient>()
    val mediaLinkResponse by produceState<MediaLinkResponse?>(null, mediaLinkGid) {
        value = if (mediaLinkGid == null) {
            null
        } else {
            client.findMediaLink(mediaLinkGid, includeMetadata = true)
        }
    }
    val matches by produceState<List<MediaLinkMatchResult>>(emptyList(), mediaLinkGid) {
        value = if (mediaLinkGid == null) {
            emptyList()
        } else {
            client.matchesFor(mediaLinkGid)
        }
    }

    Div({ classes("vstack", "h-100", "gap-2") }) {
        Div { Text("Location: ${mediaLinkResponse?.mediaLink?.filePath}") }
        //Div { Text("Current Match: ${mediaLinkResponse?.metadata?.title}") }
        Div({
            classes("vstack")
            style {
                overflowY("scroll")
            }
        }) {
            matches.forEach { result ->
                MatchResultContainer(result, mediaLinkResponse?.mediaLink?.metadataGid)
            }
        }
    }
}

@Composable
private fun MatchResultContainer(
    result: MediaLinkMatchResult,
    currentMetadataGid: String?
) {
    when (result) {
        is MediaLinkMatchResult.Success -> MatchListTable(result, currentMetadataGid)
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
    currentMetadataGid: String?
) {
    Table({
        classes("table", "table-striped")
    }) {
        Tbody {
            result.matches.forEach { match ->
                Tr {
                    Td {
                        when (match) {
                            is MetadataMatch.MovieMatch -> MovieMatchResult(match.movie, currentMetadataGid)
                            is MetadataMatch.TvShowMatch -> TvShowMatchResult(match.tvShow, currentMetadataGid)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieMatchResult(
    movie: Movie,
    currentMetadataGid: String?
) {
    val isSelected = if (currentMetadataGid == null) {
        false
    } else {
        currentMetadataGid == movie.gid
    }
    MatchResultContainer(
        title = movie.title,
        year = movie.releaseDate?.substringBefore('-').orEmpty(),
        overview = movie.overview,
        posterUrl = "https://image.tmdb.org/t/p/w300${movie.posterPath}",
        isSelected = isSelected,
    )
}

@Composable
private fun TvShowMatchResult(
    tvShow: TvShow,
    currentMetadataGid: String?
) {
    val isSelected = if (currentMetadataGid == null) {
        false
    } else {
        currentMetadataGid == tvShow.gid
    }
    MatchResultContainer(
        title = tvShow.name,
        year = tvShow.firstAirDate?.substringBefore('-').orEmpty(),
        overview = tvShow.overview,
        posterUrl = "https://image.tmdb.org/t/p/w300${tvShow.posterPath}",
        isSelected = isSelected,
    )
}

@Composable
private fun MatchResultContainer(
    title: String,
    year: String,
    overview: String,
    posterUrl: String,
    isSelected: Boolean,
) {
    Div({
        classes("vstack")
    }) {
        Div({
            classes("hstack", "m-1", "gap-3")
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
            classes("hstack", "m-1", "gap-3")
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
