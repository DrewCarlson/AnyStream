package anystream.metadata

import anystream.models.MediaKind
import anystream.models.api.QueryMetadata
import anystream.models.api.QueryMetadata.Extras

class QueryMetadataBuilder(
    private val mediaKind: MediaKind
) {
    var providerId: String? = null
    var query: String? = null
    var metadataGid: String? = null
    var year: Int? = null
    var extras: Extras? = null

    fun build(): QueryMetadata {
        return QueryMetadata(
            providerId = providerId,
            mediaKind = mediaKind,
            query = query,
            metadataGid = metadataGid,
            year = year,
            extras = extras
        )
    }
}