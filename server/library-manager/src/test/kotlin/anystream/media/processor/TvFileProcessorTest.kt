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
import anystream.media.createTvDirectory
import anystream.media.createEdgeCaseTvDirectory
import anystream.media.scanner.MediaFileScanner
import anystream.metadata.ImageStore
import anystream.metadata.MetadataService
import anystream.metadata.providers.FakeMetadataProvider
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.metadata.providers.createFakeMetadataProvider
import anystream.metadata.providers.tvShow
import anystream.models.Descriptor
import anystream.models.Episode
import anystream.models.MediaKind
import anystream.models.api.MediaLinkMatchResult
import anystream.models.api.MediaScanResult
import anystream.models.api.MetadataMatch
import app.moviebase.tmdb.Tmdb3
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
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile

class TvFileProcessorTest : FunSpec({

    val db by bindTestDatabase()
    val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val metadataDao by bindForTest({ MetadataDao(db) })
    val libraryDao by bindForTest({ LibraryDao(db) })
    val queries by bindForTest({
        val tagsDao = TagsDao(db)
        val playbackStatesDao = PlaybackStatesDao(db)
        val mediaLinkDao = MediaLinkDao(db)
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
        TvFileProcessor(
            metadataService = metadataService,
            mediaLinkDao = mediaLinkDao,
            libraryDao = libraryDao,
            fs = fs,
        )
    })

    test("match multiple shows from library root") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val library = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.TV }
            .shouldNotBeNull()

        val (mediaRootPath, showPaths) = fs.createTvDirectory()
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
                .shouldBeInstanceOf<anystream.models.api.MetadataMatch.TvShowMatch>()

            val episodeIds = match.episodes.map(Episode::id)
            libraryDao.fetchChildDirectories(directory.id)
                .shouldNotBeEmpty()
                .flatMap { mediaLinkDao.findByDirectoryId(it.id) }
                // TODO: Support other files like subtitles and posters
                .filter { it.descriptor == Descriptor.VIDEO }
                .shouldForAll {
                    it.rootMetadataId
                        .shouldNotBeNull()
                        .shouldBeEqual(match.tvShow.id)

                    it.metadataId.shouldNotBeNull()

                    episodeIds.shouldContain(it.metadataId)
                }
        }
    }
})

/**
 * Tests using FakeMetadataProvider for deterministic E2E testing without external API calls.
 */
class TvFileProcessorFakeProviderTest : FunSpec({

    val db by bindTestDatabase()
    val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val metadataDao by bindForTest({ MetadataDao(db) })
    val libraryDao by bindForTest({ LibraryDao(db) })
    val fs by bindFileSystem()

    lateinit var fakeProvider: FakeMetadataProvider
    lateinit var metadataService: MetadataService
    lateinit var processor: TvFileProcessor
    lateinit var mediaFileScanner: MediaFileScanner

    beforeTest {
        fakeProvider = createFakeMetadataProvider(metadataDao)
        val imageStore = ImageStore(fs.getPath("/test").createDirectory(), HttpClient())
        metadataService = MetadataService(listOf(fakeProvider), metadataDao, imageStore)
        processor = TvFileProcessor(
            metadataService = metadataService,
            mediaLinkDao = mediaLinkDao,
            libraryDao = libraryDao,
            fs = fs,
        )
        mediaFileScanner = MediaFileScanner(mediaLinkDao, libraryDao, fs)
    }

    suspend fun setupTvLibrary(): Pair<anystream.models.Library, anystream.models.Directory> {
        libraryDao.insertDefaultLibraries()
        val library = libraryDao.fetchLibraries().first { it.mediaKind == MediaKind.TV }
        val rootPath = fs.getPath("/tv").createDirectory()
        val rootDirectory = libraryDao.insertDirectory(null, library.id, rootPath.absolutePathString())
        return library to rootDirectory
    }

    fun createShowDirectory(showName: String, seasons: Map<Int, List<Int>>): Path {
        val showDir = fs.getPath("/tv/$showName").createDirectory()
        seasons.forEach { (seasonNum, episodes) ->
            val seasonDir = showDir.resolve("Season ${seasonNum.toString().padStart(2, '0')}").createDirectory()
            episodes.forEach { epNum ->
                val epName = "$showName - S${seasonNum.toString().padStart(2, '0')}E${epNum.toString().padStart(2, '0')}.mp4"
                seasonDir.resolve(epName).createFile()
            }
        }
        return showDir
    }

    test("match single TV show with fake provider") {
        val (library, rootDirectory) = setupTvLibrary()

        // Setup fake provider with matching data
        fakeProvider.tvShow(tmdbId = 1399, name = "Game of Thrones", year = 2011) {
            season(1) {
                episodes(10)
            }
            season(2) {
                episodes(10)
            }
        }

        // Create matching file structure
        createShowDirectory("Game of Thrones (2011)", mapOf(1 to (1..10).toList(), 2 to (1..10).toList()))

        // Scan the directory
        val scanResult = mediaFileScanner.scan(fs.getPath("/tv"))
        scanResult.shouldBeInstanceOf<MediaScanResult.Success>()

        // Process the show directory
        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(showDirectory, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        val match = result.matches.first().shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
        match.tvShow.name shouldBe "Game of Thrones"
        match.seasons shouldHaveSize 2
        match.episodes shouldHaveSize 20

        // Verify media links were updated
        val seasonDirectories = libraryDao.fetchChildDirectories(showDirectory.id)
        seasonDirectories shouldHaveSize 2

        seasonDirectories.forEach { seasonDir ->
            val links = mediaLinkDao.findByDirectoryId(seasonDir.id)
                .filter { it.descriptor == Descriptor.VIDEO }

            links.shouldForAll { link ->
                link.rootMetadataId.shouldNotBeNull()
                link.metadataId.shouldNotBeNull()
            }
        }
    }

    test("match multiple shows from library root with fake provider") {
        val (library, rootDirectory) = setupTvLibrary()

        // Setup multiple shows in fake provider
        fakeProvider.tvShow(tmdbId = 1399, name = "Game of Thrones", year = 2011) {
            season(1) { episodes(3) }
        }
        fakeProvider.tvShow(tmdbId = 1396, name = "Breaking Bad", year = 2008) {
            season(1) { episodes(3) }
        }

        // Create file structures
        createShowDirectory("Game of Thrones (2011)", mapOf(1 to listOf(1, 2, 3)))
        createShowDirectory("Breaking Bad (2008)", mapOf(1 to listOf(1, 2, 3)))

        // Scan
        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        // Process from library root
        val results = processor.findMetadataMatches(rootDirectory, import = true)

        results shouldHaveSize 2
        results.shouldForAll { it.shouldBeInstanceOf<MediaLinkMatchResult.Success>() }

        val showNames = results
            .filterIsInstance<MediaLinkMatchResult.Success>()
            .flatMap { it.matches }
            .filterIsInstance<MetadataMatch.TvShowMatch>()
            .map { it.tvShow.name }

        showNames shouldContain "Game of Thrones"
        showNames shouldContain "Breaking Bad"
    }

    test("returns NoMatchesFound when provider has no matching show") {
        val (library, rootDirectory) = setupTvLibrary()

        // Don't add any shows to fake provider
        createShowDirectory("Unknown Show (2020)", mapOf(1 to listOf(1, 2)))

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(showDirectory, import = true)

        results shouldHaveSize 1
        results.first().shouldBeInstanceOf<MediaLinkMatchResult.NoMatchesFound>()
    }

    test("returns NoSupportedFiles for empty show directory") {
        val (library, rootDirectory) = setupTvLibrary()

        fakeProvider.tvShow(tmdbId = 1234, name = "Empty Show", year = 2020) {
            season(1) { episodes(5) }
        }

        // Create show directory with only season folder but no video files
        val showDir = fs.getPath("/tv/Empty Show (2020)").createDirectory()
        showDir.resolve("Season 01").createDirectory()

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(showDirectory, import = true)

        results shouldHaveSize 1
        results.first().shouldBeInstanceOf<MediaLinkMatchResult.NoSupportedFiles>()
    }

    test("handles mismatched episode numbers gracefully") {
        val (library, rootDirectory) = setupTvLibrary()

        // Provider only has episodes 1-3
        fakeProvider.tvShow(tmdbId = 5555, name = "Partial Show", year = 2019) {
            season(1) { episodes(3) }
        }

        // File system has episodes 1-5 (episodes 4 and 5 won't match)
        createShowDirectory("Partial Show (2019)", mapOf(1 to listOf(1, 2, 3, 4, 5)))

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(showDirectory, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        // Only 3 episodes should be linked (those that have metadata)
        val seasonDir = libraryDao.fetchChildDirectories(showDirectory.id).first()
        val linkedLinks = mediaLinkDao.findByDirectoryId(seasonDir.id)
            .filter { it.descriptor == Descriptor.VIDEO && it.metadataId != null }
        linkedLinks shouldHaveSize 3

        // 2 links should not have metadata
        val unlinkedLinks = mediaLinkDao.findByDirectoryId(seasonDir.id)
            .filter { it.descriptor == Descriptor.VIDEO && it.metadataId == null }
        unlinkedLinks shouldHaveSize 2
    }

    test("handles provider search failure") {
        val (library, rootDirectory) = setupTvLibrary()

        // Configure provider to fail
        fakeProvider.shouldFailSearch = true
        fakeProvider.searchErrorMessage = "API rate limit exceeded"

        createShowDirectory("Any Show (2020)", mapOf(1 to listOf(1, 2)))

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(showDirectory, import = true)

        results shouldHaveSize 1
        results.first().shouldBeInstanceOf<MediaLinkMatchResult.NoMatchesFound>()
    }

    test("handles provider import failure") {
        val (library, rootDirectory) = setupTvLibrary()

        fakeProvider.tvShow(tmdbId = 6666, name = "Import Fail Show", year = 2021) {
            season(1) { episodes(2) }
        }
        fakeProvider.shouldFailImport = true
        fakeProvider.importErrorMessage = "Database connection lost"

        createShowDirectory("Import Fail Show (2021)", mapOf(1 to listOf(1, 2)))

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(showDirectory, import = true)

        results shouldHaveSize 1
        // Import fails, returns ImportFailed with reason
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.ImportFailed>()
        result.reason shouldContain "Import Fail Show"
    }

    test("search without import returns multiple matches") {
        val (library, rootDirectory) = setupTvLibrary()

        // Add multiple potential matches with the same year
        fakeProvider.tvShow(tmdbId = 7001, name = "The Office US", year = 2005) {
            season(1) { episodes(5) }
        }
        fakeProvider.tvShow(tmdbId = 7002, name = "The Office Documentary", year = 2005) {
            season(1) { episodes(5) }
        }

        createShowDirectory("The Office (2005)", mapOf(1 to listOf(1, 2, 3)))

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()

        // Search without import (import = false)
        val results = processor.findMetadataMatches(showDirectory, import = false)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        // Should return multiple potential matches when not importing
        result.matches.size shouldBe 2
    }

    test("verifies provider search calls are tracked") {
        val (library, rootDirectory) = setupTvLibrary()

        fakeProvider.tvShow(tmdbId = 8888, name = "Tracked Show", year = 2022) {
            season(1) { episodes(2) }
        }

        createShowDirectory("Tracked Show (2022)", mapOf(1 to listOf(1, 2)))

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()

        fakeProvider.searchCalls.shouldBeEmpty()

        processor.findMetadataMatches(showDirectory, import = true)

        fakeProvider.searchCalls.shouldNotBeEmpty()
        fakeProvider.searchCalls.first().query shouldBe "Tracked Show"
        fakeProvider.searchCalls.first().year shouldBe 2022
    }

    test("handles shows with year in title correctly") {
        val (library, rootDirectory) = setupTvLibrary()

        fakeProvider.tvShow(tmdbId = 9001, name = "2001", year = 2001) {
            season(1) { episodes(3) }
        }

        // Create directory with year in show name
        val showDir = fs.getPath("/tv/2001 (2001)").createDirectory()
        val seasonDir = showDir.resolve("Season 01").createDirectory()
        (1..3).forEach { ep ->
            seasonDir.resolve("2001 (2001) - S01E${ep.toString().padStart(2, '0')}.mp4").createFile()
        }

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(showDirectory, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()
        result.matches.first().shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
            .tvShow.name shouldBe "2001"
    }

    test("links episodes correctly across multiple seasons") {
        val (library, rootDirectory) = setupTvLibrary()

        fakeProvider.tvShow(tmdbId = 10001, name = "Multi Season Show", year = 2015) {
            season(1) { episodes(5) }
            season(2) { episodes(8) }
            season(3) { episodes(10) }
        }

        createShowDirectory("Multi Season Show (2015)", mapOf(
            1 to (1..5).toList(),
            2 to (1..8).toList(),
            3 to (1..10).toList(),
        ))

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val results = processor.findMetadataMatches(showDirectory, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        val match = result.matches.first().shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
        match.seasons shouldHaveSize 3
        match.episodes shouldHaveSize 23

        // Verify all episodes were linked
        val seasonDirectories = libraryDao.fetchChildDirectories(showDirectory.id)
        seasonDirectories shouldHaveSize 3

        val linkedCount = seasonDirectories.sumOf { seasonDir ->
            mediaLinkDao.findByDirectoryId(seasonDir.id)
                .count { it.descriptor == Descriptor.VIDEO && it.metadataId != null }
        }
        linkedCount shouldBe 23
    }

    test("preserves existing metadata when rescanning") {
        val (library, rootDirectory) = setupTvLibrary()

        fakeProvider.tvShow(tmdbId = 11001, name = "Rescan Show", year = 2018) {
            season(1) { episodes(3) }
        }

        createShowDirectory("Rescan Show (2018)", mapOf(1 to listOf(1, 2, 3)))

        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()

        // First scan and import
        val firstResults = processor.findMetadataMatches(showDirectory, import = true)
        firstResults shouldHaveSize 1
        firstResults.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        // Get metadata IDs from first scan
        val seasonDir = libraryDao.fetchChildDirectories(showDirectory.id).first()
        val firstLinkMetadataIds = mediaLinkDao.findByDirectoryId(seasonDir.id)
            .filter { it.descriptor == Descriptor.VIDEO }
            .mapNotNull { it.metadataId }

        // Second scan and import (simulating rescan)
        val secondResults = processor.findMetadataMatches(showDirectory, import = true)
        secondResults shouldHaveSize 1

        // Verify metadata IDs are preserved (should be same as first scan)
        val secondLinkMetadataIds = mediaLinkDao.findByDirectoryId(seasonDir.id)
            .filter { it.descriptor == Descriptor.VIDEO }
            .mapNotNull { it.metadataId }

        secondLinkMetadataIds shouldBe firstLinkMetadataIds
    }

    test("match show from season folder (nested scan)") {
        val (library, rootDirectory) = setupTvLibrary()

        fakeProvider.tvShow(tmdbId = 12001, name = "Nested Scan Show", year = 2020) {
            season(1) { episodes(5) }
            season(2) { episodes(5) }
        }

        createShowDirectory("Nested Scan Show (2020)", mapOf(1 to (1..5).toList(), 2 to (1..5).toList()))

        // Scan the full library first to create directory records
        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        // Get the season folder (nested directory)
        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val seasonDirectories = libraryDao.fetchChildDirectories(showDirectory.id)
        seasonDirectories shouldHaveSize 2

        val seasonFolder = seasonDirectories.first()

        // Process from season folder - should walk up to show folder and process
        val results = processor.findMetadataMatches(seasonFolder, import = true)

        results shouldHaveSize 1
        val result = results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()

        val match = result.matches.first().shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
        match.tvShow.name shouldBe "Nested Scan Show"
        match.seasons shouldHaveSize 2
        match.episodes shouldHaveSize 10

        // Verify all episodes were linked (from both seasons, not just the one we started from)
        val linkedCount = seasonDirectories.sumOf { seasonDir ->
            mediaLinkDao.findByDirectoryId(seasonDir.id)
                .count { it.descriptor == Descriptor.VIDEO && it.metadataId != null }
        }
        linkedCount shouldBe 10
    }

    test("match individual episode file") {
        val (library, rootDirectory) = setupTvLibrary()

        fakeProvider.tvShow(tmdbId = 13001, name = "Single Episode Show", year = 2019) {
            season(1) { episodes(10) }
            season(2) { episodes(10) }
        }

        createShowDirectory("Single Episode Show (2019)", mapOf(1 to (1..10).toList(), 2 to (1..10).toList()))

        // Scan the full library first to create media link records
        mediaFileScanner.scan(fs.getPath("/tv")).shouldBeInstanceOf<MediaScanResult.Success>()

        // Get a specific episode file media link
        val showDirectory = libraryDao.fetchChildDirectories(rootDirectory.id).first()
        val season1Directory = libraryDao.fetchChildDirectories(showDirectory.id)
            .first { it.filePath.contains("Season 01") }

        val episodeLinks = mediaLinkDao.findByDirectoryId(season1Directory.id)
            .filter { it.descriptor == Descriptor.VIDEO }
        episodeLinks shouldHaveSize 10

        // Get the S01E05 episode link
        val episode5Link = episodeLinks.first { it.filePath?.contains("S01E05") == true }
        episode5Link.metadataId.shouldBeNull() // Not linked yet

        // Match the individual episode file
        val result = processor.findMetadataMatches(episode5Link, import = true)

        result.shouldBeInstanceOf<MediaLinkMatchResult.Success>()
        val match = result.matches.first().shouldBeInstanceOf<MetadataMatch.TvShowMatch>()
        match.tvShow.name shouldBe "Single Episode Show"

        // Verify the episode was linked
        val updatedLink = mediaLinkDao.findByFilePath(episode5Link.filePath!!)
        updatedLink.shouldNotBeNull()
        updatedLink.metadataId.shouldNotBeNull()
        updatedLink.rootMetadataId.shouldNotBeNull()

        // Verify it's linked to the correct episode
        val episodeMetadata = match.episodes.find { it.seasonNumber == 1 && it.number == 5 }
        episodeMetadata.shouldNotBeNull()
        updatedLink.metadataId shouldBe episodeMetadata.id
        updatedLink.rootMetadataId shouldBe match.tvShow.id
    }
})