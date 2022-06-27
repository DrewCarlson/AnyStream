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
package anystream.metadata

import anystream.models.MediaKind
import anystream.models.api.*
import org.slf4j.LoggerFactory

class MetadataManager(
    private val providers: List<MetadataProvider>
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun findByRemoteId(remoteId: String): QueryMetadataResult {
        val (providerId, mediaKindString, rawId) = remoteId.split(':')
        val mediaKind = MediaKind.valueOf(mediaKindString.uppercase())
        var parsedId = rawId
        val extras = when (mediaKind) {
            MediaKind.TV -> {
                if (rawId.contains('-')) {
                    val parts = rawId.split('-')
                    parsedId = parts[0]
                    QueryMetadata.Extras.TvShowExtras(
                        seasonNumber = parts.getOrNull(1)?.toIntOrNull(),
                        episodeNumber = parts.getOrNull(2)?.toIntOrNull()
                    )
                } else null
            }
            else -> null
        }

        return search(
            QueryMetadata(
                providerId = providerId,
                metadataGid = parsedId,
                mediaKind = mediaKind,
                extras = extras
            )
        ).firstOrNull() ?: QueryMetadataResult.ErrorProviderNotFound
    }

    suspend fun search(request: QueryMetadata): List<QueryMetadataResult> {
        return if (request.providerId == null) {
            providers
                .filter { it.mediaKinds.contains(request.mediaKind) }
                .map { provider -> provider.search(request) }
        } else {
            providers.find { it.id == request.providerId }
                ?.search(request)
                .run(::listOfNotNull)
        }
    }

    suspend fun importMetadata(request: ImportMetadata): List<ImportMetadataResult> {
        val provider = providers.find { provider -> provider.id == request.providerId }
            ?: return emptyList()

        return provider.importMetadata(request)
    }
}
