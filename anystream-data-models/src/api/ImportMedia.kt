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

import anystream.models.MediaKind
import anystream.models.MediaReference
import anystream.models.StreamEncodingDetails
import kotlinx.serialization.Serializable

@Serializable
data class ImportMedia(
    val contentPath: String,
    val mediaKind: MediaKind,
    val mediaId: String? = null,
)

@Serializable
sealed class ImportMediaResult {

    @Serializable
    data class Success(
        val mediaId: String,
        val mediaReference: MediaReference,
        val subresults: List<ImportMediaResult> = emptyList(),
    ) : ImportMediaResult()

    @Serializable
    object ErrorNothingToImport : ImportMediaResult()
    @Serializable
    object ErrorFileNotFound : ImportMediaResult()
    @Serializable
    data class ErrorMediaMatchNotFound(
        val contentPath: String,
        val query: String,
        val results: List<QueryMetadataResult>,
    ) : ImportMediaResult()
    @Serializable
    object ErrorMediaRefNotFound : ImportMediaResult()

    @Serializable
    data class ErrorMediaRefAlreadyExists(
        val existingRefId: String
    ) : ImportMediaResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String,
    ) : ImportMediaResult()
}

@Serializable
sealed class ImportStreamDetailsResult {
    @Serializable
    data class Success(
        val mediaRefId: String,
        val streamDetails: List<StreamEncodingDetails>,
    ) : ImportStreamDetailsResult()

    @Serializable
    data class ProcessError(
        val stacktrace: String,
    ) : ImportStreamDetailsResult()

    @Serializable
    object ErrorFileNotFound : ImportStreamDetailsResult()

    @Serializable
    object ErrorNothingToImport : ImportStreamDetailsResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String,
    ) : ImportStreamDetailsResult()
}
