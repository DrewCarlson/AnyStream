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
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.*
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import java.nio.file.FileSystem
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively

class MediaFileScannerTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val libraryDao by bindForTest({ LibraryDao(db) })
    val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val fs by bindForTest({ Jimfs.newFileSystem(Configuration.unix()) }, FileSystem::close)
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

        libraryDao.fetchDirectories(movieLibrary.id)
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

        libraryDao.fetchDirectories(library.id)
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
})
