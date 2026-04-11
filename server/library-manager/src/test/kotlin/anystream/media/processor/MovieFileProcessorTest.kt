/*
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
import anystream.db.TagsDao
import anystream.db.bindFileSystem
import anystream.db.bindForTest
import anystream.db.bindTestDatabase
import anystream.media.createMovieDirectory
import anystream.media.scanner.MediaFileScanner
import anystream.metadata.ImageStore
import anystream.metadata.MetadataService
import anystream.metadata.providers.WireMetadataProvider
import anystream.metadata.testing.WireFixtures
import anystream.models.Descriptor
import anystream.models.MediaKind
import anystream.models.api.MediaLinkMatchResult
import anystream.models.api.MediaScanResult
import anystream.models.api.MetadataMatch
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.io.path.absolutePathString

/**
 * End-to-end scan → match → import for [MovieFileProcessor], wired through [WireMetadataProvider] over
 * the [WireFixtures] mock. Uses a single movie folder because the fixture resolves every
 * `/movie/{id}` to Memento, so multiple folders would collide on the same wire id at import time.
 */
class MovieFileProcessorTest :
    FunSpec({

        val db by bindTestDatabase()
        val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
        val metadataDao by bindForTest({ MetadataDao(db) })
        val libraryDao by bindForTest({ LibraryDao(db) })
        val queries by bindForTest({
            val tagsDao = TagsDao(db)
            val playbackStatesDao = PlaybackStatesDao(db)

            MetadataDbQueries(db, metadataDao, tagsDao, mediaLinkDao, playbackStatesDao)
        })
        val fs by bindFileSystem()
        val metadataService by bindForTest({
            val imageHttpClient = HttpClient(
                MockEngine { _ -> respond(byteArrayOf(), HttpStatusCode.OK, headersOf()) },
            )
            val imageStore = ImageStore(fs.getPath("/test"), imageHttpClient)
            val provider = WireMetadataProvider(WireFixtures.mockWireApi(), queries, imageStore)
            MetadataService(setOf(provider), metadataDao, imageStore)
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

        test("match a movie from a library root") {
            libraryDao.insertDefaultLibraries().shouldBeTrue()

            val library = libraryDao
                .fetchLibraries()
                .firstOrNull { it.mediaKind == MediaKind.MOVIE }
                .shouldNotBeNull()

            val (mediaRootPath, _) = fs.createMovieDirectory(
                fileMap = mapOf("Memento (2000)" to listOf("mkv")),
            )
            val rootDirectory = libraryDao.insertDirectory(
                parentId = null,
                libraryId = library.id,
                path = mediaRootPath.absolutePathString(),
            )

            mediaFileScanner.scan(mediaRootPath).shouldBeInstanceOf<MediaScanResult.Success>()

            val childDirectories = libraryDao.fetchChildDirectories(rootDirectory.id)
            childDirectories.shouldHaveSize(1)

            childDirectories.forEach { directory ->
                val result = processor
                    .findMetadataMatches(directory, import = true)
                    .shouldHaveSize(1)
                    .first()
                    .shouldBeInstanceOf<MediaLinkMatchResult.Success>()

                val match = result.matches
                    .first()
                    .shouldBeInstanceOf<MetadataMatch.MovieMatch>()
                match.movie.title shouldBeEqual "Memento"

                mediaLinkDao
                    .findByDirectoryId(directory.id)
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
