CREATE VIEW metadataView AS
SELECT metadata.id               metadata_id,
       metadata.gid              metadata_gid,
       metadata.rootId           metadata_rootId,
       metadata.rootGid          metadata_rootGid,
       metadata.parentId         metadata_parentId,
       metadata.parentGid        metadata_parentGid,
       metadata.parentIndex      metadata_parentIndex,
       metadata.title            metadata_title,
       metadata.overview         metadata_overview,
       metadata.tmdbId           metadata_tmdbId,
       metadata.imdbId           metadata_imdbId,
       metadata.runtime          metadata_runtime,
       metadata.'index'          metadata_index,
       metadata.contentRating    metadata_contentRating,
       metadata.posterPath       metadata_posterPath,
       metadata.backdropPath     metadata_backdropPath,
       metadata.firstAvailableAt metadata_firstAvailableAt,
       metadata.createdAt        metadata_createdAt,
       metadata.updatedAt        metadata_updatedAt,
       metadata.addedByUserId    metadata_addedByUserId,
       metadata.mediaKind        metadata_mediaKind,
       metadata.mediaType        metadata_mediaType,
       metadata.tmdbRating       metadata_tmdbRating,
       g.id                      g_id,
       g.name                    g_name,
       g.tmdbId                  g_tmdbId,
       c.id                      c_id,
       c.name                    c_name,
       c.tmdbId                  c_tmdbId
FROM metadata
         LEFT JOIN metadataGenres mg on mg.metadataId = metadata.id
         LEFT JOIN tags g on g.id = mg.genreId
         LEFT JOIN metadataCompanies mc on mc.metadataId = metadata.id
         LEFT JOIN tags c on c.id = mc.companyId;