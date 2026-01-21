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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import kotlin.time.Duration.Companion.minutes

class MediaLinkDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val dao: MediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val libraryDao: LibraryDao by bindForTest({ LibraryDao(db) })
    val metadataDao: MetadataDao by bindForTest({ MetadataDao(db) })

    test("insert and fetch media link") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link = createTestMediaLink(
            directoryId = directory.id,
            filePath = "/movies/Test Movie.mp4",
            mediaKind = MediaKind.MOVIE,
        )
        dao.insertLink(link).shouldBeTrue()

        val fetched = dao.findById(link.id)
        fetched.shouldNotBeNull()
        fetched.id shouldBe link.id
        fetched.filePath shouldBe "/movies/Test Movie.mp4"
        fetched.descriptor shouldBe Descriptor.VIDEO
    }

    test("find by file path") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link = createTestMediaLink(
            directoryId = directory.id,
            filePath = "/movies/Unique Movie.mp4",
        )
        dao.insertLink(link)

        val found = dao.findByFilePath("/movies/Unique Movie.mp4")
        found.shouldNotBeNull()
        found.id shouldBe link.id

        dao.findByFilePath("/movies/NonExistent.mp4").shouldBeNull()
    }

    test("find by metadata id") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")
        val movie = createTestMovie(title = "Linked Movie")
        metadataDao.insertMetadata(movie)

        val link = createTestMediaLink(
            directoryId = directory.id,
            metadataId = movie.id,
            filePath = "/movies/Linked Movie.mp4",
        )
        dao.insertLink(link)

        val links = dao.findByMetadataId(movie.id)
        links shouldHaveSize 1
        links.first().id shouldBe link.id
    }

    test("find by base path and descriptor") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link1 = createTestMediaLink(
            directoryId = directory.id,
            filePath = "/movies/folder1/Movie1.mp4",
            descriptor = Descriptor.VIDEO,
        )
        val link2 = createTestMediaLink(
            directoryId = directory.id,
            filePath = "/movies/folder1/Movie1.srt",
            descriptor = Descriptor.SUBTITLE,
        )
        val link3 = createTestMediaLink(
            directoryId = directory.id,
            filePath = "/movies/folder2/Movie2.mp4",
            descriptor = Descriptor.VIDEO,
        )

        dao.insertLink(link1)
        dao.insertLink(link2)
        dao.insertLink(link3)

        val videoLinks = dao.findByBasePathAndDescriptor("/movies/folder1", Descriptor.VIDEO)
        videoLinks shouldHaveSize 1
        videoLinks.first().filePath shouldBe "/movies/folder1/Movie1.mp4"

        val subtitleLinks = dao.findByBasePathAndDescriptor("/movies/folder1", Descriptor.SUBTITLE)
        subtitleLinks shouldHaveSize 1
        subtitleLinks.first().filePath shouldBe "/movies/folder1/Movie1.srt"
    }

    test("find by directory id") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val dir1 = libraryDao.insertDirectory(null, library.id, "/dir1")
        val dir2 = libraryDao.insertDirectory(null, library.id, "/dir2")

        val link1 = createTestMediaLink(directoryId = dir1.id, filePath = "/dir1/file1.mp4")
        val link2 = createTestMediaLink(directoryId = dir1.id, filePath = "/dir1/file2.mp4")
        val link3 = createTestMediaLink(directoryId = dir2.id, filePath = "/dir2/file3.mp4")

        dao.insertLink(link1)
        dao.insertLink(link2)
        dao.insertLink(link3)

        val dir1Links = dao.findByDirectoryId(dir1.id)
        dir1Links shouldHaveSize 2
        dir1Links.map { it.filePath } shouldContainExactlyInAnyOrder listOf("/dir1/file1.mp4", "/dir1/file2.mp4")
    }

    test("update metadata ids") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")
        val movie = createTestMovie(title = "Update Test Movie")
        metadataDao.insertMetadata(movie)

        val link = createTestMediaLink(
            directoryId = directory.id,
            filePath = "/movies/Update Test.mp4",
        )
        dao.insertLink(link)

        // Initially no metadata linked
        dao.findById(link.id)?.metadataId.shouldBeNull()

        // Update metadata link
        dao.updateMetadataIds(MediaLinkMetadataUpdate(link.id, movie.id, movie.id))

        // Verify update
        val updated = dao.findById(link.id)
        updated.shouldNotBeNull()
        updated.metadataId shouldBe movie.id
        updated.rootMetadataId shouldBe movie.id
    }

    test("update metadata ids in batch") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")
        val movie1 = createTestMovie(title = "Batch Movie 1")
        val movie2 = createTestMovie(title = "Batch Movie 2")
        metadataDao.insertMetadata(listOf(movie1, movie2))

        val link1 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/batch1.mp4")
        val link2 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/batch2.mp4")
        dao.insertLink(link1)
        dao.insertLink(link2)

        dao.updateMetadataIds(
            listOf(
                MediaLinkMetadataUpdate(link1.id, movie1.id, movie1.id),
                MediaLinkMetadataUpdate(link2.id, movie2.id, movie2.id),
            )
        )

        dao.findById(link1.id)?.metadataId shouldBe movie1.id
        dao.findById(link2.id)?.metadataId shouldBe movie2.id
    }

    test("delete by id") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link = createTestMediaLink(directoryId = directory.id, filePath = "/movies/delete-me.mp4")
        dao.insertLink(link)

        dao.findById(link.id).shouldNotBeNull()

        dao.deleteById(link.id).shouldBeTrue()

        dao.findById(link.id).shouldBeNull()
    }

    test("delete by metadata id") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")
        val movie = createTestMovie(title = "Delete By Metadata Movie")
        metadataDao.insertMetadata(movie)

        val link = createTestMediaLink(
            directoryId = directory.id,
            metadataId = movie.id,
            filePath = "/movies/delete-metadata.mp4",
        )
        dao.insertLink(link)

        dao.deleteByMetadataId(movie.id)

        dao.findById(link.id).shouldBeNull()
    }

    test("delete by root metadata id") {
        val library = libraryDao.insertLibrary(MediaKind.TV)
        val directory = libraryDao.insertDirectory(null, library.id, "/tv")
        val show = createTestTvShow(name = "Delete Root Test Show")
        val season = createTestSeason(showId = show.id, seasonNumber = 1)
        val episode = createTestEpisode(show.id, season.id, 1, 1)
        metadataDao.insertMetadata(listOf(show, season, episode))

        val link = createTestMediaLink(
            directoryId = directory.id,
            metadataId = episode.id,
            rootMetadataId = show.id,
            filePath = "/tv/Show/S01E01.mp4",
            mediaKind = MediaKind.TV,
        )
        dao.insertLink(link)

        dao.deleteByRootMetadataId(show.id)

        dao.findById(link.id).shouldBeNull()
    }

    test("delete by base path") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link1 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/folder/file1.mp4")
        val link2 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/folder/file2.mp4")
        val link3 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/other/file3.mp4")

        dao.insertLink(link1)
        dao.insertLink(link2)
        dao.insertLink(link3)

        val deletedIds = dao.deleteByBasePath("/movies/folder")

        deletedIds shouldHaveSize 2
        dao.findById(link1.id).shouldBeNull()
        dao.findById(link2.id).shouldBeNull()
        dao.findById(link3.id).shouldNotBeNull()
    }

    test("find all file paths") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link1 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/file1.mp4")
        val link2 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/file2.mp4")

        dao.insertLink(link1)
        dao.insertLink(link2)

        val paths = dao.findAllFilePaths()
        paths shouldContainExactlyInAnyOrder listOf("/movies/file1.mp4", "/movies/file2.mp4")
    }

    test("descriptor for id") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val videoLink = createTestMediaLink(
            directoryId = directory.id,
            filePath = "/movies/video.mp4",
            descriptor = Descriptor.VIDEO,
        )
        val subtitleLink = createTestMediaLink(
            directoryId = directory.id,
            filePath = "/movies/subtitle.srt",
            descriptor = Descriptor.SUBTITLE,
        )

        dao.insertLink(videoLink)
        dao.insertLink(subtitleLink)

        dao.descriptorForId(videoLink.id) shouldBe Descriptor.VIDEO
        dao.descriptorForId(subtitleLink.id) shouldBe Descriptor.SUBTITLE
        dao.descriptorForId("nonexistent").shouldBeNull()
    }

    test("insert and find stream encodings") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link = createTestMediaLink(directoryId = directory.id, filePath = "/movies/encoded.mp4")
        dao.insertLink(link)

        val streams = listOf(
            StreamEncoding(
                id = ObjectId.next(),
                codecName = "h264",
                codecLongName = "H.264 / AVC",
                index = 0,
                type = StreamEncodingType.VIDEO,
                mediaLinkId = link.id,
                default = true,
                width = 1920,
                height = 1080,
            ),
            StreamEncoding(
                id = ObjectId.next(),
                codecName = "aac",
                codecLongName = "AAC (Advanced Audio Coding)",
                index = 1,
                type = StreamEncodingType.AUDIO,
                mediaLinkId = link.id,
                default = true,
                channels = 2,
                channelLayout = "stereo",
            ),
        )
        dao.insertStreamDetails(streams)

        val foundStreams = dao.findStreamEncodings(link.id)
        foundStreams shouldHaveSize 2
        foundStreams.map { it.codecName } shouldContainExactlyInAnyOrder listOf("h264", "aac")
    }

    test("find stream encodings for multiple media links") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link1 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/movie1.mp4")
        val link2 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/movie2.mp4")
        dao.insertLink(link1)
        dao.insertLink(link2)

        val streams = listOf(
            StreamEncoding(
                id = ObjectId.next(),
                codecName = "h264",
                type = StreamEncodingType.VIDEO,
                mediaLinkId = link1.id,
                default = true,
            ),
            StreamEncoding(
                id = ObjectId.next(),
                codecName = "hevc",
                type = StreamEncodingType.VIDEO,
                mediaLinkId = link2.id,
                default = true,
            ),
        )
        dao.insertStreamDetails(streams)

        val streamMap = dao.findStreamEncodings(listOf(link1.id, link2.id))
        streamMap.keys shouldContainExactlyInAnyOrder listOf(link1.id, link2.id)
        streamMap[link1.id]?.first()?.codecName shouldBe "h264"
        streamMap[link2.id]?.first()?.codecName shouldBe "hevc"
    }

    test("count stream details") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link = createTestMediaLink(directoryId = directory.id, filePath = "/movies/count-test.mp4")
        dao.insertLink(link)

        dao.countStreamDetails(link.id) shouldBe 0

        val streams = listOf(
            StreamEncoding(
                id = ObjectId.next(),
                codecName = "h264",
                type = StreamEncodingType.VIDEO,
                mediaLinkId = link.id,
                default = true,
            ),
            StreamEncoding(
                id = ObjectId.next(),
                codecName = "aac",
                type = StreamEncodingType.AUDIO,
                mediaLinkId = link.id,
                default = true,
            ),
        )
        dao.insertStreamDetails(streams)

        dao.countStreamDetails(link.id) shouldBe 2
    }

    test("find links to analyze returns links without stream encodings") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val linkWithStreams = createTestMediaLink(directoryId = directory.id, filePath = "/movies/with-streams.mp4")
        val linkWithoutStreams = createTestMediaLink(directoryId = directory.id, filePath = "/movies/without-streams.mp4")

        dao.insertLink(linkWithStreams)
        dao.insertLink(linkWithoutStreams)

        // Add streams to one link
        dao.insertStreamDetails(
            listOf(
                StreamEncoding(
                    id = ObjectId.next(),
                    codecName = "h264",
                    type = StreamEncodingType.VIDEO,
                    mediaLinkId = linkWithStreams.id,
                    default = true,
                )
            )
        )

        val linksToAnalyze = dao.findLinksToAnalyze()
        linksToAnalyze shouldHaveSize 1
        linksToAnalyze.first() shouldBe linkWithoutStreams.id
    }

    test("find by ids") {
        val library = libraryDao.insertLibrary(MediaKind.MOVIE)
        val directory = libraryDao.insertDirectory(null, library.id, "/movies")

        val link1 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/id1.mp4")
        val link2 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/id2.mp4")
        val link3 = createTestMediaLink(directoryId = directory.id, filePath = "/movies/id3.mp4")

        dao.insertLink(link1)
        dao.insertLink(link2)
        dao.insertLink(link3)

        val found = dao.findByIds(listOf(link1.id, link3.id))
        found shouldHaveSize 2
        found.map { it.filePath } shouldContainExactlyInAnyOrder listOf("/movies/id1.mp4", "/movies/id3.mp4")
    }

    test("find by root metadata ids") {
        val library = libraryDao.insertLibrary(MediaKind.TV)
        val directory = libraryDao.insertDirectory(null, library.id, "/tv")

        val show1 = createTestTvShow(name = "Show 1")
        val show2 = createTestTvShow(name = "Show 2")
        metadataDao.insertMetadata(listOf(show1, show2))

        val link1 = createTestMediaLink(
            directoryId = directory.id,
            rootMetadataId = show1.id,
            filePath = "/tv/show1/ep1.mp4",
            mediaKind = MediaKind.TV,
        )
        val link2 = createTestMediaLink(
            directoryId = directory.id,
            rootMetadataId = show2.id,
            filePath = "/tv/show2/ep1.mp4",
            mediaKind = MediaKind.TV,
        )

        dao.insertLink(link1)
        dao.insertLink(link2)

        val found = dao.findByRootMetadataIds(listOf(show1.id))
        found shouldHaveSize 1
        found.first().id shouldBe link1.id
    }
})
