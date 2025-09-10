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
package anystream.models.api

import anystream.models.StreamEncoding
import dev.drewhamilton.poko.Poko
import kotlinx.serialization.Serializable

@Poko
@Serializable
class MediaScanRequest(
    val filePath: String,
)

@Serializable
sealed class MediaScanResult {

    @Serializable
    data class Success(
        val directories: ContentIdContainer,
        val mediaLinks: ContentIdContainer,
    ) : MediaScanResult() {
        fun merge(others: List<Success>): MediaScanResult {
            return others.fold(this) { result, next ->
                Success(
                    directories = result.directories.merge(next.directories),
                    mediaLinks = result.mediaLinks.merge(next.mediaLinks)
                )
            }
        }
    }

    /**
     * The file or directory exists but the parent directory is
     * not tracked within a library directory.
     */
    @Serializable
    data object ErrorNotInLibrary : MediaScanResult()

    /**
     * The directory to be scanned contained no processable files.
     */
    @Serializable
    data object ErrorNothingToScan : MediaScanResult()

    /**
     * The file or directory to be scanned could not be found.
     */
    @Serializable
    data object ErrorFileNotFound : MediaScanResult()

    @Serializable
    data object ErrorAbsolutePathRequired : MediaScanResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String,
    ) : MediaScanResult()
}

@Serializable
data class ContentIdContainer(
    val addedIds: List<String> = emptyList(),
    val removedIds: List<String> = emptyList(),
    val existingIds: List<String> = emptyList(),
) {
    companion object {
        val EMPTY = ContentIdContainer(emptyList(), emptyList(), emptyList())
    }

    fun merge(container: ContentIdContainer): ContentIdContainer {
        return ContentIdContainer(
            addedIds = addedIds + container.addedIds,
            removedIds = removedIds + container.removedIds,
            existingIds = existingIds + container.existingIds,
        )
    }
}

@Serializable
sealed class MediaAnalyzerResult {
    @Poko
    @Serializable
    class Success(
        val mediaLinkId: String,
        val streams: List<StreamEncoding>,
    ) : MediaAnalyzerResult()

    @Poko
    @Serializable
    class ProcessError(
        val stacktrace: String,
    ) : MediaAnalyzerResult()

    @Serializable
    data object ErrorFileNotFound : MediaAnalyzerResult()

    @Serializable
    data object ErrorNothingToImport : MediaAnalyzerResult()

    @Poko
    @Serializable
    class ErrorDatabaseException(
        val stacktrace: String,
    ) : MediaAnalyzerResult()
}
