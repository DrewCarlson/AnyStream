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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
        val metadata = Metadata(
            id = "metadata",
            mediaType = MediaType.MOVIE,
            mediaKind = MediaKind.MOVIE,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        val mediaLink = MediaLink(
            id = "media-link",
            updatedAt = Clock.System.now(),
            createdAt = Clock.System.now(),
            type = MediaLinkType.LOCAL,
            descriptor = Descriptor.VIDEO,
            directoryId = directory.id,
            mediaKind = MediaKind.MOVIE,
            metadataId = metadata.id,
        )
        val user = User(
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

        val playbackState = PlaybackState(
            id = "playback-state",
            mediaLinkId = "media-link",
            metadataId = "metadata",
            position = Duration.ZERO,
            runtime = 100.seconds,
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