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
package anystream.metadata

import anystream.models.api.*
import org.slf4j.Logger
import org.slf4j.MarkerFactory

class MetadataManager(
    private val providers: List<MetadataProvider>,
    private val logger: Logger,
) {
    private val classMarker = MarkerFactory.getMarker(this::class.simpleName)

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