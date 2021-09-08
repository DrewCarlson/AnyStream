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
import anystream.models.Movie
import anystream.models.TvShow
import kotlinx.serialization.Serializable


@Serializable
data class ImportMetadata(
    val contentIds: List<String>,
    val providerId: String,
    val mediaKind: MediaKind,
    val year: Int? = null,
    val refresh: Boolean = false,
)

@Serializable
data class QueryMetadata(
    val providerId: String?,
    val query: String,
    val mediaKind: MediaKind,
    val year: Int? = null,
)

@Serializable
sealed class MetadataMatch {
    abstract val contentId: String
    abstract val remoteId: String
    abstract val exists: Boolean

    data class MovieMatch(
        override val contentId: String,
        override val remoteId: String,
        override val exists: Boolean,
        val movie: Movie,
    ) : MetadataMatch()

    data class TvShowMatch(
        override val contentId: String,
        override val remoteId: String,
        override val exists: Boolean,
        val tvShow: TvShow,
    ) : MetadataMatch()
}

@Serializable
sealed class ImportMetadataResult {

    @Serializable
    data class Success(
        val match: MetadataMatch,
        val subresults: List<ImportMetadataResult> = emptyList(),
    ) : ImportMetadataResult()

    @Serializable
    data class ErrorMediaAlreadyExists(
        val existingMediaId: String,
        val match: MetadataMatch,
    ) : ImportMetadataResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String,
    ) : ImportMetadataResult()

    @Serializable
    data class ErrorDataProviderException(
        val stacktrace: String,
    ) : ImportMetadataResult()
}

@Serializable
sealed class QueryMetadataResult {

    @Serializable
    data class Success(
        val providerId: String,
        val results: List<MetadataMatch>
    ) : QueryMetadataResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String,
    ) : QueryMetadataResult()

    @Serializable
    data class ErrorDataProviderException(
        val stacktrace: String,
    ) : QueryMetadataResult()
}