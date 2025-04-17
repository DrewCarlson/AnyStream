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
package anystream.service.stream

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

internal fun FileSystem.observeNewSegmentFiles(
    directory: Path,
    fileMatchRegex: Regex
): Flow<Int> {
    return callbackFlow {
        val watchService = newWatchService()
        directory.register(watchService, ENTRY_CREATE)
        while (isActive) {
            val key = watchService.poll(2, TimeUnit.SECONDS)
            key?.pollEvents()?.forEach { event ->
                val targetPath = (event.context() as? Path) ?: return@forEach
                val fileName = directory.resolve(targetPath).name
                if (fileMatchRegex.containsMatchIn(fileName)) {
                    val segmentIndex =
                        checkNotNull(fileMatchRegex.find(fileName)).groupValues.last().toInt()
                    send(segmentIndex)
                }
            }
            if (key?.reset() == false) break
        }
        awaitClose {
            try {
                watchService.close()
            } catch (_: IOException) {
                //logger.error("Failed to close File Watch Service", e)
            }
        }
    }.distinctUntilChanged().flowOn(IO)
}
