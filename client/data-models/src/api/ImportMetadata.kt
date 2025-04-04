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

import anystream.models.*
import kotlinx.serialization.Serializable

@Serializable
data class ImportMetadata(
    val metadataIds: List<String>,
    val providerId: String,
    val mediaKind: MediaKind,
    val year: Int? = null,
    val refresh: Boolean = false,
)

@Serializable
data class QueryMetadata(
    val providerId: String?,
    val mediaKind: MediaKind,
    val query: String? = null,
    val metadataId: String? = null,
    val year: Int? = null,
    val extras: Extras? = null,
    val cacheContent: Boolean = false,
    val firstResultOnly: Boolean = false,
) {
    @Serializable
    sealed class Extras {
        @Serializable
        data class TvShowExtras(
            val seasonNumber: Int? = null,
            val episodeNumber: Int? = null,
        ) : Extras()

        fun asTvShowExtras(): TvShowExtras? {
            return this as? TvShowExtras
        }
    }
}

@Serializable
sealed class MetadataMatch {
    abstract val providerId: String
    abstract val remoteMetadataId: String?
    abstract val remoteId: String
    abstract val exists: Boolean
    abstract val metadataId: String?

    @Serializable
    data class MovieMatch(
        val movie: Movie,
        override val remoteMetadataId: String?,
        override val remoteId: String,
        override val exists: Boolean,
        override val providerId: String,
    ) : MetadataMatch() {
        override val metadataId: String? = if (exists) movie.id else null
    }

    @Serializable
    data class TvShowMatch(
        val tvShow: TvShow,
        val seasons: List<TvSeason>,
        val episodes: List<Episode>,
        override val remoteMetadataId: String?,
        override val remoteId: String,
        override val exists: Boolean,
        override val providerId: String,
    ) : MetadataMatch() {
        override val metadataId:  String? = if (exists) tvShow.id else null
    }
}

@Serializable
sealed class MediaLinkMatchResult {

    @Serializable
    data class Success(
        val mediaLink: MediaLink?,
        val directory: Directory?,
        val matches: List<MetadataMatch>,
        val subResults: List<MediaLinkMatchResult>,
    ) : MediaLinkMatchResult()

    @Serializable
    data class NoSupportedFiles(
        val mediaLink: MediaLink?,
        val directory: Directory?,
    ) : MediaLinkMatchResult()

    @Serializable
    data class FileNameParseFailed(
        val mediaLink: MediaLink?,
        val directory: Directory?,
    ) : MediaLinkMatchResult()

    @Serializable
    data class NoMatchesFound(
        val mediaLink: MediaLink?,
        val directory: Directory?,
    ) : MediaLinkMatchResult()
}

@Serializable
sealed class ImportMetadataResult {

    @Serializable
    data class Success(
        val match: MetadataMatch,
        val subresults: List<ImportMetadataResult> = emptyList(),
    ) : ImportMetadataResult()

    @Serializable
    data class ErrorMetadataAlreadyExists(
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
        val results: List<MetadataMatch>,
        val extras: QueryMetadata.Extras?,
    ) : QueryMetadataResult()

    @Serializable
    object ErrorProviderNotFound : QueryMetadataResult()

    @Serializable
    data class ErrorDatabaseException(
        val stacktrace: String,
    ) : QueryMetadataResult()

    @Serializable
    data class ErrorDataProviderException(
        val stacktrace: String,
    ) : QueryMetadataResult()
}
