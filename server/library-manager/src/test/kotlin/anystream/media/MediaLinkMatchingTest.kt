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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class MediaLinkMatchingTest :
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

        test("removeMediaLink - delete a VIDEO media link") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val allLinks = mediaLinkDao.all()
            allLinks.shouldHaveSize(11) // from createMovieDirectory: 6 video + 5 subtitle files

            val service = createLibraryService()
            val linkToRemove = allLinks.first()

            service.removeMediaLink(linkToRemove).shouldBeTrue()

            mediaLinkDao.findById(linkToRemove.id).shouldBeNull()
            mediaLinkDao.all().shouldHaveSize(10)
        }

        test("removeMediaLink - delete a SUBTITLE media link") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val subtitleLink = mediaLinkDao.all().first { it.descriptor == Descriptor.SUBTITLE }
            val service = createLibraryService()

            service.removeMediaLink(subtitleLink).shouldBeTrue()

            mediaLinkDao.findById(subtitleLink.id).shouldBeNull()
        }

        test("matchMediaLink with remoteId - delegates to processor and updates metadata") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val videoLink = mediaLinkDao.all().first { it.descriptor == Descriptor.VIDEO }
            val movie = createTestMovie()
            val movieMatch = MetadataMatch.MovieMatch(
                movie = movie,
                remoteMetadataId = "tmdb:12345",
                remoteId = "12345",
                exists = true,
                providerId = "tmdb",
            )

            var importCalledWithLink: MediaLink? = null
            var importCalledWithMatch: MetadataMatch? = null
            val processor = createTestProcessor(
                kinds = listOf(MediaKind.MOVIE),
                findMeta = { _, _ -> movieMatch },
                importMatch = { link, match ->
                    importCalledWithLink = link
                    importCalledWithMatch = match
                    match
                },
            )

            val service = createLibraryService(processors = setOf(processor))
            service.matchMediaLink(videoLink, "12345")

            val calledLink = importCalledWithLink.shouldNotBeNull()
            calledLink.id shouldBe videoLink.id
            importCalledWithMatch.shouldNotBeNull()
            importCalledWithMatch shouldBe movieMatch
        }

        test("matchMediaLink with remoteId - no processor for media kind") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val videoLink = mediaLinkDao.all().first { it.descriptor == Descriptor.VIDEO }

            // Service with no processors
            val service = createLibraryService(processors = emptySet())
            // Should not throw - just returns early
            service.matchMediaLink(videoLink, "12345")

            // Metadata should remain null
            val unchangedLink = mediaLinkDao.findById(videoLink.id).shouldNotBeNull()
            unchangedLink.metadataId.shouldBeNull()
        }

        test("matchMediaLink with remoteId - metadata not found by provider") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val videoLink = mediaLinkDao.all().first { it.descriptor == Descriptor.VIDEO }

            val processor = createTestProcessor(
                kinds = listOf(MediaKind.MOVIE),
                findMeta = { _, _ -> null },
            )

            val service = createLibraryService(processors = setOf(processor))
            service.matchMediaLink(videoLink, "nonexistent-id")

            val unchangedLink = mediaLinkDao.findById(videoLink.id).shouldNotBeNull()
            unchangedLink.metadataId.shouldBeNull()
        }

        test("matchMediaLink with MetadataMatch - non-VIDEO descriptor is ignored") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .first { it.mediaKind == MediaKind.MOVIE }

            val (moviesRoot, _) = fs.createMovieDirectory()
            libraryDao.insertDirectory(null, library.id, moviesRoot.absolutePathString())

            mediaFileScanner.scan(moviesRoot).shouldBeInstanceOf<MediaScanResult.Success>()

            val subtitleLink = mediaLinkDao.all().first { it.descriptor == Descriptor.SUBTITLE }

            val movie = createTestMovie()
            val movieMatch = MetadataMatch.MovieMatch(
                movie = movie,
                remoteMetadataId = "tmdb:12345",
                remoteId = "12345",
                exists = true,
                providerId = "tmdb",
            )

            var importCalled = false
            val processor = createTestProcessor(
                kinds = listOf(MediaKind.MOVIE),
                importMatch = { _, _ ->
                    importCalled = true
                    movieMatch
                },
            )

            val service = createLibraryService(processors = setOf(processor))
            service.matchMediaLink(subtitleLink, movieMatch)

            // matchMediaLink returns early for non-VIDEO descriptors
            importCalled.shouldBeFalse()
        }
    })
