/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.util

import anystream.test.RESOURCES
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class SinglePageAppTests {

    @Test
    fun testRootPathReturnsIndex() {
        testWithConfiguration({
            defaultFile = "index.html"
            staticFilePath = "$RESOURCES/static"
        }) {
            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(File("$RESOURCES/static/index.html").readText(), response.bodyAsText())
        }
    }

    @Test
    fun testExistingFileReturnsContents() {
        testWithConfiguration({
            defaultFile = "index.html"
            staticFilePath = "$RESOURCES/static"
        }) {
            val response = client.get("/test.json")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(File("$RESOURCES/static/test.json").readText(), response.bodyAsText())
        }
    }

    @Test
    fun testNonExistentFileReturnsIndex() {
        testWithConfiguration({
            defaultFile = "index.html"
            staticFilePath = "$RESOURCES/static"
        }) {
            val response = client.get("/not-real")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(File("$RESOURCES/static/index.html").readText(), response.bodyAsText())
        }
    }

    @Ignore("Doesn't work unless a handler is defined for the target path, should be fixed.")
    @Test
    fun testFileInIgnorePathIsNotReturned() {
        testWithConfiguration({
            defaultFile = "index.html"
            staticFilePath = RESOURCES
            ignoreBasePath = "/static"
        }) {
            val response = client.get("/test.json")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, response.bodyAsChannel().totalBytesRead)
        }
    }

    private fun testWithConfiguration(
        configure: SinglePageApp.() -> Unit,
        test: suspend ApplicationTestBuilder.() -> Unit
    ) {
        testApplication {
            install(SinglePageApp, configure)
            /*routing {
                get("/static/test.json") {
                    call.respond(HttpStatusCode.NoContent)
                }
            }*/
            test()
        }
    }
}
