INSERT INTO playbackStates(id,
                           gid,
                           mediaLinkId,
                           metadataGid,
                           userId,
                           position,
                           runtime,
                           createdAt,
                           updatedAt)
VALUES (NULL,
        :state.gid,
        :state.mediaLinkId,
        :state.metadataGid,
        :state.userId,
        :state.position,
        :state.runtime,
        :createdAt,
        :state.updatedAt)