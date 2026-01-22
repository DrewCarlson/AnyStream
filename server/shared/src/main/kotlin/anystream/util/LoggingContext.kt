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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import java.util.UUID

object MdcKeys {
    const val SCAN_ID = "scanId"
    const val LIBRARY_ID = "libraryId"
    const val LIBRARY_NAME = "libraryName"
    const val MEDIA_KIND = "mediaKind"
    const val DIRECTORY_ID = "directoryId"
    const val DIRECTORY_PATH = "directoryPath"
    const val MEDIA_LINK_ID = "mediaLinkId"
    const val METADATA_ID = "metadataId"
    const val ROOT_METADATA_ID = "rootMetadataId"
    const val PHASE = "phase"
    const val PROVIDER_ID = "providerId"
}

enum class ProcessingPhase { SCAN, MATCH, IMPORT, LINK, ANALYZE }

@DslMarker
annotation class LoggingContextDsl

@LoggingContextDsl
class LoggingContextBuilder {
    private val entries = mutableMapOf<String, String>()

    fun scanId(value: String) { entries[MdcKeys.SCAN_ID] = value }
    fun libraryId(value: String) { entries[MdcKeys.LIBRARY_ID] = value }
    fun libraryName(value: String) { entries[MdcKeys.LIBRARY_NAME] = value }
    fun mediaKind(value: Enum<*>) { entries[MdcKeys.MEDIA_KIND] = value.name }
    fun directoryId(value: String) { entries[MdcKeys.DIRECTORY_ID] = value }
    fun directoryPath(value: String) { entries[MdcKeys.DIRECTORY_PATH] = value }
    fun mediaLinkId(value: String?) { value?.let { entries[MdcKeys.MEDIA_LINK_ID] = it } }
    fun metadataId(value: String) { entries[MdcKeys.METADATA_ID] = value }
    fun rootMetadataId(value: String) { entries[MdcKeys.ROOT_METADATA_ID] = value }
    fun phase(value: ProcessingPhase) { entries[MdcKeys.PHASE] = value.name }
    fun providerId(value: String) { entries[MdcKeys.PROVIDER_ID] = value }

    fun put(key: String, value: Any?) {
        value?.let { entries[key] = it.toString() }
    }

    @PublishedApi
    internal fun build(): Map<String, String> = entries.toMap()
}

suspend inline fun <T> withLoggingContext(
    configure: LoggingContextBuilder.() -> Unit,
    crossinline block: suspend CoroutineScope.() -> T,
): T {
    val entries = LoggingContextBuilder().apply(configure).build()

    // Save old values to restore after block completes
    val oldValues = entries.keys.associateWith { MDC.get(it) }
    entries.forEach { (key, value) -> MDC.put(key, value) }

    return try {
        withContext(MDCContext()) { block() }
    } finally {
        oldValues.forEach { (key, oldValue) ->
            if (oldValue != null) {
                MDC.put(key, oldValue)
            } else {
                MDC.remove(key)
            }
        }
    }
}

fun newScanId(): String = UUID.randomUUID().toString().take(8)

fun String.abbreviatePath(segments: Int = 3): String {
    val parts = split("/", "\\").filter { it.isNotEmpty() }
    return if (parts.size <= segments) this
    else ".../" + parts.takeLast(segments).joinToString("/")
}
