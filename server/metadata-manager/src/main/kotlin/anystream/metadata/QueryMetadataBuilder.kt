/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
import anystream.models.api.QueryMetadata
import anystream.models.api.QueryMetadata.Extras

class QueryMetadataBuilder(
    private val mediaKind: MediaKind
) {
    var providerId: String? = null
    var query: String? = null
    var metadataId: String? = null
    var year: Int? = null
    var extras: Extras? = null

    fun build(): QueryMetadata {
        return QueryMetadata(
            providerId = providerId,
            mediaKind = mediaKind,
            query = query,
            metadataId = metadataId,
            year = year,
            extras = extras
        )
    }
}