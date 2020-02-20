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
package anystream.models.api

import anystream.models.MediaKind
import kotlinx.serialization.Serializable


@Serializable
data class ImportMedia(
    val contentPath: String,
    val mediaKind: MediaKind,
)

@Serializable
sealed class ImportMediaResult {

    @Serializable
    data class Success(
        val mediaId: String,
        val mediaRefId: String,
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

    @Serializable
    data class ErrorDataProviderException(
        val stacktrace: String,
    ) : ImportMediaResult()
}
