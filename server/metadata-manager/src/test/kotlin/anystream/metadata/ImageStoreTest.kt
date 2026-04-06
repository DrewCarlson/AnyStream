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
package anystream.metadata

import anystream.db.bindFileSystem
import anystream.db.bindForTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.ktor.client.HttpClient
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class ImageStoreTest :
    FunSpec({

        val fs by bindFileSystem()
        val imageStore by bindForTest({
            ImageStore(fs.getPath("/data"), HttpClient())
        })

        context("getMetadataImagePath") {
            test("returns path under metadata directory") {
                val path = imageStore.getMetadataImagePath("poster", "movie-123", "root-456")

                path.toString() shouldContain "metadata"
                path.toString() shouldContain "root-456"
                path.toString() shouldContain "poster"
                path.toString() shouldEndWith "movie-123"
            }

            test("uses metadataId as rootMetadataId when not specified") {
                val path = imageStore.getMetadataImagePath("backdrop", "movie-123")

                path.absolutePathString() shouldContain "movie-123"
                path.absolutePathString() shouldContain "backdrop"
            }

            test("creates parent directories") {
                val path = imageStore.getMetadataImagePath("poster", "id1", "root1")

                path.parent.exists() shouldBe true
            }

            test("different image types produce different paths") {
                val posterPath = imageStore.getMetadataImagePath("poster", "id1", "root1")
                val backdropPath = imageStore.getMetadataImagePath("backdrop", "id1", "root1")

                posterPath shouldNotBe backdropPath
            }
        }

        context("getPersonImagePath") {
            test("returns path under metadata/people directory") {
                val path = imageStore.getPersonImagePath("person-789")

                path.absolutePathString() shouldContain "metadata"
                path.absolutePathString() shouldContain "people"
                path.absolutePathString() shouldEndWith "person-789"
            }

            test("creates parent directories") {
                val path = imageStore.getPersonImagePath("person-789")

                path.parent.exists() shouldBe true
            }

            test("different person ids produce different paths") {
                val path1 = imageStore.getPersonImagePath("person-1")
                val path2 = imageStore.getPersonImagePath("person-2")

                path1 shouldNotBe path2
            }
        }
    })
