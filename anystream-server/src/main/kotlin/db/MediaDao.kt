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

import anystream.db.model.MediaDb
import anystream.models.Genre
import anystream.models.ProductionCompany
import org.jdbi.v3.core.result.LinkedHashMapRowReducer
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMappers
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.statement.UseRowReducer

private const val MEDIA_COLUMNS =
    """            media.id media_id,
            media.gid media_gid,
            media.rootId media_rootId,
            media.rootGid media_rootGid,
            media.parentId media_parentId,
            media.parentGid media_parentGid,
            media.parentIndex media_parentIndex,
            media.title media_title,
            media.overview media_overview,
            media.tmdbId media_tmdbId,
            media.imdbId media_imdbId,
            media.runtime media_runtime,
            media.'index' media_index,
            media.contentRating media_contentRating,
            media.posterPath media_posterPath,
            media.backdropPath media_backdropPath,
            media.firstAvailableAt media_firstAvailableAt,
            media.createdAt media_createdAt,
            media.updatedAt media_updatedAt,
            media.addedByUserId media_addedByUserId,
            media.mediaKind media_mediaKind,
            media.mediaType media_mediaType,
            media.tmdbRating media_tmdbRating"""

private const val GENRE_COLUMNS = """
            g.id g_id, g.name g_name, g.tmdbId g_tmdbId"""

private const val COMPANIES_COLUMNS = """
            c.id c_id, c.name c_name, c.tmdbId c_tmdbId"""

private const val JOIN_GENRES = """
            LEFT JOIN mediaGenres mg on mg.mediaId = media.id
            LEFT JOIN tags g on g.id = mg.genreId"""

private const val JOIN_COMPANIES = """
            LEFT JOIN mediaCompanies mc on mc.mediaId = media.id
            LEFT JOIN tags c on c.id = mc.companyId"""

@RegisterKotlinMappers(
    RegisterKotlinMapper(MediaDb::class, prefix = "media"),
    RegisterKotlinMapper(Genre::class, prefix = "g"),
    RegisterKotlinMapper(ProductionCompany::class, prefix = "c"),
)
interface MediaDao {

    @SqlUpdate
    @UseClasspathSqlLocator
    fun createTable()

    @SqlQuery("SELECT * FROM media")
    fun all(): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE id IN (<ids>)")
    fun findByIds(@BindList("ids") ids: List<Int>): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.id = ?
        """
    )
    fun findById(mediaId: Int): MediaDb?

    @SqlQuery("SELECT * FROM media WHERE parentGid = ?")
    fun findAllByParentGid(parentGid: String): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.parentGid = ? AND media.mediaType = ?
        """
    )
    fun findAllByParentGidAndType(parentGid: String, type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE rootGid = ?")
    fun findAllByRootGid(rootGid: String): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.rootGid = ? AND media.mediaType = ?
        """
    )
    fun findAllByRootGidAndType(rootGid: String, type: MediaDb.Type): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.rootGid = ? AND media.parentIndex = ? AND media.mediaType = ?
        """
    )
    fun findAllByRootGidAndParentIndexAndType(rootGid: String, parentIndex: Int, type: MediaDb.Type): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.gid = ? AND media.mediaType = ?
        """
    )
    fun findByGidAndType(gid: String, type: MediaDb.Type): MediaDb?

    @SqlQuery("SELECT * FROM media WHERE gid = ? AND mediaType = ?")
    fun findAllByGidAndType(gid: String, type: MediaDb.Type): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.gid IN (<gids>) AND media.mediaType = :type
        """
    )
    fun findAllByGidsAndType(@BindList("gids") gids: List<String>, type: MediaDb.Type): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.gid = ?
        """
    )
    fun findByGid(gid: String): MediaDb?

    @SqlQuery("SELECT mediaType FROM media WHERE media.gid = ?")
    fun findTypeByGid(gid: String): MediaDb.Type?

    @SqlQuery("SELECT * FROM media WHERE media.mediaType = ?")
    fun findAllByType(type: MediaDb.Type): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM
            (SELECT * FROM media WHERE mediaType = ? ORDER BY createdAt DESC LIMIT ?) media
            $JOIN_GENRES $JOIN_COMPANIES
        """
    )
    fun findByType(type: MediaDb.Type, limit: Int): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.mediaType = ? ORDER BY media.title
        """
    )
    fun findAllByTypeSortedByTitle(type: MediaDb.Type): List<MediaDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.tmdbId = ? AND media.mediaType = ?
        """
    )
    fun findByTmdbIdAndType(tmdbId: Int, type: MediaDb.Type): MediaDb?

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM media $JOIN_GENRES $JOIN_COMPANIES
            WHERE media.tmdbId IN (<ids>) AND media.mediaType = :type
        """
    )
    fun findAllByTmdbIdsAndType(@BindList("ids") tmdbId: List<Int>, type: MediaDb.Type): List<MediaDb>

    @SqlQuery("SELECT * FROM media WHERE lower(title) LIKE lower(?) AND mediaType = ? LIMIT ?")
    fun searchByTitleAndType(title: String, type: MediaDb.Type, limit: Int): List<MediaDb>

    @SqlQuery("SELECT COUNT(id) FROM media")
    fun count(): Long

    @SqlQuery("SELECT COUNT(id) FROM media WHERE parentGid = ? AND 'index' > 0")
    fun countSeasonsForTvShow(showId: String): Int

    @SqlUpdate
    @GetGeneratedKeys("id")
    @UseClasspathSqlLocator
    fun insertMedia(media: MediaDb): Int

    @SqlUpdate("DELETE FROM media WHERE id = ?")
    fun deleteById(mediaId: Int)

    @SqlUpdate("DELETE FROM media WHERE gid = ?")
    fun deleteByGid(mediaId: String)

    @SqlUpdate("DELETE FROM media WHERE rootGid = ?")
    fun deleteByRootGid(rootMediaId: String)

    class MediaReducer : LinkedHashMapRowReducer<Int, MediaDb> {
        override fun accumulate(container: MutableMap<Int, MediaDb>, rowView: RowView) {
            val rowId = rowView.getColumn("media_id", Integer::class.javaObjectType) as Int
            var mediaDb = container.computeIfAbsent(rowId) { rowView.getRow(MediaDb::class.java) }
            rowView.getColumn("g_id", Integer::class.javaObjectType)?.let { joinId ->
                if (mediaDb.genres.none { it.id == joinId.toInt() }) {
                    val genre = rowView.getRow(Genre::class.java)
                    mediaDb = mediaDb.copy(genres = mediaDb.genres + genre)
                }
            }
            rowView.getColumn("c_id", Integer::class.javaObjectType)?.let { joinId ->
                if (mediaDb.companies.none { it.id == joinId.toInt() }) {
                    val company = rowView.getRow(ProductionCompany::class.java)
                    mediaDb = mediaDb.copy(companies = mediaDb.companies + company)
                }
            }
            container[rowId] = mediaDb
        }
    }
}
