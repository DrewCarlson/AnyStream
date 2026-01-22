/**
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
package anystream.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.slf4j.MDC

class LoggingContextTest : FunSpec({

    beforeEach {
        MDC.clear()
    }

    afterEach {
        MDC.clear()
    }

    context("withLoggingContext") {
        test("should set and clear MDC values") {
            MDC.get(MdcKeys.LIBRARY_ID).shouldBeNull()

            withLoggingContext({
                libraryId("lib-123")
                scanId("scan-456")
            }) {
                MDC.get(MdcKeys.LIBRARY_ID) shouldBe "lib-123"
                MDC.get(MdcKeys.SCAN_ID) shouldBe "scan-456"
            }

            MDC.get(MdcKeys.LIBRARY_ID).shouldBeNull()
            MDC.get(MdcKeys.SCAN_ID).shouldBeNull()
        }

        test("should convert enum values to their names") {
            withLoggingContext({ phase(ProcessingPhase.SCAN) }) {
                MDC.get(MdcKeys.PHASE) shouldBe "SCAN"
            }

            withLoggingContext({ phase(ProcessingPhase.MATCH) }) {
                MDC.get(MdcKeys.PHASE) shouldBe "MATCH"
            }

            withLoggingContext({ phase(ProcessingPhase.IMPORT) }) {
                MDC.get(MdcKeys.PHASE) shouldBe "IMPORT"
            }
        }

        test("should skip null values") {
            val nullValue: String? = null

            withLoggingContext({
                libraryId("lib-123")
                mediaLinkId(nullValue)
            }) {
                MDC.get(MdcKeys.LIBRARY_ID) shouldBe "lib-123"
                MDC.get(MdcKeys.MEDIA_LINK_ID).shouldBeNull()
            }
        }

        test("should handle nested contexts") {
            withLoggingContext({
                libraryId("lib-123")
                phase(ProcessingPhase.SCAN)
            }) {
                MDC.get(MdcKeys.LIBRARY_ID) shouldBe "lib-123"
                MDC.get(MdcKeys.PHASE) shouldBe "SCAN"

                withLoggingContext({
                    phase(ProcessingPhase.MATCH)
                    mediaLinkId("link-789")
                }) {
                    // Inner context should override phase
                    MDC.get(MdcKeys.PHASE) shouldBe "MATCH"
                    // Inner context should add new key
                    MDC.get(MdcKeys.MEDIA_LINK_ID) shouldBe "link-789"
                    // Outer context should still be visible
                    MDC.get(MdcKeys.LIBRARY_ID) shouldBe "lib-123"
                }

                // After inner context, phase should be restored
                MDC.get(MdcKeys.PHASE) shouldBe "SCAN"
                // Inner-only key should be cleared
                MDC.get(MdcKeys.MEDIA_LINK_ID).shouldBeNull()
                // Outer key should still be present
                MDC.get(MdcKeys.LIBRARY_ID) shouldBe "lib-123"
            }
        }

        test("should propagate context across coroutine boundaries") {
            withLoggingContext({
                scanId("scan-123")
                libraryId("lib-456")
            }) {
                // Verify context is available in current coroutine
                MDC.get(MdcKeys.SCAN_ID) shouldBe "scan-123"

                // Verify context propagates to child coroutines
                withContext(Dispatchers.Default) {
                    MDC.get(MdcKeys.SCAN_ID) shouldBe "scan-123"
                    MDC.get(MdcKeys.LIBRARY_ID) shouldBe "lib-456"
                }
            }
        }

        test("should return value from block") {
            val result = withLoggingContext({ libraryId("lib-123") }) {
                "computed-value"
            }

            result shouldBe "computed-value"
        }

        test("should clear context even when exception is thrown") {
            try {
                withLoggingContext({ libraryId("lib-123") }) {
                    MDC.get(MdcKeys.LIBRARY_ID) shouldBe "lib-123"
                    throw RuntimeException("test exception")
                }
            } catch (_: RuntimeException) {
                // Expected
            }

            MDC.get(MdcKeys.LIBRARY_ID).shouldBeNull()
        }

        test("should support generic put for custom keys") {
            withLoggingContext({
                libraryId("lib-123")
                put("customKey", "customValue")
                put("nullKey", null)
            }) {
                MDC.get(MdcKeys.LIBRARY_ID) shouldBe "lib-123"
                MDC.get("customKey") shouldBe "customValue"
                MDC.get("nullKey").shouldBeNull()
            }

            MDC.get("customKey").shouldBeNull()
        }
    }

    context("concurrentMap MDC propagation") {
        test("should propagate MDC context to concurrent operations") {
            withLoggingContext({
                scanId("scan-abc")
                libraryId("lib-xyz")
                phase(ProcessingPhase.SCAN)
            }) {
                val items = listOf(1, 2, 3, 4, 5)

                val results = items.asFlow()
                    .concurrentMap(this, concurrencyLevel = 3) { item ->
                        // Each concurrent operation should see the MDC context
                        val scanId = MDC.get(MdcKeys.SCAN_ID)
                        val libraryId = MDC.get(MdcKeys.LIBRARY_ID)
                        val phase = MDC.get(MdcKeys.PHASE)
                        Triple(scanId, libraryId, phase) to item
                    }
                    .toList()

                results shouldHaveSize 5
                results.forEach { (context, _) ->
                    context.first shouldBe "scan-abc"
                    context.second shouldBe "lib-xyz"
                    context.third shouldBe "SCAN"
                }
            }
        }

        test("should maintain context isolation between parallel operations") {
            val scanId1 = "scan-111"
            val scanId2 = "scan-222"

            supervisorScope {
                val results1 = async {
                    withLoggingContext({ scanId(scanId1) }) {
                        listOf("a", "b", "c").asFlow()
                            .concurrentMap(this, concurrencyLevel = 2) { item ->
                                MDC.get(MdcKeys.SCAN_ID) to item
                            }
                            .toList()
                    }
                }

                val results2 = async {
                    withLoggingContext({ scanId(scanId2) }) {
                        listOf("x", "y", "z").asFlow()
                            .concurrentMap(this, concurrencyLevel = 2) { item ->
                                MDC.get(MdcKeys.SCAN_ID) to item
                            }
                            .toList()
                    }
                }

                val all1 = results1.await()
                val all2 = results2.await()

                // All items from first flow should have scanId1
                all1.forEach { (scanId, _) ->
                    scanId shouldBe scanId1
                }
                all1.map { it.second } shouldContainExactlyInAnyOrder listOf("a", "b", "c")

                // All items from second flow should have scanId2
                all2.forEach { (scanId, _) ->
                    scanId shouldBe scanId2
                }
                all2.map { it.second } shouldContainExactlyInAnyOrder listOf("x", "y", "z")
            }
        }

        test("should handle high concurrency without context leakage") {
            val itemCount = 100
            val concurrency = 20

            withLoggingContext({
                scanId("main-scan")
                phase(ProcessingPhase.MATCH)
            }) {
                val items = (1..itemCount).toList()

                val results = items.asFlow()
                    .concurrentMap(this, concurrencyLevel = concurrency) { item ->
                        // Simulate some work
                        val scanId = MDC.get(MdcKeys.SCAN_ID)
                        val phase = MDC.get(MdcKeys.PHASE)
                        Triple(scanId, phase, item)
                    }
                    .toList()

                results shouldHaveSize itemCount

                // Every single result should have the correct context
                results.forEach { (scanId, phase, _) ->
                    scanId shouldBe "main-scan"
                    phase shouldBe "MATCH"
                }
            }
        }

        test("should preserve context when concurrent operations complete at different times") {
            withLoggingContext({
                scanId("async-scan")
                libraryId("async-lib")
            }) {
                supervisorScope {
                    val deferreds = (1..10).map { index ->
                        async(Dispatchers.Default) {
                            // Each async block should have the MDC context
                            val scanId = MDC.get(MdcKeys.SCAN_ID)
                            val libraryId = MDC.get(MdcKeys.LIBRARY_ID)
                            index to (scanId to libraryId)
                        }
                    }

                    val results = deferreds.awaitAll()

                    results shouldHaveSize 10
                    results.forEach { (_, context) ->
                        context.first shouldBe "async-scan"
                        context.second shouldBe "async-lib"
                    }
                }
            }
        }
    }

    context("newScanId") {
        test("should generate 8 character scan IDs") {
            val scanId = newScanId()
            scanId shouldHaveLength 8
        }

        test("should generate unique scan IDs") {
            val ids = (1..100).map { newScanId() }.toSet()
            ids shouldHaveSize 100
        }

        test("should generate valid hex-like characters") {
            val scanId = newScanId()
            scanId shouldMatch Regex("[a-f0-9]{8}")
        }
    }

    context("abbreviatePath") {
        test("should not abbreviate short paths") {
            "/media/movies".abbreviatePath() shouldBe "/media/movies"
            "/a/b/c".abbreviatePath() shouldBe "/a/b/c"
        }

        test("should abbreviate long paths to last 3 segments by default") {
            "/home/user/media/movies/Inception (2010)".abbreviatePath() shouldBe ".../media/movies/Inception (2010)"
            "/very/long/path/to/some/directory".abbreviatePath() shouldBe ".../to/some/directory"
        }

        test("should respect custom segment count") {
            "/a/b/c/d/e".abbreviatePath(segments = 2) shouldBe ".../d/e"
            "/a/b/c/d/e".abbreviatePath(segments = 4) shouldBe ".../b/c/d/e"
            "/a/b/c/d/e".abbreviatePath(segments = 5) shouldBe "/a/b/c/d/e"
        }

        test("should handle Windows-style paths") {
            "C:\\Users\\Drew\\Movies\\Inception".abbreviatePath() shouldBe ".../Drew/Movies/Inception"
        }

        test("should handle empty segments") {
            "//a//b//c//d".abbreviatePath() shouldBe ".../b/c/d"
        }
    }
})
