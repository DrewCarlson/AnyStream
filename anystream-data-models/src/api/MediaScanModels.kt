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

import anystream.models.StreamEncodingDetails
import kotlinx.serialization.Serializable

@Serializable
data class MediaScanRequest(
    val filePath: String
)

@Serializable
sealed class MediaScanResult {

    @Serializable
    data class Success(
        val parentMediaLinkGid: String,
        val addedMediaLinkGids: List<String>,
        val removedMediaLinkGids: List<String>,
        val existingMediaLinkGids: List<String>
    ) : MediaScanResult() {
        val allValidGids = addedMediaLinkGids + existingMediaLinkGids
    }

    @Serializable
    object ErrorNothingToScan : MediaScanResult() {
        override fun toString(): String = this::class.simpleName.orEmpty()
    }

    @Serializable
    object ErrorFileNotFound : MediaScanResult() {
        override fun toString(): String = this::class.simpleName.orEmpty()
    }

    @Serializable
    object ErrorInvalidConfiguration : MediaScanResult() {
        override fun toString(): String = this::class.simpleName.orEmpty()
    }

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String
    ) : MediaScanResult()
}

@Serializable
sealed class MediaAnalyzerResult {
    @Serializable
    data class Success(
        val mediaLinkId: String,
        val streams: List<StreamEncodingDetails>
    ) : MediaAnalyzerResult()

    @Serializable
    data class ProcessError(
        val stacktrace: String
    ) : MediaAnalyzerResult()

    @Serializable
    object ErrorFileNotFound : MediaAnalyzerResult()

    @Serializable
    object ErrorNothingToImport : MediaAnalyzerResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String
    ) : MediaAnalyzerResult()
}
