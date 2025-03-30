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
package anystream.db

import anystream.db.converter.INSTANT_DATATYPE
import anystream.db.tables.records.PlaybackStateRecord
import anystream.db.tables.references.METADATA
import anystream.db.tables.references.PLAYBACK_STATE
import anystream.db.util.*
import anystream.models.PlaybackState
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class PlaybackStatesDao(
    private val db: DSLContext
) {

    /**
     * SELECT ps.*
     * FROM playbackStates ps
     * JOIN (
     *     SELECT m1.id AS metadataId,
     *            CASE
     *                WHEN m1.rootId IS NULL THEN m1.id
     *                ELSE m1.rootId
     *            END AS rootOrSelfGid,
     *            MAX(ps.updatedAt) AS maxUpdatedAt
     *     FROM playbackStates ps
     *     JOIN metadata m1 ON m1.id = ps.metadataId
     *     WHERE ps.userId = ?
     *     GROUP BY rootOrSelfGid
     * ) x ON x.metadataId = ps.metadataId AND x.maxUpdatedAt = ps.updatedAt
     * ORDER BY ps.updatedAt DESC
     * LIMIT ?
     */
    suspend fun findWithUniqueRootByUserId(userId: String, limit: Int): List<PlaybackState> {
        val subquery = DSL.select(
            METADATA.ID.`as`("metadataId"),
            DSL.`when`(METADATA.ROOT_ID.isNull(), METADATA.ID)
                .otherwise(METADATA.ROOT_ID)
                .`as`("rootOrSelfId"),
            DSL.max(PLAYBACK_STATE.UPDATED_AT)
                .`as`("maxUpdatedAt")
        )
            .from(PLAYBACK_STATE)
            .join(METADATA)
            .on(METADATA.ID.eq(PLAYBACK_STATE.METADATA_ID))
            .where(PLAYBACK_STATE.USER_ID.eq(userId))
            .groupBy(DSL.field("rootOrSelfId"))
            .asTable("x")

        return db.select(PLAYBACK_STATE)
            .from(PLAYBACK_STATE)
            .join(subquery)
            .on(
                subquery.field("metadataId", SQLDataType.VARCHAR(24).notNull())!!
                    .eq(PLAYBACK_STATE.METADATA_ID)
                    .and(
                        subquery.field("maxUpdatedAt", INSTANT_DATATYPE)!!
                            .eq(PLAYBACK_STATE.UPDATED_AT)
                    )
            )
            .orderBy(PLAYBACK_STATE.UPDATED_AT.desc())
            .limit(limit)
            .awaitInto()
    }

    suspend fun findByUserIdAndMetadataId(userId: String, metadataId: String): PlaybackState? {
        return db.selectFrom(PLAYBACK_STATE)
            .where(
                PLAYBACK_STATE.USER_ID.eq(userId),
                PLAYBACK_STATE.METADATA_ID.eq(metadataId)
            )
            .awaitFirstOrNullInto()
    }

    suspend fun findByUserIdAndMetadataIds(userId: String, metadataIds: List<String>): List<PlaybackState> {
        return db.select(PLAYBACK_STATE)
            .from(PLAYBACK_STATE)
            .where(
                PLAYBACK_STATE.USER_ID.eq(userId),
                PLAYBACK_STATE.METADATA_ID.`in`(metadataIds)
            )
            .awaitInto()
    }

    suspend fun fetchById(playbackStateId: String): PlaybackState? {
        return db.select(PLAYBACK_STATE)
            .from(PLAYBACK_STATE)
            .where(PLAYBACK_STATE.ID.eq(playbackStateId))
            .awaitFirstOrNullInto()
    }

    suspend fun fetchByIds(ids: List<String>): List<PlaybackState> {
        return db.select(PLAYBACK_STATE)
            .from(PLAYBACK_STATE)
            .where(PLAYBACK_STATE.ID.`in`(ids))
            .awaitInto()
    }

    suspend fun insert(playbackState: PlaybackState): Boolean {
        return db.insertInto(PLAYBACK_STATE)
            .set(PlaybackStateRecord(playbackState))
            .awaitFirstOrNull() == 1
    }
}
