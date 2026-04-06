/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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

import anystream.db.LibraryDao
import anystream.db.MediaLinkDao
import anystream.db.bindFileSystem
import anystream.db.bindForTest
import anystream.db.bindTestDatabase
import anystream.media.analyzer.MediaFileAnalyzer
import anystream.media.file.FileNameParser
import anystream.media.file.ParsedFileNameResult
import anystream.media.processor.MediaFileProcessor
import anystream.media.scanner.MediaFileScanner
import anystream.models.*
import anystream.models.api.*
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class RefreshMetadataTest :
    FunSpec({

        val db: DSLContext by bindTestDatabase()
        val libraryDao by bindForTest({ LibraryDao(db) })
        val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
        val fs by bindFileSystem()
        val mediaFileScanner by bindForTest({ MediaFileScanner(mediaLinkDao, libraryDao, fs) })

        fun createTestMovie(id: String = ObjectId.next()): Movie {
            val now = Clock.System.now()
            return Movie(
                id = MetadataId(id),
                title = "Test Movie",
                overview = "A test movie",
                tmdbId = 12345,
                imdbId = "tt1234567",
                runtime = 120.minutes,
                releaseDate = now,
                createdAt = now,
                contentRating = "PG-13",
            )
        }

        fun createTestProcessor(
            kinds: List<MediaKind>,
            findMatchesForDir: suspend (Directory, Boolean) -> List<MediaLinkMatchResult> = { _, _ -> emptyList() },
            findMatchesForLink: suspend (MediaLink, Boolean) -> MediaLinkMatchResult = { link, _ ->
                MediaLinkMatchResult.NoSupportedFiles(link, null)
            },
            importMatch: suspend (MediaLink, MetadataMatch) -> MetadataMatch? = { _, match -> match },
            findMeta: suspend (MediaLink, String) -> MetadataMatch? = { _, _ -> null },
        ): MediaFileProcessor {
            return object : MediaFileProcessor {
                override val mediaKinds: List<MediaKind> = kinds
                override val fileNameParser: FileNameParser = object : FileNameParser {
                    override fun parseFileName(path: Path): ParsedFileNameResult = ParsedFileNameResult.Unknown
                }

                override suspend fun findMetadataMatches(
                    directory: Directory,
                    import: Boolean,
                ) = findMatchesForDir(directory, import)

                override suspend fun findMetadataMatches(
                    mediaLink: MediaLink,
                    import: Boolean,
                ) = findMatchesForLink(mediaLink, import)

                override suspend fun importMetadataMatch(
                    mediaLink: MediaLink,
                    metadataMatch: MetadataMatch,
                ) = importMatch(mediaLink, metadataMatch)

                override suspend fun findMetadata(
                    mediaLink: MediaLink,
                    remoteId: String,
                ) = findMeta(mediaLink, remoteId)
            }
        }

        fun createLibraryService(processors: Set<MediaFileProcessor> = emptySet()): LibraryService {
            return LibraryService(
                mediaFileAnalyzer = MediaFileAnalyzer({ error("not implemented!") }, mediaLinkDao),
                processors = processors,
                mediaLinkDao = mediaLinkDao,
                libraryDao = libraryDao,
                fs = fs,
            )
        }

        test("refreshMetadata by directoryId - nonexistent directory returns empty") {
            val service = createLibraryService()

            val result = service.refreshMetadata(DirectoryId("nonexistent"))
            result.shouldBeEmpty()
        }

        test("refreshMetadata by directoryId - directory without matching processor returns empty") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            val directory = libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            val service = createLibraryService()
            // directory exists and has a library, so this should work but return empty
            // because there are no processors
            val result = service.refreshMetadata(directory.id)
            result.shouldBeEmpty()
        }

        test("refreshMetadata by Directory - no processors returns empty") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            val directory = libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            val service = createLibraryService(processors = emptySet())
            val result = service.refreshMetadata(directory)
            result.shouldBeEmpty()
        }

        test("refreshMetadata by Directory - delegates to matching processor") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, movieDirs) = fs.createMovieDirectory()
            val rootDirectory = libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(rootDirectory).shouldBeInstanceOf<MediaScanResult.Success>()

            val movie = createTestMovie()
            val expectedMatch = MetadataMatch.MovieMatch(
                movie = movie,
                remoteMetadataId = "tmdb:12345",
                remoteId = "12345",
                exists = true,
                providerId = "tmdb",
            )
            val expectedResult = MediaLinkMatchResult.Success(
                mediaLink = null,
                directory = rootDirectory,
                matches = listOf(expectedMatch),
                subResults = emptyList(),
            )

            val processor = createTestProcessor(
                kinds = listOf(MediaKind.MOVIE),
                findMatchesForDir = { _, _ -> listOf(expectedResult) },
            )

            val service = createLibraryService(processors = setOf(processor))
            val results = service.refreshMetadata(rootDirectory)

            results.shouldHaveSize(1)
            results.first().shouldBeInstanceOf<MediaLinkMatchResult.Success>()
            val success = results.first() as MediaLinkMatchResult.Success
            success.matches.shouldHaveSize(1)
            success.matches.first() shouldBe expectedMatch
        }

        test("refreshMetadata by Directory - processor for wrong media kind is not used") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val movieLibrary = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            val directory = libraryDao.insertDirectory(null, movieLibrary.id, moviesRoot.absolutePathString())

            // Only a TV processor, no MOVIE processor
            val tvProcessor = createTestProcessor(
                kinds = listOf(MediaKind.TV),
                findMatchesForDir = { _, _ -> error("should not be called") },
            )

            val service = createLibraryService(processors = setOf(tvProcessor))
            val results = service.refreshMetadata(directory)
            results.shouldBeEmpty()
        }

        test("refreshMetadata by MediaLink - delegates to matching processor") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val videoLink = mediaLinkDao.all().first { it.descriptor == Descriptor.VIDEO }

            val movie = createTestMovie()
            val expectedMatch = MetadataMatch.MovieMatch(
                movie = movie,
                remoteMetadataId = "tmdb:12345",
                remoteId = "12345",
                exists = true,
                providerId = "tmdb",
            )

            val processor = createTestProcessor(
                kinds = listOf(MediaKind.MOVIE),
                findMatchesForLink = { _, _ ->
                    MediaLinkMatchResult.Success(
                        mediaLink = videoLink,
                        directory = null,
                        matches = listOf(expectedMatch),
                        subResults = emptyList(),
                    )
                },
            )

            val service = createLibraryService(processors = setOf(processor))
            val result = service.refreshMetadata(videoLink, import = false)

            val success = result.shouldBeInstanceOf<MediaLinkMatchResult.Success>()
            success.matches.shouldHaveSize(1)
            success.matches.first() shouldBe expectedMatch
        }

        test("refreshMetadata by MediaLink - no processor for media kind throws TODO") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val videoLink = mediaLinkDao.all().first { it.descriptor == Descriptor.VIDEO }

            val service = createLibraryService(processors = emptySet())

            // The MediaLink overload of refreshMetadata calls TODO() when no processor is found
            io.kotest.assertions.throwables.shouldThrow<NotImplementedError> {
                service.refreshMetadata(videoLink, import = false)
            }
        }

        test("refreshMetadata by directoryId - valid directory delegates to processor") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            val rootDirectory = libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(rootDirectory).shouldBeInstanceOf<MediaScanResult.Success>()

            val movie = createTestMovie()
            val expectedMatch = MetadataMatch.MovieMatch(
                movie = movie,
                remoteMetadataId = "tmdb:99",
                remoteId = "99",
                exists = true,
                providerId = "tmdb",
            )

            var directorySeen: Directory? = null
            val processor = createTestProcessor(
                kinds = listOf(MediaKind.MOVIE),
                findMatchesForDir = { dir, _ ->
                    directorySeen = dir
                    listOf(
                        MediaLinkMatchResult.Success(
                            mediaLink = null,
                            directory = dir,
                            matches = listOf(expectedMatch),
                            subResults = emptyList(),
                        ),
                    )
                },
            )

            val service = createLibraryService(processors = setOf(processor))
            val results = service.refreshMetadata(rootDirectory.id)

            results.shouldHaveSize(1)
            val seenDir = directorySeen.shouldNotBeNull()
            seenDir.id shouldBe rootDirectory.id
        }

        test("refreshMetadata by MediaLink with import=true - processor performs import") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val videoLink = mediaLinkDao.all().first { it.descriptor == Descriptor.VIDEO }

            val movie = createTestMovie()
            val expectedMatch = MetadataMatch.MovieMatch(
                movie = movie,
                remoteMetadataId = "tmdb:12345",
                remoteId = "12345",
                exists = true,
                providerId = "tmdb",
            )

            var importFlagSeen: Boolean? = null
            val processor = createTestProcessor(
                kinds = listOf(MediaKind.MOVIE),
                findMatchesForLink = { _, import ->
                    importFlagSeen = import
                    MediaLinkMatchResult.Success(
                        mediaLink = videoLink,
                        directory = null,
                        matches = listOf(expectedMatch),
                        subResults = emptyList(),
                    )
                },
            )

            val service = createLibraryService(processors = setOf(processor))
            service.refreshMetadata(videoLink, import = true)

            importFlagSeen shouldBe true
        }
    })
