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
import anystream.db.TagsDao
import anystream.db.bindFileSystem
import anystream.db.bindForTest
import anystream.db.bindTestDatabase
import anystream.media.createTvDirectory
import anystream.media.scanner.MediaFileScanner
import anystream.metadata.MetadataService
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.Descriptor
import anystream.models.Episode
import anystream.models.MediaKind
import anystream.models.api.MediaLinkMatchResult
import anystream.models.api.MediaScanResult
import app.moviebase.tmdb.Tmdb3
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.io.path.absolutePathString

class TvFileProcessorTest : FunSpec({

    val db by bindTestDatabase()
    val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val metadataDao by bindForTest({ MetadataDao(db) })
    val libraryDao by bindForTest({ LibraryDao(db) })
    val queries by bindForTest({
        val tagsDao = TagsDao(db)
        val playbackStatesDao = PlaybackStatesDao(db)
        val mediaLinkDao = MediaLinkDao(db)

        MetadataDbQueries(db, metadataDao, tagsDao, mediaLinkDao, playbackStatesDao)
    })
    val tmdb by bindForTest({
        Tmdb3 {
            tmdbApiKey = "c1e9e8ade306dd9cbc5e17b05ed4badd"
        }
    })
    val metadataService by bindForTest({
        val provider = TmdbMetadataProvider(tmdb, queries)
        MetadataService(listOf(provider))
    })
    val fs by bindFileSystem()
    val mediaFileScanner by bindForTest({ MediaFileScanner(mediaLinkDao, libraryDao, fs) })
    val processor by bindForTest({
        TvFileProcessor(
            metadataService = metadataService,
            mediaLinkDao = mediaLinkDao,
            libraryDao = libraryDao,
            metadataDao = metadataDao,
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