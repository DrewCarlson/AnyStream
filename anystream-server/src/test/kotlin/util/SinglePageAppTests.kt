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
import io.ktor.server.testing.*
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
    fun testExistingFileReturnContents() {
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
    fun testNonExistentFileRedirectsToIndex() {
        testWithConfiguration({
            defaultFile = "index.html"
            staticFilePath = "$RESOURCES/static"
        }) {
            handleRequest(HttpMethod.Get, "/not-real").apply {
                assertEquals(HttpStatusCode.Found, response.status())
                assertEquals("/", response.headers["location"])
            }
        }
    }

    private fun testWithConfiguration(
        configure: SinglePageApp.() -> Unit,
        test: TestApplicationEngine.() -> Unit
    ) {
        withTestApplication({ install(SinglePageApp, configure) }, test)
    }
}