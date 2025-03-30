package anystream.db

import anystream.models.Descriptor
import anystream.models.MediaKind
import anystream.models.MediaLinkType
import anystream.models.MediaType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import org.jooq.DSLContext

class PlaybackStatesDaoTest : FunSpec({

    val db: DSLContext by bindTestDatabase()
    val libraryDao by bindForTest({ LibraryDao(db) })
    val userDao by bindForTest({ UserDao(db) })
    val metadataDao by bindForTest({ MetadataDao(db) })
    val mediaLinkDao by bindForTest({ MediaLinkDao(db) })
    val playbackStatesDao by bindForTest({ PlaybackStatesDao(db) })

    test("insert playbackState") {
        libraryDao.insertDefaultLibraries()
        val library = libraryDao.all()
            .firstOrNull { it.mediaKind == MediaKind.MOVIE }
            .shouldNotBeNull()
        val directory = libraryDao.insertDirectory(
            parentId = null,
            libraryId = library.id,
            path = "/dir"
        )
        val metadata = anystream.models.Metadata(
            id = "metadata",
            mediaType = MediaType.MOVIE,
            mediaKind = MediaKind.MOVIE,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        val mediaLink = anystream.models.MediaLink(
            id = "media-link",
            updatedAt = Clock.System.now(),
            createdAt = Clock.System.now(),
            type = MediaLinkType.LOCAL,
            descriptor = Descriptor.VIDEO,
            directoryId = directory.id,
            mediaKind = MediaKind.MOVIE,
            metadataId = metadata.id,
        )
        val user = anystream.models.User(
            id = "user",
            displayName = "User",
            passwordHash = "passwordHash",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            username = "user"
        )

        metadataDao.insertMetadata(metadata)
            .shouldBe(metadata.id)
        mediaLinkDao.insertLink(mediaLink)
            .shouldBeTrue()
        userDao.insertUser(user, emptySet())
            .shouldNotBeNull()
            .shouldBeEqual(user)

        val playbackState = anystream.models.PlaybackState(
            id = "playback-state",
            mediaLinkId = "media-link",
            metadataId = "metadata",
            position = 0.0,
            runtime = 100.0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            userId = "user",
        )

        playbackStatesDao.insert(playbackState).shouldBeTrue()

        playbackStatesDao.fetchById(playbackState.id)
            .shouldNotBeNull()
            .shouldBe(playbackState)

        playbackStatesDao.fetchByIds(listOf(playbackState.id))
            .shouldContainExactly(playbackState)
    }

    test("fetch when empty") {
        playbackStatesDao.fetchById("").shouldBeNull()
        playbackStatesDao.fetchByIds(listOf("")).shouldBeEmpty()
    }
})