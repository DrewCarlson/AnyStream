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

import anystream.db.MediaLinkDao
import anystream.db.mappers.registerMappers
import anystream.db.model.MediaLinkDb
import anystream.db.runMigrations
import anystream.models.MediaKind
import anystream.models.MediaLink
import anystream.models.api.MediaScanResult
import anystream.models.backend.MediaScannerState
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.attach
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MediaFileScannerTest {
    private lateinit var handle: Handle
    private lateinit var mediaLinkDao: MediaLinkDao
    private lateinit var mediaFileScanner: MediaFileScanner
    private val userId = 1

    @BeforeTest
    fun setUp() {
        runMigrations("jdbc:sqlite:test.db")
        val jdbi = Jdbi.create("jdbc:sqlite:test.db").apply {
            installPlugin(SqlObjectPlugin())
            installPlugin(KotlinSqlObjectPlugin())
            installPlugin(KotlinPlugin())
            registerMappers()
        }
        handle = jdbi.open()
        mediaLinkDao = handle.attach<MediaLinkDao>()
        mediaFileScanner = MediaFileScanner(mediaLinkDao)
    }

    @AfterTest
    fun cleanUp() {
        handle.close()
        File("test.db").delete()
    }

    @Test
    fun `test scanForMedia with movie directory`() {
        val libraryLink = createMediaLinkDbForTesting()
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
            val result = mediaFileScanner.scanForMedia(userId, libraryLink, null)
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
        val libraryLink = createMediaLinkDbForTesting()
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
            val result = mediaFileScanner.scanForMedia(userId, libraryLink, null)
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
        val libraryLink = createMediaLinkDbForTesting()
        mediaLinkDao.insertLink(libraryLink)

        runBlocking {
            val result = mediaFileScanner.scanForMedia(userId, libraryLink, null)
            assertTrue(result is MediaScanResult.Success)
            assertEquals(MediaScannerState.Idle, mediaFileScanner.state.value)
        }
    }

    @Test
    fun `test scanForMedia with existing childLink`() {
        val libraryLink = createMediaLinkDbForTesting()
        val childLink = createMediaLinkDbForTesting()
        mediaLinkDao.insertLink(libraryLink)
        mediaLinkDao.insertLink(childLink)

        runBlocking {
            val result = mediaFileScanner.scanForMedia(userId, libraryLink, childLink)
            assertTrue(result is MediaScanResult.Success)
            assertEquals(MediaScannerState.Idle, mediaFileScanner.state.value)
        }
    }

    @Test
    fun `test scanForMedia with non-existing childLink`() {
        val libraryLink = createMediaLinkDbForTesting()
        val childLink = createMediaLinkDbForTesting().copy(filePath = "non-existing-file")
        mediaLinkDao.insertLink(libraryLink)
        mediaLinkDao.insertLink(childLink)

        runBlocking {
            val result = mediaFileScanner.scanForMedia(userId, libraryLink, childLink)
            assertTrue(result is MediaScanResult.ErrorFileNotFound)
            assertEquals(MediaScannerState.Idle, mediaFileScanner.state.value)
        }
    }

    private fun createMediaLinkDbForTesting(filePath: String? = null): MediaLinkDb {
        return MediaLinkDb(
            id = 1,
            gid = "test-gid",
            metadataId = 1,
            metadataGid = "test-metadata-gid",
            rootMetadataId = 1,
            rootMetadataGid = "test-root-metadata-gid",
            parentMediaLinkId = null,
            parentMediaLinkGid = null,
            addedAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            addedByUserId = userId,
            mediaKind = MediaKind.MOVIE,
            type = MediaLinkDb.Type.LOCAL,
            filePath = filePath ?: createTempDir().absolutePath,
            directory = true,
            fileIndex = null,
            hash = null,
            descriptor = MediaLink.Descriptor.ROOT_DIRECTORY,
            streams = emptyList(),
        )
    }
}
