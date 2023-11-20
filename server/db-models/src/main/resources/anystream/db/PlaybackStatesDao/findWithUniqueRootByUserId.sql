SELECT ps.*
FROM playbackStates ps
JOIN (
    SELECT m1.gid AS metadataGid,
           CASE
               WHEN m1.rootGid IS NULL THEN m1.gid
               ELSE m1.rootGid
           END AS rootOrSelfGid,
           MAX(ps.updatedAt) AS maxUpdatedAt
    FROM playbackStates ps
    JOIN metadata m1 ON m1.gid = ps.metadataGid
    WHERE ps.userId = ?
    GROUP BY rootOrSelfGid
) x ON x.metadataGid = ps.metadataGid AND x.maxUpdatedAt = ps.updatedAt
ORDER BY ps.updatedAt DESC
LIMIT ?