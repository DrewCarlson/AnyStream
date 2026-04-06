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
package anystream.db

import anystream.models.*
import anystream.util.ObjectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import kotlin.time.Clock

class MediaLinkDaoTest :
    FunSpec({

        val db: DSLContext by bindTestDatabase()
        val libraryDao by bindForTest({ LibraryDao(db) })
        val metadataDao by bindForTest({ MetadataDao(db) })
        val mediaLinkDao by bindForTest({ MediaLinkDao(db) })

        suspend fun setupLibraryAndDirectory(): DirectoryId {
            libraryDao.insertDefaultLibraries()
            val library = libraryDao.all().first { it.mediaKind == MediaKind.MOVIE }
            return libraryDao.insertDirectory(null, library.id, "/movies").id
        }

        fun createMediaLink(
            directoryId: DirectoryId,
            filePath: String = "/movies/test.mp4",
            descriptor: Descriptor = Descriptor.VIDEO,
            mediaKind: MediaKind = MediaKind.MOVIE,
            metadataId: MetadataId? = null,
            rootMetadataId: MetadataId? = null,
            hash: String? = null,
        ): MediaLink {
            val now = Clock.System.now()
            return MediaLink(
                id = MediaLinkId(ObjectId.next()),
                directoryId = directoryId,
                filePath = filePath,
                descriptor = descriptor,
                mediaKind = mediaKind,
                type = MediaLinkType.LOCAL,
                createdAt = now,
                updatedAt = now,
                metadataId = metadataId,
                rootMetadataId = rootMetadataId,
                hash = hash,
            )
        }

        fun createMetadata(
            id: String = ObjectId.next(),
            type: MediaType = MediaType.MOVIE,
        ): Metadata {
            val now = Clock.System.now()
            return Metadata(
                id = MetadataId(id),
                title = "Test",
                mediaType = type,
                mediaKind = MediaKind.MOVIE,
                createdAt = now,
                updatedAt = now,
            )
        }

        test("insertLink and findById") {
            val dirId = setupLibraryAndDirectory()
            val link = createMediaLink(dirId)

            mediaLinkDao.insertLink(link).shouldBeTrue()

            val found = mediaLinkDao.findById(link.id).shouldNotBeNull()
            found.id shouldBe link.id
            found.filePath shouldBe link.filePath
            found.descriptor shouldBe Descriptor.VIDEO
        }

        test("findById returns null for nonexistent") {
            mediaLinkDao.findById(MediaLinkId("nonexistent")).shouldBeNull()
        }

        test("all returns all links") {
            val dirId = setupLibraryAndDirectory()
            val link1 = createMediaLink(dirId, filePath = "/movies/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/b.mp4")

            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)

            mediaLinkDao.all().shouldHaveSize(2)
        }

        test("findByIds") {
            val dirId = setupLibraryAndDirectory()
            val link1 = createMediaLink(dirId, filePath = "/movies/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/b.mp4")
            val link3 = createMediaLink(dirId, filePath = "/movies/c.mp4")

            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)
            mediaLinkDao.insertLink(link3)

            val found = mediaLinkDao.findByIds(listOf(link1.id, link3.id))
            found.shouldHaveSize(2)
            found.map { it.id }.shouldContainExactlyInAnyOrder(link1.id, link3.id)
        }

        test("findByFilePath") {
            val dirId = setupLibraryAndDirectory()
            val link = createMediaLink(dirId, filePath = "/movies/unique.mp4")
            mediaLinkDao.insertLink(link)

            mediaLinkDao.findByFilePath("/movies/unique.mp4").shouldNotBeNull().id shouldBe link.id
            mediaLinkDao.findByFilePath("/movies/nope.mp4").shouldBeNull()
        }

        test("findByMetadataId") {
            val dirId = setupLibraryAndDirectory()
            val metadata = createMetadata()
            metadataDao.insertMetadata(metadata)

            val link = createMediaLink(dirId, metadataId = metadata.id)
            mediaLinkDao.insertLink(link)

            val found = mediaLinkDao.findByMetadataId(metadata.id)
            found.shouldHaveSize(1)
            found.first().id shouldBe link.id
        }

        test("findByMetadataIds") {
            val dirId = setupLibraryAndDirectory()
            val meta1 = createMetadata(id = ObjectId.next())
            val meta2 = createMetadata(id = ObjectId.next())
            metadataDao.insertMetadata(meta1)
            metadataDao.insertMetadata(meta2)

            val link1 = createMediaLink(dirId, filePath = "/movies/m1.mp4", metadataId = meta1.id)
            val link2 = createMediaLink(dirId, filePath = "/movies/m2.mp4", metadataId = meta2.id)
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)

            val found = mediaLinkDao.findByMetadataIds(listOf(meta1.id, meta2.id))
            found.shouldHaveSize(2)
        }

        test("findByMetadataIds with empty list returns empty") {
            mediaLinkDao.findByMetadataIds(emptyList()).shouldBeEmpty()
        }

        test("findByRootMetadataIds") {
            val dirId = setupLibraryAndDirectory()
            val metadata = createMetadata()
            metadataDao.insertMetadata(metadata)

            val link = createMediaLink(dirId, rootMetadataId = metadata.id, metadataId = metadata.id)
            mediaLinkDao.insertLink(link)

            val found = mediaLinkDao.findByRootMetadataIds(listOf(metadata.id))
            found.shouldHaveSize(1)
            found.first().id shouldBe link.id
        }

        test("findByDirectoryId") {
            val dirId = setupLibraryAndDirectory()
            val link1 = createMediaLink(dirId, filePath = "/movies/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/b.mp4")
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)

            val found = mediaLinkDao.findByDirectoryId(dirId)
            found.shouldHaveSize(2)
        }

        test("findByDirectoryIdAndDescriptor") {
            val dirId = setupLibraryAndDirectory()
            val video = createMediaLink(dirId, filePath = "/movies/a.mp4", descriptor = Descriptor.VIDEO)
            val subtitle = createMediaLink(dirId, filePath = "/movies/a.srt", descriptor = Descriptor.SUBTITLE)
            mediaLinkDao.insertLink(video)
            mediaLinkDao.insertLink(subtitle)

            mediaLinkDao.findByDirectoryIdAndDescriptor(dirId, Descriptor.VIDEO).shouldHaveSize(1)
            mediaLinkDao.findByDirectoryIdAndDescriptor(dirId, Descriptor.SUBTITLE).shouldHaveSize(1)
            mediaLinkDao.findByDirectoryIdAndDescriptor(dirId, Descriptor.AUDIO).shouldBeEmpty()
        }

        test("findByBasePathAndDescriptor") {
            val dirId = setupLibraryAndDirectory()
            val video = createMediaLink(dirId, filePath = "/movies/dir1/a.mp4", descriptor = Descriptor.VIDEO)
            val subtitle = createMediaLink(dirId, filePath = "/movies/dir1/a.srt", descriptor = Descriptor.SUBTITLE)
            val otherVideo = createMediaLink(dirId, filePath = "/movies/dir2/b.mp4", descriptor = Descriptor.VIDEO)
            mediaLinkDao.insertLink(video)
            mediaLinkDao.insertLink(subtitle)
            mediaLinkDao.insertLink(otherVideo)

            mediaLinkDao.findByBasePathAndDescriptor("/movies/dir1", Descriptor.VIDEO).shouldHaveSize(1)
            mediaLinkDao.findByBasePathAndDescriptor("/movies/dir1", Descriptor.SUBTITLE).shouldHaveSize(1)
            mediaLinkDao.findByBasePathAndDescriptor("/movies/", Descriptor.VIDEO).shouldHaveSize(2)
        }

        test("findByBasePathAndDescriptors") {
            val dirId = setupLibraryAndDirectory()
            val video = createMediaLink(dirId, filePath = "/movies/dir1/a.mp4", descriptor = Descriptor.VIDEO)
            val subtitle = createMediaLink(dirId, filePath = "/movies/dir1/a.srt", descriptor = Descriptor.SUBTITLE)
            mediaLinkDao.insertLink(video)
            mediaLinkDao.insertLink(subtitle)

            val found = mediaLinkDao.findByBasePathAndDescriptors(
                "/movies/dir1",
                listOf(Descriptor.VIDEO, Descriptor.SUBTITLE),
            )
            found.shouldHaveSize(2)
        }

        test("findByBasePath") {
            val dirId = setupLibraryAndDirectory()
            val link1 = createMediaLink(dirId, filePath = "/movies/dir1/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/dir1/b.srt", descriptor = Descriptor.SUBTITLE)
            val link3 = createMediaLink(dirId, filePath = "/movies/dir2/c.mp4")
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)
            mediaLinkDao.insertLink(link3)

            mediaLinkDao.findByBasePath("/movies/dir1").shouldHaveSize(2)
            mediaLinkDao.findByBasePath("/movies/").shouldHaveSize(3)
            mediaLinkDao.findByBasePath("/other").shouldBeEmpty()
        }

        test("findAllFilePaths") {
            val dirId = setupLibraryAndDirectory()
            val link1 = createMediaLink(dirId, filePath = "/movies/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/b.mp4")
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)

            val paths = mediaLinkDao.findAllFilePaths()
            paths.shouldHaveSize(2)
            paths.shouldContainExactlyInAnyOrder("/movies/a.mp4", "/movies/b.mp4")
        }

        test("descriptorForId") {
            val dirId = setupLibraryAndDirectory()
            val link = createMediaLink(dirId, descriptor = Descriptor.SUBTITLE)
            mediaLinkDao.insertLink(link)

            mediaLinkDao.descriptorForId(link.id) shouldBe Descriptor.SUBTITLE
            mediaLinkDao.descriptorForId(MediaLinkId("nope")).shouldBeNull()
        }

        test("updateMetadataIds - single update") {
            val dirId = setupLibraryAndDirectory()
            val metadata = createMetadata()
            metadataDao.insertMetadata(metadata)

            val link = createMediaLink(dirId)
            mediaLinkDao.insertLink(link)

            link.metadataId.shouldBeNull()

            mediaLinkDao.updateMetadataIds(
                MediaLinkMetadataUpdate(
                    mediaLinkId = link.id,
                    metadataId = metadata.id,
                    rootMetadataId = metadata.id,
                ),
            )

            val updated = mediaLinkDao.findById(link.id).shouldNotBeNull()
            updated.metadataId shouldBe metadata.id
            updated.rootMetadataId shouldBe metadata.id
        }

        test("updateMetadataIds - batch update") {
            val dirId = setupLibraryAndDirectory()
            val meta1 = createMetadata(id = ObjectId.next())
            val meta2 = createMetadata(id = ObjectId.next())
            metadataDao.insertMetadata(meta1)
            metadataDao.insertMetadata(meta2)

            val link1 = createMediaLink(dirId, filePath = "/movies/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/b.mp4")
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)

            mediaLinkDao.updateMetadataIds(
                listOf(
                    MediaLinkMetadataUpdate(link1.id, meta1.id, meta1.id),
                    MediaLinkMetadataUpdate(link2.id, meta2.id, meta2.id),
                ),
            )

            mediaLinkDao.findById(link1.id).shouldNotBeNull().metadataId shouldBe meta1.id
            mediaLinkDao.findById(link2.id).shouldNotBeNull().metadataId shouldBe meta2.id
        }

        test("deleteById") {
            val dirId = setupLibraryAndDirectory()
            val link = createMediaLink(dirId)
            mediaLinkDao.insertLink(link)

            mediaLinkDao.deleteById(link.id).shouldBeTrue()
            mediaLinkDao.findById(link.id).shouldBeNull()
        }

        test("deleteById returns false for nonexistent") {
            mediaLinkDao.deleteById(MediaLinkId("nonexistent")).shouldBeFalse()
        }

        test("deleteByBasePath") {
            val dirId = setupLibraryAndDirectory()
            val link1 = createMediaLink(dirId, filePath = "/movies/dir1/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/dir1/b.mp4")
            val link3 = createMediaLink(dirId, filePath = "/movies/dir2/c.mp4")
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)
            mediaLinkDao.insertLink(link3)

            val deleted = mediaLinkDao.deleteByBasePath("/movies/dir1")
            deleted.shouldHaveSize(2)
            deleted.shouldContainExactlyInAnyOrder(link1.id, link2.id)

            mediaLinkDao.all().shouldHaveSize(1)
        }

        test("deleteByMetadataId") {
            val dirId = setupLibraryAndDirectory()
            val metadata = createMetadata()
            metadataDao.insertMetadata(metadata)

            val link = createMediaLink(dirId, metadataId = metadata.id)
            mediaLinkDao.insertLink(link)

            mediaLinkDao.deleteByMetadataId(metadata.id)
            mediaLinkDao.findById(link.id).shouldBeNull()
        }

        test("deleteByRootMetadataId") {
            val dirId = setupLibraryAndDirectory()
            val metadata = createMetadata()
            metadataDao.insertMetadata(metadata)

            val link1 = createMediaLink(dirId, filePath = "/movies/a.mp4", rootMetadataId = metadata.id, metadataId = metadata.id)
            val link2 = createMediaLink(dirId, filePath = "/movies/b.mp4", rootMetadataId = metadata.id, metadataId = metadata.id)
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)

            mediaLinkDao.deleteByRootMetadataId(metadata.id)
            mediaLinkDao.all().shouldBeEmpty()
        }

        test("deleteDownloadByHash") {
            val dirId = setupLibraryAndDirectory()
            val link = createMediaLink(dirId, hash = "abc123")
            mediaLinkDao.insertLink(link)

            mediaLinkDao.deleteDownloadByHash("abc123").shouldBeTrue()
            mediaLinkDao.findById(link.id).shouldBeNull()

            mediaLinkDao.deleteDownloadByHash("nonexistent").shouldBeFalse()
        }

        fun createStreamEncoding(
            mediaLinkId: MediaLinkId,
            codecName: String = "h264",
            type: StreamEncodingType = StreamEncodingType.VIDEO,
            width: Int? = 1920,
            height: Int? = 1080,
        ): StreamEncoding {
            return StreamEncoding(
                id = StreamEncodingId(ObjectId.next()),
                mediaLinkId = mediaLinkId,
                codecName = codecName,
                type = type,
                width = width,
                height = height,
                default = true,
            )
        }

        test("insertStreamDetails and findStreamEncodings") {
            val dirId = setupLibraryAndDirectory()
            val link = createMediaLink(dirId)
            mediaLinkDao.insertLink(link)

            val stream = createStreamEncoding(link.id, codecName = "h264")
            mediaLinkDao.insertStreamDetails(listOf(stream))

            val found = mediaLinkDao.findStreamEncodings(link.id)
            found.shouldHaveSize(1)
            found.first().codecName shouldBe "h264"
        }

        test("countStreamDetails") {
            val dirId = setupLibraryAndDirectory()
            val link = createMediaLink(dirId)
            mediaLinkDao.insertLink(link)

            mediaLinkDao.countStreamDetails(link.id) shouldBe 0

            val streams = listOf(
                createStreamEncoding(link.id, codecName = "h264", type = StreamEncodingType.VIDEO),
                createStreamEncoding(link.id, codecName = "aac", type = StreamEncodingType.AUDIO, width = null, height = null),
            )
            mediaLinkDao.insertStreamDetails(streams)

            mediaLinkDao.countStreamDetails(link.id) shouldBe 2
        }

        test("findStreamEncodings by multiple ids") {
            val dirId = setupLibraryAndDirectory()
            val link1 = createMediaLink(dirId, filePath = "/movies/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/b.mp4")
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)

            mediaLinkDao.insertStreamDetails(
                listOf(
                    createStreamEncoding(link1.id, codecName = "h264"),
                    createStreamEncoding(link2.id, codecName = "h265"),
                ),
            )

            val result = mediaLinkDao.findStreamEncodings(listOf(link1.id, link2.id))
            result.keys.shouldHaveSize(2)
            result[link1.id]!!.first().codecName shouldBe "h264"
            result[link2.id]!!.first().codecName shouldBe "h265"
        }

        test("findLinksToAnalyze returns links without stream details") {
            val dirId = setupLibraryAndDirectory()
            val link1 = createMediaLink(dirId, filePath = "/movies/a.mp4")
            val link2 = createMediaLink(dirId, filePath = "/movies/b.mp4")
            mediaLinkDao.insertLink(link1)
            mediaLinkDao.insertLink(link2)

            // Both should need analysis initially
            mediaLinkDao.findLinksToAnalyze().shouldHaveSize(2)

            // Add stream details for link1
            mediaLinkDao.insertStreamDetails(
                listOf(createStreamEncoding(link1.id)),
            )

            // Only link2 should need analysis now
            val toAnalyze = mediaLinkDao.findLinksToAnalyze()
            toAnalyze.shouldHaveSize(1)
            toAnalyze.first() shouldBe link2.id
        }

        test("findLinksToAnalyze with limit") {
            val dirId = setupLibraryAndDirectory()
            (1..5).forEach { i ->
                mediaLinkDao.insertLink(createMediaLink(dirId, filePath = "/movies/$i.mp4"))
            }

            mediaLinkDao.findLinksToAnalyze(limit = 2).shouldHaveSize(2)
        }
    })
