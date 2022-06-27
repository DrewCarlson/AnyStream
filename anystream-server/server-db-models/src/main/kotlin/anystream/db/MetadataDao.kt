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

import anystream.db.model.MetadataDb
import anystream.models.Genre
import anystream.models.ProductionCompany
import org.jdbi.v3.core.result.LinkedHashMapRowReducer
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMappers
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.statement.UseRowReducer

private const val MEDIA_COLUMNS =
    """            metadata.id metadata_id,
            metadata.gid metadata_gid,
            metadata.rootId metadata_rootId,
            metadata.rootGid metadata_rootGid,
            metadata.parentId metadata_parentId,
            metadata.parentGid metadata_parentGid,
            metadata.parentIndex metadata_parentIndex,
            metadata.title metadata_title,
            metadata.overview metadata_overview,
            metadata.tmdbId metadata_tmdbId,
            metadata.imdbId metadata_imdbId,
            metadata.runtime metadata_runtime,
            metadata.'index' metadata_index,
            metadata.contentRating metadata_contentRating,
            metadata.posterPath metadata_posterPath,
            metadata.backdropPath metadata_backdropPath,
            metadata.firstAvailableAt metadata_firstAvailableAt,
            metadata.createdAt metadata_createdAt,
            metadata.updatedAt metadata_updatedAt,
            metadata.addedByUserId metadata_addedByUserId,
            metadata.mediaKind metadata_mediaKind,
            metadata.mediaType metadata_mediaType,
            metadata.tmdbRating metadata_tmdbRating"""

private const val GENRE_COLUMNS = """
            g.id g_id, g.name g_name, g.tmdbId g_tmdbId"""

private const val COMPANIES_COLUMNS = """
            c.id c_id, c.name c_name, c.tmdbId c_tmdbId"""

private const val JOIN_GENRES = """
            LEFT JOIN metadataGenres mg on mg.metadataId = metadata.id
            LEFT JOIN tags g on g.id = mg.genreId"""

private const val JOIN_COMPANIES = """
            LEFT JOIN metadataCompanies mc on mc.metadataId = metadata.id
            LEFT JOIN tags c on c.id = mc.companyId"""

@RegisterKotlinMappers(
    RegisterKotlinMapper(MetadataDb::class, prefix = "metadata"),
    RegisterKotlinMapper(Genre::class, prefix = "g"),
    RegisterKotlinMapper(ProductionCompany::class, prefix = "c"),
)
interface MetadataDao {

    @SqlQuery("SELECT * FROM metadata")
    fun all(): List<MetadataDb>

    @SqlQuery("SELECT * FROM metadata WHERE id IN (<ids>)")
    fun findByIds(@BindList("ids") ids: List<Int>): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.id = ?
        """
    )
    fun findById(metadataId: Int): MetadataDb?

    @SqlQuery("SELECT * FROM metadata WHERE parentGid = ?")
    fun findAllByParentGid(parentGid: String): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.parentGid = ? AND metadata.mediaType = ?
        """
    )
    fun findAllByParentGidAndType(parentGid: String, type: MetadataDb.Type): List<MetadataDb>

    @SqlQuery("SELECT * FROM metadata WHERE rootGid = ?")
    fun findAllByRootGid(rootGid: String): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.rootGid = ? AND metadata.mediaType = ?
        """
    )
    fun findAllByRootGidAndType(rootGid: String, type: MetadataDb.Type): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.rootGid = ? AND metadata.parentIndex = ? AND metadata.mediaType = ?
        """
    )
    fun findAllByRootGidAndParentIndexAndType(rootGid: String, parentIndex: Int, type: MetadataDb.Type): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.gid = ? AND metadata.mediaType = ?
        """
    )
    fun findByGidAndType(gid: String, type: MetadataDb.Type): MetadataDb?

    @SqlQuery("SELECT * FROM metadata WHERE gid = ? AND mediaType = ?")
    fun findAllByGidAndType(gid: String, type: MetadataDb.Type): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.gid IN (<gids>) AND metadata.mediaType = :type
        """
    )
    fun findAllByGidsAndType(@BindList("gids") gids: List<String>, type: MetadataDb.Type): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.gid = ?
        """
    )
    fun findByGid(gid: String): MetadataDb?

    @SqlQuery("SELECT mediaType FROM metadata WHERE metadata.gid = ?")
    fun findTypeByGid(gid: String): MetadataDb.Type?

    @SqlQuery("SELECT * FROM metadata WHERE metadata.mediaType = ?")
    fun findAllByType(type: MetadataDb.Type): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM
            (SELECT * FROM metadata WHERE mediaType = ? ORDER BY createdAt DESC LIMIT ?) metadata
            $JOIN_GENRES $JOIN_COMPANIES
        """
    )
    fun findByType(type: MetadataDb.Type, limit: Int): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.mediaType = ? ORDER BY metadata.title
        """
    )
    fun findAllByTypeSortedByTitle(type: MetadataDb.Type): List<MetadataDb>

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.tmdbId = ? AND metadata.mediaType = ?
        """
    )
    fun findByTmdbIdAndType(tmdbId: Int, type: MetadataDb.Type): MetadataDb?

    @UseRowReducer(MediaReducer::class)
    @SqlQuery(
        """
            SELECT $MEDIA_COLUMNS, $GENRE_COLUMNS, $COMPANIES_COLUMNS FROM metadata $JOIN_GENRES $JOIN_COMPANIES
            WHERE metadata.tmdbId IN (<ids>) AND metadata.mediaType = :type
        """
    )
    fun findAllByTmdbIdsAndType(@BindList("ids") tmdbId: List<Int>, type: MetadataDb.Type): List<MetadataDb>

    @SqlQuery("SELECT * FROM metadata WHERE lower(title) LIKE lower(?) AND mediaType = ? LIMIT ?")
    fun searchByTitleAndType(title: String, type: MetadataDb.Type, limit: Int): List<MetadataDb>

    @SqlQuery("SELECT COUNT(id) FROM metadata")
    fun count(): Long

    @SqlQuery("SELECT COUNT(id) FROM metadata WHERE parentGid = ? AND 'index' > 0")
    fun countSeasonsForTvShow(showId: String): Int

    @SqlUpdate
    @GetGeneratedKeys("id")
    @UseClasspathSqlLocator
    fun insertMetadata(metadata: MetadataDb): Int

    @SqlBatch
    @UseClasspathSqlLocator
    fun insertMetadata(metadata: List<MetadataDb>)

    @SqlUpdate("DELETE FROM metadata WHERE id = ?")
    fun deleteById(metadataId: Int)

    @SqlUpdate("DELETE FROM metadata WHERE gid = ?")
    fun deleteByGid(metadataId: String)

    @SqlUpdate("DELETE FROM metadata WHERE rootGid = ?")
    fun deleteByRootGid(rootGid: String)

    class MediaReducer : LinkedHashMapRowReducer<Int, MetadataDb> {
        override fun accumulate(container: MutableMap<Int, MetadataDb>, rowView: RowView) {
            val rowId = rowView.getColumn("metadata_id", Integer::class.javaObjectType).toInt()
            var metadataDb = container.computeIfAbsent(rowId) { rowView.getRow(MetadataDb::class.java) }
            rowView.getColumn("g_id", Integer::class.javaObjectType)?.let { joinId ->
                if (metadataDb.genres.none { it.id == joinId.toInt() }) {
                    val genre = rowView.getRow(Genre::class.java)
                    metadataDb = metadataDb.copy(genres = metadataDb.genres + genre)
                }
            }
            rowView.getColumn("c_id", Integer::class.javaObjectType)?.let { joinId ->
                if (metadataDb.companies.none { it.id == joinId.toInt() }) {
                    val company = rowView.getRow(ProductionCompany::class.java)
                    metadataDb = metadataDb.copy(companies = metadataDb.companies + company)
                }
            }
            container[rowId] = metadataDb
        }
    }
}
