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
package anystream.media

import anystream.db.*
import anystream.media.scanner.MediaFileScanner
import anystream.models.MediaKind
import anystream.models.api.MediaScanResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.*
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively

class MediaFileScannerTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val libraryDao by bindForTest({ LibraryDao(db) })
    val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val fs by bindFileSystem()
    val mediaFileScanner by bindForTest({ MediaFileScanner(mediaLinkDao, libraryDao, fs) })

    listOf(MediaKind.MOVIE, MediaKind.TV).forEach { mediaKind ->
        suspend fun createLibrary() {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao.fetchLibraries()
                .firstOrNull { it.mediaKind == mediaKind }
                .shouldNotBeNull()

            val (libraryRootPath, _) = when (mediaKind) {
                MediaKind.MOVIE -> fs.createMovieDirectory()
                MediaKind.TV -> fs.createTvDirectory()
                else -> error("No test filesystem mapped for media kind: $mediaKind")
            }
            libraryDao.insertDirectory(
                parentId = null,
                libraryId = library.id,
                path = libraryRootPath.absolutePathString(),
            )
        }

        test("scan - $mediaKind directory outside of library roots") {
            createLibrary()

            val untrackedRootPath = fs.getPath("/untracked-root").createDirectory()
            val untrackedMediaPath = untrackedRootPath.resolve("/untracked-media").createDirectory()

            mediaFileScanner.scan(untrackedRootPath)
                .shouldBeInstanceOf<MediaScanResult.ErrorNotInLibrary>()

            mediaFileScanner.scan(untrackedMediaPath)
                .shouldBeInstanceOf<MediaScanResult.ErrorNotInLibrary>()
        }

        test("scan - $mediaKind files outside of library roots") {
            val untrackedRootPath = fs.getPath("/untracked-root").createDirectory()
            val untrackedMediaPath = untrackedRootPath.resolve("/untracked-media").createDirectory()
            val untrackedMediaFilePaths = when (mediaKind) {
                MediaKind.MOVIE,
                MediaKind.TV -> listOf(
                    VIDEO_EXTENSIONS.random(),
                    SUBTITLE_EXTENSIONS.random()
                )

                MediaKind.MUSIC -> listOf(AUDIO_EXTENSIONS.random())
                else -> error("No test file extensions mapped for media kind: $mediaKind")
            }.map { fileExtension ->
                untrackedMediaPath
                    .resolve("test-file.$fileExtension")
                    .createFile()
            }

            mediaFileScanner.scan(untrackedRootPath)
                .shouldBeInstanceOf<MediaScanResult.ErrorNotInLibrary>()

            untrackedMediaFilePaths.forEach { path ->
                mediaFileScanner.scan(path)
                    .shouldBeInstanceOf<MediaScanResult.ErrorNotInLibrary>()
            }
        }
    }

    test("scan - MOVIE root directory") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val movieLibrary = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.MOVIE }
            .shouldNotBeNull()

        val (moviesRoot, movieFolders) = fs.createMovieDirectory()

        val mediaDirectory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = movieLibrary.id,
            path = moviesRoot.absolutePathString(),
        )

        mediaFileScanner.scan(directory = mediaDirectory).shouldBeInstanceOf<MediaScanResult.Success>()

        libraryDao.fetchDirectoriesByLibrary(movieLibrary.id)
            .shouldHaveSize(movieFolders.size + 1) // add 1 for root directory
            .map { it.filePath }
            .shouldContainExactly(
                (listOf(moviesRoot) + movieFolders.keys)
                    .map { it.absolutePathString() }
            )

        val movieFiles = movieFolders
            .flatMap { (_, files) -> files }
            .shouldNotBeEmpty()

        val mediaLinks = mediaLinkDao.all()
            .shouldHaveSize(movieFiles.size)

        mediaLinks.map { it.filePath }
            .shouldContainExactlyInAnyOrder(
                movieFiles.map { it.absolutePathString() }
            )
    }

    test("scan - TV root directory") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val library = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.TV }
            .shouldNotBeNull()

        val (mediaRootPath, showDirectories) = fs.createTvDirectory()
        val allMediaPaths = showDirectories
            .flatMap { (showPath, seasonDirectories) ->
                listOf(showPath) + seasonDirectories.keys
            }

        libraryDao.insertDirectory(
            parentId = null,
            libraryId = library.id,
            path = mediaRootPath.absolutePathString(),
        )

        mediaFileScanner.scan(path = mediaRootPath).shouldBeInstanceOf<MediaScanResult.Success>()

        libraryDao.fetchDirectoriesByLibrary(library.id)
            .shouldHaveSize(allMediaPaths.size + 1) // add 1 for root directory
            .map { it.filePath }
            .shouldContainExactlyInAnyOrder(
                (listOf(mediaRootPath) + allMediaPaths)
                    .map { it.absolutePathString() }
            )

        val allFiles = showDirectories
            .flatMap { (_, seasonDirectories) ->
                seasonDirectories.flatMap { (_, files) -> files }
            }

        val mediaLinks = mediaLinkDao.all()
            .shouldHaveSize(allFiles.size)

        mediaLinks.map { it.filePath }
            .shouldContainExactlyInAnyOrder(
                allFiles.map { it.absolutePathString() }
            )
    }

    test("scan - MOVIE prune deleted files") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val movieLibrary = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.MOVIE }
            .shouldNotBeNull()

        val (moviesRoot, movieDirectories) = fs.createMovieDirectory()

        val mediaDirectory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = movieLibrary.id,
            path = moviesRoot.absolutePathString(),
        )

        mediaFileScanner.scan(directory = mediaDirectory).shouldBeInstanceOf<MediaScanResult.Success>()

        val deletedMovieDirectory = movieDirectories.entries.first()
        val deletedMovieDirectoryPath = deletedMovieDirectory.key
            .apply { deleteRecursively() }
            .absolutePathString()
        val deletedMovieDirectoryRecord = libraryDao.fetchDirectoryByPath(deletedMovieDirectoryPath)
            .shouldNotBeNull()
        val deletedMovieVideoMediaLinks = mediaLinkDao.findByBasePath(deletedMovieDirectoryPath)
            .shouldNotBeEmpty()

        val folderDeleteResult = mediaFileScanner.scan(directory = deletedMovieDirectoryRecord)
            .shouldBeInstanceOf<MediaScanResult.Success>()

        folderDeleteResult.mediaLinks
            .removedIds
            .shouldContainExactlyInAnyOrder(deletedMovieVideoMediaLinks.map { it.id })

        folderDeleteResult.directories
            .removedIds
            .shouldContainExactly(deletedMovieDirectoryRecord.id)
    }

    test("scan - empty directory returns success with no links") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val movieLibrary = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.MOVIE }
            .shouldNotBeNull()

        val emptyRoot = fs.getPath("/empty-movies").createDirectory()
        val emptySubdir = emptyRoot.resolve("Empty Movie Folder").createDirectory()

        val mediaDirectory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = movieLibrary.id,
            path = emptyRoot.absolutePathString(),
        )

        val result = mediaFileScanner.scan(directory = mediaDirectory)
            .shouldBeInstanceOf<MediaScanResult.Success>()

        result.mediaLinks.addedIds.shouldBeEmpty()
        libraryDao.fetchDirectoriesByLibrary(movieLibrary.id).shouldHaveSize(2)
    }

    test("scan - incremental rescan detects new files") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val movieLibrary = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.MOVIE }
            .shouldNotBeNull()

        val moviesRoot = fs.getPath("/incremental-movies").createDirectory()
        val movie1Dir = moviesRoot.resolve("Movie One (2020)").createDirectory()
        movie1Dir.resolve("Movie One (2020).mp4").createFile()

        val mediaDirectory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = movieLibrary.id,
            path = moviesRoot.absolutePathString(),
        )

        // First scan
        val firstResult = mediaFileScanner.scan(directory = mediaDirectory)
            .shouldBeInstanceOf<MediaScanResult.Success>()

        firstResult.mediaLinks.addedIds.shouldHaveSize(1)

        // Add new movie
        val movie2Dir = moviesRoot.resolve("Movie Two (2021)").createDirectory()
        movie2Dir.resolve("Movie Two (2021).mp4").createFile()

        // Rescan
        val secondResult = mediaFileScanner.scan(directory = mediaDirectory)
            .shouldBeInstanceOf<MediaScanResult.Success>()

        secondResult.mediaLinks.addedIds.shouldHaveSize(1)
        secondResult.mediaLinks.removedIds.shouldBeEmpty()
        mediaLinkDao.all().shouldHaveSize(2)
    }

    test("scan - handles TV show with multiple seasons") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val tvLibrary = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.TV }
            .shouldNotBeNull()

        val tvRoot = fs.getPath("/multi-season-tv").createDirectory()
        val showDir = tvRoot.resolve("Test Show").createDirectory()

        // Create 3 seasons with varying episode counts
        val season1 = showDir.resolve("Season 1").createDirectory()
        (1..5).forEach { ep -> season1.resolve("S01E0$ep.mp4").createFile() }

        val season2 = showDir.resolve("Season 2").createDirectory()
        (1..8).forEach { ep ->
            val epNum = if (ep < 10) "0$ep" else "$ep"
            season2.resolve("S02E$epNum.mp4").createFile()
        }

        val season3 = showDir.resolve("Season 3").createDirectory()
        (1..3).forEach { ep -> season3.resolve("S03E0$ep.mp4").createFile() }

        val mediaDirectory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = tvLibrary.id,
            path = tvRoot.absolutePathString(),
        )

        val result = mediaFileScanner.scan(directory = mediaDirectory)
            .shouldBeInstanceOf<MediaScanResult.Success>()

        // 5 + 8 + 3 = 16 episode files
        result.mediaLinks.addedIds.shouldHaveSize(16)

        // Root + Show + 3 Seasons = 5 directories
        libraryDao.fetchDirectoriesByLibrary(tvLibrary.id).shouldHaveSize(5)
    }

    test("scan - handles mixed file types in movie directory") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val movieLibrary = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.MOVIE }
            .shouldNotBeNull()

        val moviesRoot = fs.getPath("/mixed-movies").createDirectory()
        val movieDir = moviesRoot.resolve("Mixed Movie (2020)").createDirectory()

        // Video file
        movieDir.resolve("Mixed Movie (2020).mkv").createFile()
        // Subtitle files
        movieDir.resolve("Mixed Movie (2020).srt").createFile()
        movieDir.resolve("Mixed Movie (2020).en.srt").createFile()
        // Poster (should be ignored by scanner)
        movieDir.resolve("poster.jpg").createFile()

        val mediaDirectory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = movieLibrary.id,
            path = moviesRoot.absolutePathString(),
        )

        val result = mediaFileScanner.scan(directory = mediaDirectory)
            .shouldBeInstanceOf<MediaScanResult.Success>()

        // 1 video + 2 subtitles = 3 media links
        result.mediaLinks.addedIds.shouldHaveSize(3)

        val links = mediaLinkDao.all()
        links.shouldHaveSize(3)
    }

    test("scan - rescan after adding new movie directory") {
        libraryDao.insertDefaultLibraries().shouldBeTrue()

        val movieLibrary = libraryDao.fetchLibraries()
            .firstOrNull { it.mediaKind == MediaKind.MOVIE }
            .shouldNotBeNull()

        val moviesRoot = fs.getPath("/change-movies").createDirectory()
        val movie1Dir = moviesRoot.resolve("Movie One (2020)").createDirectory()
        movie1Dir.resolve("Movie One (2020).mp4").createFile()

        val mediaDirectory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = movieLibrary.id,
            path = moviesRoot.absolutePathString(),
        )

        // First scan
        mediaFileScanner.scan(directory = mediaDirectory)
            .shouldBeInstanceOf<MediaScanResult.Success>()

        val firstLinks = mediaLinkDao.all()
        firstLinks.shouldHaveSize(1)

        // Add new movie
        val movie2Dir = moviesRoot.resolve("Movie Two (2021)").createDirectory()
        movie2Dir.resolve("Movie Two (2021).mp4").createFile()

        // Rescan root directory
        val secondResult = mediaFileScanner.scan(directory = mediaDirectory)
            .shouldBeInstanceOf<MediaScanResult.Success>()

        // Should detect the new movie file
        secondResult.mediaLinks.addedIds.shouldHaveSize(1)
        mediaLinkDao.all().shouldHaveSize(2)
    }
})
