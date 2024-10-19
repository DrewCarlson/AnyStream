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

import anystream.db.LibraryDao
import anystream.db.MediaLinkDao
import anystream.db.createTestDatabase
import anystream.media.scanner.MediaFileScanner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore

@Ignore("Disabled until MediaFileScanner is migrated away from File APIs")
class MediaFileScannerTest {

    private lateinit var closeDb: () -> Unit
    private lateinit var mediaLinkDao: MediaLinkDao
    private lateinit var mediaFileScanner: MediaFileScanner
    private val userId = 1

    @BeforeTest
    fun setUp() {
        val (connection, db) = createTestDatabase()
        closeDb = connection::close
        mediaLinkDao = MediaLinkDao(db)
        mediaFileScanner = MediaFileScanner(mediaLinkDao, LibraryDao(db))
    }

    @AfterTest
    fun cleanUp() {
        closeDb()
    }

    /*@Test
    fun `test scanForMedia with movie directory`() {
        val libraryLink = createMediaLinkForTesting()
        mediaLinkDao.insertLink(libraryLink)
        val rootFolder = File(libraryLink.filePath!!)

        val movie1 = File(rootFolder, "2 Fast 2 Furious (2003)")
        val movie2 = File(rootFolder, "Alice in Wonderland (1951)")

        movie1.mkdirs()
        movie2.mkdirs()

        val videoFile1 = File(movie1, "2 Fast 2 Furious (2003).mkv")
        val videoFile2 = File(movie2, "Alice in Wonderland (1951).mkv")
        val subtitleFile = File(movie2, "Alice in Wonderland (1951).en.srt")

        videoFile1.createNewFile()
        videoFile2.createNewFile()
        subtitleFile.createNewFile()

        runBlocking {
            val result = mediaFileScanner.scan(libraryLink)
            assertTrue(result is MediaScanResult.Success)

            val successResult = assertIs<MediaScanResult.Success>(result)
            assertEquals(5, successResult.addedMediaLinkGids.size, successResult.toString())

            val expectedFilePaths = listOf(
                movie1.absolutePath,
                movie2.absolutePath,
                videoFile1.absolutePath,
                subtitleFile.absolutePath,
                videoFile2.absolutePath,
            )
            val filePathsInDb = mediaLinkDao.findFilePathsByGids(successResult.addedMediaLinkGids)
            assertEquals(expectedFilePaths, filePathsInDb)

            assertEquals(MediaScannerState.Idle, mediaFileScanner.state.value)
        }

        movie1.deleteRecursively()
        movie2.deleteRecursively()
    }

    @Test
    fun `test scanForMedia with tv show directory`() {
        val libraryLink = createMediaLinkForTesting()
        mediaLinkDao.insertLink(libraryLink)
        val rootFolder = File(libraryLink.filePath!!)

        val tvShow1 = File(rootFolder, "Burn Notice")
        val tvShow1Season1 = File(tvShow1, "Season 01")
        val tvShow1Season2 = File(tvShow1, "Season 02")

        tvShow1.mkdirs()
        tvShow1Season1.mkdirs()
        tvShow1Season2.mkdirs()

        val tvShow1Episode1 = File(tvShow1Season1, "Burn Notice - S01E01 - Burn Notice.mp4")
        val tvShow1Episode2 = File(tvShow1Season2, "Burn Notice - S02E01 - Breaking and Entering.mp4")
        val tvShow1Episode3 = File(tvShow1Season2, "Burn Notice - S02E02 - Turn and Burn.mp4")

        tvShow1Episode1.createNewFile()
        tvShow1Episode2.createNewFile()
        tvShow1Episode3.createNewFile()

        runBlocking {
            val result = mediaFileScanner.scan(libraryLink)
            assertTrue(result is MediaScanResult.Success)

            val successResult = result as MediaScanResult.Success
            assertEquals(6, successResult.addedMediaLinkGids.size)

            val expectedFilePaths = listOf(
                tvShow1.absolutePath,
                tvShow1Season1.absolutePath,
                tvShow1Season2.absolutePath,
                tvShow1Episode1.absolutePath,
                tvShow1Episode2.absolutePath,
                tvShow1Episode3.absolutePath,
            )

            val filePathsInDb = mediaLinkDao.findFilePathsByGids(successResult.addedMediaLinkGids)
            assertEquals(expectedFilePaths, filePathsInDb)

            assertEquals(MediaScannerState.Idle, mediaFileScanner.state.value)
        }

        tvShow1.deleteRecursively()
    }

    @Test
    fun `test scanForMedia with no childLink`() {
        val libraryLink = createMediaLinkForTesting()
        mediaLinkDao.insertLink(libraryLink)

        runBlocking {
            val result = mediaFileScanner.scan(libraryLink)
            assertTrue(result is MediaScanResult.Success)
            assertEquals(MediaScannerState.Idle, mediaFileScanner.state.value)
        }
    }

    @Test
    fun `test scanForMedia with existing childLink`() {
        val libraryLink = createMediaLinkForTesting()
        val childLink = createMediaLinkForTesting()
        mediaLinkDao.insertLink(libraryLink)
        mediaLinkDao.insertLink(childLink)

        runBlocking {
            val result = mediaFileScanner.scan(childLink)
            assertTrue(result is MediaScanResult.Success)
            assertEquals(MediaScannerState.Idle, mediaFileScanner.state.value)
        }
    }

    @Test
    fun `test scanForMedia with non-existing childLink`() {
        val libraryLink = createMediaLinkForTesting()
        val childLink = createMediaLinkForTesting().copy(filePath = "non-existing-file")
        mediaLinkDao.insertLink(libraryLink)
        mediaLinkDao.insertLink(childLink)

        runBlocking {
            val result = mediaFileScanner.scan(childLink)
            assertTrue(result is MediaScanResult.ErrorFileNotFound)
            assertEquals(MediaScannerState.Idle, mediaFileScanner.state.value)
        }
    }

    private fun createMediaLinkForTesting(filePath: String? = null): MediaLink {
        return MediaLink(
            id = 1,
            gid = "test-gid",
            metadataId = 1,
            metadataGid = "test-metadata-gid",
            rootMetadataId = 1,
            rootMetadataGid = "test-root-metadata-gid",
            parentId = null,
            parentGid = null,
            addedAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            mediaKind = MediaKind.MOVIE,
            type = MediaLink.Type.LOCAL,
            filePath = filePath ?: createTempDir().absolutePath,
            fileIndex = null,
            hash = null,
            descriptor = Descriptor.ROOT_DIRECTORY,
            streams = emptyList(),
        )
    }*/
}
