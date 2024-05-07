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
import kotlinx.serialization.Serializable

@Serializable
data class MediaScanRequest(
    val filePath: String,
)

@Serializable
sealed class MediaScanResult {

    @Serializable
    data class Success(
        val addedIds: List<String>,
        val removedIds: List<String>,
        val existingIds: List<String>,
    ) : MediaScanResult()

    @Serializable
    data object ErrorNothingToScan : MediaScanResult()

    @Serializable
    data object ErrorFileNotFound : MediaScanResult()

    @Serializable
    data object ErrorInvalidConfiguration : MediaScanResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String,
    ) : MediaScanResult()
}

@Serializable
sealed class MediaAnalyzerResult {
    @Serializable
    data class Success(
        val mediaLinkId: String,
        val streams: List<StreamEncoding>,
    ) : MediaAnalyzerResult()

    @Serializable
    data class ProcessError(
        val stacktrace: String,
    ) : MediaAnalyzerResult()

    @Serializable
    data object ErrorFileNotFound : MediaAnalyzerResult()

    @Serializable
    data object ErrorNothingToImport : MediaAnalyzerResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String,
    ) : MediaAnalyzerResult()
}
