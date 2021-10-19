/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SinglePageAppTests {

    @Test
    fun testRootPathReturnsIndex() {
        testWithConfiguration({
            defaultFile = "index.html"
            staticFilePath = "$RESOURCES/static"
        }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(
                    File("$RESOURCES/static/index.html").readText(),
                    response.content
                )
            }
        }
    }

    @Test
    fun testExistingFileReturnsContents() {
        testWithConfiguration({
            defaultFile = "index.html"
            staticFilePath = "$RESOURCES/static"
        }) {
            handleRequest(HttpMethod.Get, "/test.json").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(
                    File("$RESOURCES/static/test.json").readText(),
                    response.content
                )
            }
        }
    }

    @Test
    fun testNonExistentFileReturnsIndex() {
        testWithConfiguration({
            defaultFile = "index.html"
            staticFilePath = "$RESOURCES/static"
        }) {
            handleRequest(HttpMethod.Get, "/not-real").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(
                    File("$RESOURCES/static/index.html").readText(),
                    response.content
                )
            }
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
            handleRequest(HttpMethod.Get, "/static/test.json").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNull(response.content)
            }
        }
    }

    private fun testWithConfiguration(
        configure: SinglePageApp.() -> Unit,
        test: TestApplicationEngine.() -> Unit
    ) {
        withTestApplication({
            install(SinglePageApp, configure)
            /*routing {
                get("/static/test.json") {
                    call.respond(HttpStatusCode.NoContent)
                }
            }*/
        }, test)
    }
}