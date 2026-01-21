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
package anystream.media.processor

import anystream.data.MetadataDbQueries
import anystream.db.LibraryDao
import anystream.db.MediaLinkDao
import anystream.db.MetadataDao
import anystream.db.PlaybackStatesDao
import anystream.db.SearchableContentDao
import anystream.db.TagsDao
import anystream.db.bindFileSystem
import anystream.db.bindForTest
import anystream.db.bindTestDatabase
import anystream.media.createMovieDirectory
import anystream.media.createTvDirectory
import anystream.media.scanner.MediaFileScanner
import anystream.metadata.ImageStore
import anystream.metadata.MetadataService
import anystream.metadata.providers.FakeMetadataProvider
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.metadata.providers.createFakeMetadataProvider
import anystream.models.Descriptor
import anystream.models.Episode
import anystream.models.MediaKind
import anystream.models.api.MediaLinkMatchResult
import anystream.models.api.MediaScanResult
import anystream.models.api.MetadataMatch
import app.moviebase.tmdb.Tmdb3
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile

class MovieFileProcessorTest : FunSpec({

    val db by bindTestDatabase()
    val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val metadataDao by bindForTest({ MetadataDao(db) })
    val libraryDao by bindForTest({ LibraryDao(db) })
    val queries by bindForTest({
        val tagsDao = TagsDao(db)
        val playbackStatesDao = PlaybackStatesDao(db)
        val searchableContentDao = SearchableContentDao(db)

        MetadataDbQueries(db, metadataDao, tagsDao, mediaLinkDao, playbackStatesDao, searchableContentDao)
    })
    val tmdb by bindForTest({
        Tmdb3 {
            tmdbApiKey = "c1e9e8ade306dd9cbc5e17b05ed4badd"
        }
    })
    val fs by bindFileSystem()
    val metadataService by bindForTest({
        val imageStore = ImageStore(fs.getPath("/test"), HttpClient())
        val provider = TmdbMetadataProvider(tmdb, queries, imageStore)
        MetadataService(listOf(provider), metadataDao, imageStore)
    })
    val mediaFileScanner by bindForTest({ MediaFileScanner(mediaLinkDao, libraryDao, fs) })
    val processor by bindForTest({
        MovieFileProcessor(
            metadataService = metadataService,
            mediaLinkDao = mediaLinkDao,
            libraryDao = libraryDao,
            fs = fs,
        )
    })

    test("match multiple movies from library root") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val library = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.MOVIE }
            .shouldNotBeNull()

        val (mediaRootPath, moviePaths) = fs.createMovieDirectory()
        val rootDirectory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = library.id,
            path = mediaRootPath.absolutePathString(),
        )

        mediaFileScanner.scan(mediaRootPath).shouldBeInstanceOf<MediaScanResult.Success>()

        val childDirectories = libraryDao.fetchChildDirectories(rootDirectory.id)

        childDirectories.forEach { directory ->
            val result = processor.findMetadataMatches(directory, import = true)
                .shouldHaveSize(1)
                .first()
                .shouldBeInstanceOf<MediaLinkMatchResult.Success>()

            val match = result.matches
                .shouldHaveSize(1)
                .first()
                .shouldBeInstanceOf<MetadataMatch.MovieMatch>()

            mediaLinkDao.findByDirectoryId(directory.id)
                // TODO: Support other files like subtitles and posters
                .filter { it.descriptor == Descriptor.VIDEO }
                .shouldForAll {
                    it.rootMetadataId
                        .shouldNotBeNull()
                        .shouldBeEqual(match.movie.id)

                    it.metadataId
                        .shouldNotBeNull()
                        .shouldBeEqual(match.movie.id)
                }
        }
    }
})

/**
 * Tests using FakeMetadataProvider for deterministic E2E testing without external API calls.
 */
class MovieFileProcessorFakeProviderTest : FunSpec({

    val db by bindTestDatabase()
    val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val metadataDao by bindForTest({ MetadataDao(db) })
    val libraryDao by bindForTest({ LibraryDao(db) })
    val fs by bindFileSystem()

    lateinit var fakeProvider: FakeMetadataProvider
    lateinit var metadataService: MetadataService
    lateinit var processor: MovieFileProcessor
    lateinit var mediaFileScanner: MediaFileScanner

    beforeTest {
        fakeProvider = createFakeMetadataProvider(metadataDao)
        val imageStore = ImageStore(fs.getPath("/test").createDirectory(), HttpClient())
        metadataService = MetadataService(listOf(fakeProvider), metadataDao, imageStore)
        processor = MovieFileProcessor(
            metadataService = metadataService,
            mediaLinkDao = mediaLinkDao,
            libraryDao = libraryDao,
            fs = fs,
        )
        mediaFileScanner = MediaFileScanner(mediaLinkDao, libraryDao, fs)
    }

    suspend fun setupMovieLibrary(): Pair<anystream.models.Library, anystream.models.Directory> {
        libraryDao.insertDefaultLibraries()
        val library = libraryDao.fetchLibraries().first { it.mediaKind == MediaKind.MOVIE }
        val rootPath = fs.getPath("/movies").createDirectory()
        val rootDirectory = libraryDao.insertDirectory(null, library.id, rootPath.absolutePathString())
        return library to rootDirectory
    }

    fun createMovieDirectory(movieName: String, year: Int): Path {
        val movieDir = fs.getPath("/movies/$movieName ($year)").createDirectory()
        movieDir.resolve("$movieName ($year).mp4").createFile()
        return movieDir
    }

    test("match single movie with fake provider") {
        val (library, rootDirectory) = setupMovieLibrary()

        // Setup fake provider with matching data
        fakeProvider.addMovie(tmdbId = 603, title = "The Matrix", year = 1999)

        // Create matching file structure
        createMovieDirectory("The Matrix", 1999)

        // Scan the directory
        val scanResult = mediaFileScanner.scan(fs.getPath("/movies"))
        scanResult.shouldBeInstanceOf<MediaScanResult.Success>()

        // Process the movie directory
        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(movieDirectory, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        val match = result.matches.first().shouldBeInstanceOf<MetadataMatch.MovieMatch>()
        match.movie.title shouldBe "The Matrix"

        // Verify media links were updated
        val links = mediaLinkDao.findByDirectoryId(movieDirectory.id)
            .filter { it.descriptor == Descriptor.VIDEO }

        links shouldHaveSize 1
        links.first().rootMetadataId.shouldNotBeNull()
        links.first().metadataId.shouldNotBeNull()
    }

    test("match multiple movies from library root with fake provider") {
        val (library, rootDirectory) = setupMovieLibrary()

        // Setup multiple movies in fake provider
        fakeProvider.addMovie(tmdbId = 603, title = "The Matrix", year = 1999)
        fakeProvider.addMovie(tmdbId = 604, title = "The Matrix Reloaded", year = 2003)

        // Create file structures
        createMovieDirectory("The Matrix", 1999)
        createMovieDirectory("The Matrix Reloaded", 2003)

        // Scan
        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        // Process from library root
        val results = processor.findMetadataMatches(rootDirectory, import = true)

        results shouldHaveSize 2
        results.shouldForAll { it.shouldBeInstanceOf<MediaLinkMatchResult.Success>() }

        val movieTitles = results
            .filterIsInstance<MediaLinkMatchResult.Success>()
            .flatMap { it.matches }
            .filterIsInstance<MetadataMatch.MovieMatch>()
            .map { it.movie.title }

        movieTitles shouldContain "The Matrix"
        movieTitles shouldContain "The Matrix Reloaded"
    }

    test("returns NoMatchesFound when provider has no matching movie") {
        val (library, rootDirectory) = setupMovieLibrary()

        // Don't add any movies to fake provider
        createMovieDirectory("Unknown Movie", 2020)

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(movieDirectory, import = true)

        results shouldHaveSize 1
        results.first().shouldBeInstanceOf<MediaLinkMatchResult.NoMatchesFound>()
    }

    test("returns NoSupportedFiles for empty movie directory") {
        val (library, rootDirectory) = setupMovieLibrary()

        fakeProvider.addMovie(tmdbId = 1234, title = "Empty Movie", year = 2020)

        // Create movie directory without any video files
        val movieDir = fs.getPath("/movies/Empty Movie (2020)").createDirectory()

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(movieDirectory, import = true)

        results shouldHaveSize 1
        results.first().shouldBeInstanceOf<MediaLinkMatchResult.NoSupportedFiles>()
    }

    test("handles provider search failure") {
        val (library, rootDirectory) = setupMovieLibrary()

        // Configure provider to fail
        fakeProvider.shouldFailSearch = true
        fakeProvider.searchErrorMessage = "API rate limit exceeded"

        createMovieDirectory("Any Movie", 2020)

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(movieDirectory, import = true)

        results shouldHaveSize 1
        results.first().shouldBeInstanceOf<MediaLinkMatchResult.NoMatchesFound>()
    }

    test("handles provider import failure") {
        val (library, rootDirectory) = setupMovieLibrary()

        fakeProvider.addMovie(tmdbId = 6666, title = "Import Fail Movie", year = 2021)
        fakeProvider.shouldFailImport = true
        fakeProvider.importErrorMessage = "Database connection lost"

        createMovieDirectory("Import Fail Movie", 2021)

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(movieDirectory, import = true)

        results shouldHaveSize 1
        // Import fails, so ImportFailed is returned with details
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.ImportFailed>()
        result.reason shouldContain "Import Fail Movie"
    }

    test("search without import returns multiple matches") {
        val (library, rootDirectory) = setupMovieLibrary()

        // Add multiple potential matches with same year
        fakeProvider.addMovie(tmdbId = 7001, title = "Inception", year = 2010)
        fakeProvider.addMovie(tmdbId = 7002, title = "Inception Extended", year = 2010)

        createMovieDirectory("Inception", 2010)

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()

        // Search without import (import = false)
        val results = processor.findMetadataMatches(movieDirectory, import = false)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        // Should return multiple potential matches when not importing
        result.matches.size shouldBe 2
    }

    test("verifies provider search calls are tracked") {
        val (library, rootDirectory) = setupMovieLibrary()

        fakeProvider.addMovie(tmdbId = 8888, title = "Tracked Movie", year = 2022)

        createMovieDirectory("Tracked Movie", 2022)

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()

        fakeProvider.searchCalls.shouldBeEmpty()

        processor.findMetadataMatches(movieDirectory, import = true)

        fakeProvider.searchCalls.shouldNotBeEmpty()
        fakeProvider.searchCalls.first().query shouldBe "Tracked Movie"
        fakeProvider.searchCalls.first().year shouldBe 2022
    }

    test("handles movies with year in title correctly") {
        val (library, rootDirectory) = setupMovieLibrary()

        fakeProvider.addMovie(tmdbId = 9001, title = "2001", year = 1968)

        // Create directory with year in movie title
        val movieDir = fs.getPath("/movies/2001 (1968)").createDirectory()
        movieDir.resolve("2001 (1968).mp4").createFile()

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(movieDirectory, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()
        result.matches.first().shouldBeInstanceOf<MetadataMatch.MovieMatch>()
            .movie.title shouldBe "2001"
    }

    test("preserves existing metadata when rescanning") {
        val (library, rootDirectory) = setupMovieLibrary()

        fakeProvider.addMovie(tmdbId = 11001, title = "Rescan Movie", year = 2018)

        createMovieDirectory("Rescan Movie", 2018)

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()

        // First scan and import
        val firstResults = processor.findMetadataMatches(movieDirectory, import = true)
        firstResults shouldHaveSize 1
        firstResults.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        // Get metadata IDs from first scan
        val firstLinkMetadataIds = mediaLinkDao.findByDirectoryId(movieDirectory.id)
            .filter { it.descriptor == Descriptor.VIDEO }
            .mapNotNull { it.metadataId }

        // Second scan and import (simulating rescan)
        val secondResults = processor.findMetadataMatches(movieDirectory, import = true)
        secondResults shouldHaveSize 1

        // Verify metadata IDs are preserved (should be same as first scan)
        val secondLinkMetadataIds = mediaLinkDao.findByDirectoryId(movieDirectory.id)
            .filter { it.descriptor == Descriptor.VIDEO }
            .mapNotNull { it.metadataId }

        secondLinkMetadataIds shouldBe firstLinkMetadataIds
    }

    test("handles movie with standard file naming") {
        val (library, rootDirectory) = setupMovieLibrary()

        fakeProvider.addMovie(tmdbId = 12001, title = "Gladiator", year = 2000)

        // Use the standard createMovieDirectory helper
        createMovieDirectory("Gladiator", 2000)

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(movieDirectory, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        result.matches.first().shouldBeInstanceOf<MetadataMatch.MovieMatch>()
            .movie.title shouldBe "Gladiator"

        // Video file should be linked to movie metadata
        val links = mediaLinkDao.findByDirectoryId(movieDirectory.id)
            .filter { it.descriptor == Descriptor.VIDEO }

        links shouldHaveSize 1
        links.first().rootMetadataId.shouldNotBeNull()
        links.first().metadataId.shouldNotBeNull()
    }

    test("matches movie with partial title match") {
        val (library, rootDirectory) = setupMovieLibrary()

        // Provider has "The Dark Knight" but directory says "Dark Knight"
        fakeProvider.addMovie(tmdbId = 155, title = "The Dark Knight", year = 2008)

        createMovieDirectory("Dark Knight", 2008)

        mediaFileScanner.scan(fs.getPath("/movies")).shouldBeInstanceOf<MediaScanResult.Success>()

        val movieDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(movieDirectory, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()
        result.matches.first().shouldBeInstanceOf<MetadataMatch.MovieMatch>()
            .movie.title shouldBe "The Dark Knight"
    }
})