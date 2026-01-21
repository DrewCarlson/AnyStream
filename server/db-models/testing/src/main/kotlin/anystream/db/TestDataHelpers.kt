/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.db

import anystream.models.*
import anystream.util.ObjectId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Creates a test movie metadata record.
 */
fun createTestMovie(
    id: String = ObjectId.next(),
    title: String = "Test Movie",
    tmdbId: Int? = 12345,
    imdbId: String? = "tt1234567",
    overview: String = "A test movie overview",
    runtime: Duration = 120.minutes,
    releaseDate: Instant? = Clock.System.now(),
    contentRating: String? = "PG-13",
    tmdbRating: Int? = 75,
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = createdAt,
): Metadata {
    return Metadata(
        id = id,
        rootId = null,
        parentId = null,
        parentIndex = null,
        title = title,
        overview = overview,
        tmdbId = tmdbId,
        imdbId = imdbId,
        runtime = runtime,
        index = null,
        contentRating = contentRating,
        firstAvailableAt = releaseDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mediaKind = MediaKind.MOVIE,
        mediaType = MediaType.MOVIE,
        tmdbRating = tmdbRating,
    )
}

/**
 * Creates a test TV show metadata record.
 */
fun createTestTvShow(
    id: String = ObjectId.next(),
    name: String = "Test Show",
    tmdbId: Int? = 54321,
    overview: String = "A test TV show overview",
    firstAirDate: Instant? = Clock.System.now(),
    contentRating: String? = "TV-14",
    tmdbRating: Int? = 80,
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = createdAt,
): Metadata {
    return Metadata(
        id = id,
        rootId = null,
        parentId = null,
        parentIndex = null,
        title = name,
        overview = overview,
        tmdbId = tmdbId,
        imdbId = null,
        runtime = null,
        index = null,
        contentRating = contentRating,
        firstAvailableAt = firstAirDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_SHOW,
        tmdbRating = tmdbRating,
    )
}

/**
 * Creates a test TV season metadata record.
 */
fun createTestSeason(
    showId: String,
    seasonNumber: Int,
    id: String = ObjectId.next(),
    name: String = "Season $seasonNumber",
    tmdbId: Int? = null,
    overview: String = "Season $seasonNumber overview",
    airDate: Instant? = Clock.System.now(),
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = createdAt,
): Metadata {
    return Metadata(
        id = id,
        rootId = showId,
        parentId = showId,
        parentIndex = null,
        title = name,
        overview = overview,
        tmdbId = tmdbId,
        imdbId = null,
        runtime = null,
        index = seasonNumber,
        contentRating = null,
        firstAvailableAt = airDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_SEASON,
        tmdbRating = null,
    )
}

/**
 * Creates a test TV episode metadata record.
 */
fun createTestEpisode(
    showId: String,
    seasonId: String,
    seasonNumber: Int,
    episodeNumber: Int,
    id: String = ObjectId.next(),
    name: String = "Episode $episodeNumber",
    tmdbId: Int? = null,
    overview: String = "Episode $episodeNumber overview",
    airDate: Instant? = Clock.System.now(),
    tmdbRating: Int? = null,
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = createdAt,
): Metadata {
    return Metadata(
        id = id,
        rootId = showId,
        parentId = seasonId,
        parentIndex = seasonNumber,
        title = name,
        overview = overview,
        tmdbId = tmdbId,
        imdbId = null,
        runtime = null,
        index = episodeNumber,
        contentRating = null,
        firstAvailableAt = airDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mediaKind = MediaKind.TV,
        mediaType = MediaType.TV_EPISODE,
        tmdbRating = tmdbRating,
    )
}

/**
 * Creates a test directory record.
 */
fun createTestDirectory(
    id: String = ObjectId.next(),
    libraryId: String,
    filePath: String = "/test/media",
    parentId: String? = null,
): Directory {
    return Directory(
        id = id,
        parentId = parentId,
        libraryId = libraryId,
        filePath = filePath,
    )
}

/**
 * Creates a test media link record.
 */
fun createTestMediaLink(
    directoryId: String,
    metadataId: String? = null,
    rootMetadataId: String? = null,
    id: String = ObjectId.next(),
    filePath: String = "/test/file.mp4",
    descriptor: Descriptor = Descriptor.VIDEO,
    mediaKind: MediaKind = MediaKind.MOVIE,
    type: MediaLinkType = MediaLinkType.LOCAL,
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = createdAt,
): MediaLink {
    return MediaLink(
        id = id,
        metadataId = metadataId,
        rootMetadataId = rootMetadataId,
        directoryId = directoryId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mediaKind = mediaKind,
        type = type,
        filePath = filePath,
        fileIndex = null,
        hash = null,
        descriptor = descriptor,
    )
}

/**
 * Represents a complete TV show test graph with show, seasons, and episodes.
 */
data class TestTvShowGraph(
    val show: Metadata,
    val seasons: List<Metadata>,
    val episodes: List<Metadata>,
)

/**
 * Creates a complete TV show test graph with the specified number of seasons and episodes.
 */
fun createTestTvShowWithEpisodes(
    showName: String = "Test Show",
    showId: String = ObjectId.next(),
    tmdbId: Int? = 54321,
    seasonCount: Int = 2,
    episodesPerSeason: Int = 10,
    createdAt: Instant = Clock.System.now(),
): TestTvShowGraph {
    val show = createTestTvShow(
        id = showId,
        name = showName,
        tmdbId = tmdbId,
        createdAt = createdAt,
    )

    val seasons = (1..seasonCount).map { seasonNumber ->
        createTestSeason(
            showId = showId,
            seasonNumber = seasonNumber,
            tmdbId = tmdbId?.let { it * 1000 + seasonNumber },
            createdAt = createdAt,
        )
    }

    val episodes = seasons.flatMap { season ->
        val seasonNumber = season.index!!
        (1..episodesPerSeason).map { episodeNumber ->
            createTestEpisode(
                showId = showId,
                seasonId = season.id,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                tmdbId = tmdbId?.let { it * 100000 + seasonNumber * 1000 + episodeNumber },
                createdAt = createdAt,
            )
        }
    }

    return TestTvShowGraph(show, seasons, episodes)
}

/**
 * Represents a test movie library with movies and their media links.
 */
data class TestMovieLibrary(
    val libraryId: String,
    val directory: Directory,
    val movies: List<Metadata>,
    val mediaLinks: List<MediaLink>,
)

/**
 * Creates a test movie library with the specified number of movies.
 */
fun createTestMovieLibrary(
    libraryId: String = ObjectId.next(),
    directoryId: String = ObjectId.next(),
    basePath: String = "/test/movies",
    movieCount: Int = 5,
    createdAt: Instant = Clock.System.now(),
): TestMovieLibrary {
    val directory = createTestDirectory(
        id = directoryId,
        libraryId = libraryId,
        filePath = basePath,
    )

    val moviesWithLinks = (1..movieCount).map { index ->
        val movie = createTestMovie(
            title = "Test Movie $index",
            tmdbId = 10000 + index,
            createdAt = createdAt,
        )
        val link = createTestMediaLink(
            directoryId = directoryId,
            metadataId = movie.id,
            filePath = "$basePath/Test Movie $index (2023)/Test Movie $index.mp4",
            mediaKind = MediaKind.MOVIE,
            createdAt = createdAt,
        )
        movie to link
    }

    return TestMovieLibrary(
        libraryId = libraryId,
        directory = directory,
        movies = moviesWithLinks.map { it.first },
        mediaLinks = moviesWithLinks.map { it.second },
    )
}

/**
 * Represents a test TV library with shows, seasons, episodes, and media links.
 */
data class TestTvLibrary(
    val libraryId: String,
    val directory: Directory,
    val shows: List<TestTvShowGraph>,
    val mediaLinks: List<MediaLink>,
)

/**
 * Creates a test TV library with the specified number of shows.
 */
fun createTestTvLibrary(
    libraryId: String = ObjectId.next(),
    directoryId: String = ObjectId.next(),
    basePath: String = "/test/tv",
    showCount: Int = 2,
    seasonsPerShow: Int = 2,
    episodesPerSeason: Int = 10,
    createdAt: Instant = Clock.System.now(),
): TestTvLibrary {
    val directory = createTestDirectory(
        id = directoryId,
        libraryId = libraryId,
        filePath = basePath,
    )

    val showsWithLinks = (1..showCount).map { showIndex ->
        val showGraph = createTestTvShowWithEpisodes(
            showName = "Test Show $showIndex",
            tmdbId = 50000 + showIndex,
            seasonCount = seasonsPerShow,
            episodesPerSeason = episodesPerSeason,
            createdAt = createdAt,
        )

        val links = showGraph.episodes.map { episode ->
            val seasonNum = episode.parentIndex!!
            val episodeNum = episode.index!!
            createTestMediaLink(
                directoryId = directoryId,
                metadataId = episode.id,
                rootMetadataId = showGraph.show.id,
                filePath = "$basePath/Test Show $showIndex/Season $seasonNum/S${seasonNum.toString().padStart(2, '0')}E${episodeNum.toString().padStart(2, '0')}.mp4",
                mediaKind = MediaKind.TV,
                descriptor = Descriptor.VIDEO,
                createdAt = createdAt,
            )
        }

        showGraph to links
    }

    return TestTvLibrary(
        libraryId = libraryId,
        directory = directory,
        shows = showsWithLinks.map { it.first },
        mediaLinks = showsWithLinks.flatMap { it.second },
    )
}

/**
 * DSL builder for creating test TV shows with a fluent API.
 */
class TestTvShowBuilder(
    private val showId: String = ObjectId.next(),
    private val showName: String = "Test Show",
    private val tmdbId: Int? = null,
) {
    private val seasons = mutableListOf<TestSeasonBuilder>()

    fun season(seasonNumber: Int, configure: TestSeasonBuilder.() -> Unit = {}): TestTvShowBuilder {
        seasons.add(TestSeasonBuilder(showId, seasonNumber).apply(configure))
        return this
    }

    fun build(): TestTvShowGraph {
        val show = createTestTvShow(id = showId, name = showName, tmdbId = tmdbId)
        val builtSeasons = seasons.map { it.buildSeason() }
        val builtEpisodes = seasons.flatMap { it.buildEpisodes() }
        return TestTvShowGraph(show, builtSeasons, builtEpisodes)
    }
}

class TestSeasonBuilder(
    private val showId: String,
    private val seasonNumber: Int,
) {
    private val seasonId = ObjectId.next()
    private val episodes = mutableListOf<Pair<Int, String>>()

    fun episode(episodeNumber: Int, name: String = "Episode $episodeNumber"): TestSeasonBuilder {
        episodes.add(episodeNumber to name)
        return this
    }

    fun episodes(count: Int): TestSeasonBuilder {
        repeat(count) { index ->
            episode(index + 1)
        }
        return this
    }

    internal fun buildSeason(): Metadata {
        return createTestSeason(showId = showId, seasonNumber = seasonNumber, id = seasonId)
    }

    internal fun buildEpisodes(): List<Metadata> {
        return episodes.map { (episodeNumber, name) ->
            createTestEpisode(
                showId = showId,
                seasonId = seasonId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                name = name,
            )
        }
    }
}

/**
 * DSL entry point for building a test TV show.
 */
fun testTvShow(
    name: String = "Test Show",
    tmdbId: Int? = null,
    configure: TestTvShowBuilder.() -> Unit = {}
): TestTvShowGraph {
    return TestTvShowBuilder(showName = name, tmdbId = tmdbId).apply(configure).build()
}
