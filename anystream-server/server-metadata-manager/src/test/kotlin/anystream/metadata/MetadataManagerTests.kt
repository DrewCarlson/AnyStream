/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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

import anystream.data.MediaDbQueries
import anystream.db.*
import anystream.db.mappers.registerMappers
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.MediaKind
import anystream.models.api.*
import app.moviebase.tmdb.Tmdb3
import kotlinx.coroutines.runBlocking
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlite3.SQLitePlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.attach
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.test.*

class MetadataManagerTests {

    lateinit var handle: Handle
    lateinit var manager: MetadataManager

    @BeforeTest
    fun before() {
        runMigrations("jdbc:sqlite:test.db")
        val jdbi = Jdbi.create("jdbc:sqlite:test.db").apply {
            installPlugin(SQLitePlugin())
            installPlugin(SqlObjectPlugin())
            installPlugin(KotlinSqlObjectPlugin())
            installPlugin(KotlinPlugin())
            registerMappers()
        }
        handle = jdbi.open()
        val mediaDao = handle.attach<MediaDao>()
        val tagsDao = handle.attach<TagsDao>()
        val playbackStatesDao = handle.attach<PlaybackStatesDao>()
        val mediaReferencesDao = handle.attach<MediaReferencesDao>()
        val searchableContentDao = handle.attach<SearchableContentDao>().apply { createTable() }

        val queries = MediaDbQueries(searchableContentDao, mediaDao, tagsDao, mediaReferencesDao, playbackStatesDao)
        val provider = TmdbMetadataProvider(Tmdb3("c1e9e8ade306dd9cbc5e17b05ed4badd"), queries)
        manager = MetadataManager(listOf(provider), LoggerFactory.getLogger("test"))
    }

    @AfterTest
    fun after() {
        handle.close()
        File("test.db").delete()
    }

    @Test
    fun testMovieImportTmdb() = runBlocking {
        val request = ImportMetadata(
            contentIds = listOf("77"),
            mediaKind = MediaKind.MOVIE,
            year = 2000,
            providerId = "tmdb",
            refresh = false,
        )
        val importResults = manager.importMetadata(request)
        val importResult = assertIs<ImportMetadataResult.Success>(importResults.first())
        val metadataMatch = assertIs<MetadataMatch.MovieMatch>(importResult.match)

        assertEquals("77", metadataMatch.contentId)
        assertEquals("tmdb:movie:77", metadataMatch.remoteId)
        assertEquals("Memento", metadataMatch.movie.title)
        assertTrue(metadataMatch.exists)

        val mediaDao = handle.attach<MediaDao>()
        val dbResult = assertNotNull(mediaDao.findByGid(metadataMatch.movie.id))
        assertEquals(metadataMatch.movie.title, dbResult.title)
    }

    @Test
    fun testTvShowImportTmdb() = runBlocking {
        val request = ImportMetadata(
            contentIds = listOf("63333"),
            mediaKind = MediaKind.TV,
            year = 2015,
            providerId = "tmdb",
            refresh = false,
        )
        val importResults = manager.importMetadata(request)
        val importResult = assertIs<ImportMetadataResult.Success>(importResults.first())
        val metadataMatch = assertIs<MetadataMatch.TvShowMatch>(importResult.match)

        assertEquals("63333", metadataMatch.contentId)
        assertEquals("tmdb:tv:63333", metadataMatch.remoteId)
        assertEquals("The Last Kingdom", metadataMatch.tvShow.name)
        assertTrue(metadataMatch.exists)

        val mediaDao = handle.attach<MediaDao>()
        val dbResult = assertNotNull(mediaDao.findByGid(metadataMatch.tvShow.id))
        assertEquals(metadataMatch.tvShow.name, dbResult.title)
    }

    @Test
    fun testQueryTmdbMovie() = runBlocking {
        val query = QueryMetadata(
            providerId = "tmdb",
            mediaKind = MediaKind.MOVIE,
            query = "the avengers",
            year = 2012,
        )
        val queryResult = manager.search(query)
        val searchResult = queryResult.first()
        assertIs<QueryMetadataResult.Success>(searchResult)
        val result = searchResult.results.first()
        assertIs<MetadataMatch.MovieMatch>(result)

        assertEquals("The Avengers", result.movie.title)
    }

    @Test
    fun testQueryTmdbTvShow() = runBlocking {
        val query = QueryMetadata(
            providerId = "tmdb",
            mediaKind = MediaKind.TV,
            query = "last kingdom",
            year = 2015,
        )
        val queryResult = manager.search(query)
        val searchResult = queryResult.first()
        assertIs<QueryMetadataResult.Success>(searchResult)
        val result = searchResult.results.first()
        assertIs<MetadataMatch.TvShowMatch>(result)

        assertEquals("The Last Kingdom", result.tvShow.name)
    }

    @Test
    fun testFindTmdbTvShowByRemoteId() = runBlocking {
        val remoteId = "tmdb:tv:456"
        val queryResult = manager.findByRemoteId(remoteId)

        assertIs<QueryMetadataResult.Success>(queryResult)
        assertEquals("tmdb", queryResult.providerId)
        assertNull(queryResult.extras)

        val result = queryResult.results.singleOrNull()
        assertNotNull(result)
        assertIs<MetadataMatch.TvShowMatch>(result)

        assertEquals(remoteId, result.remoteId)
        assertEquals(remoteId, result.tvShow.id)
        assertEquals("456", result.contentId)

        assertEquals("The Simpsons", result.tvShow.name)
    }

    @Test
    fun testFindTmdbMovieByRemoteId() = runBlocking {
        val remoteId = "tmdb:movie:77"
        val queryResult = manager.findByRemoteId(remoteId)

        assertIs<QueryMetadataResult.Success>(queryResult)
        assertEquals("tmdb", queryResult.providerId)
        assertNull(queryResult.extras)

        val result = queryResult.results.singleOrNull()
        assertNotNull(result)
        assertIs<MetadataMatch.MovieMatch>(result)

        assertEquals(remoteId, result.remoteId)
        assertEquals(remoteId, result.movie.id)
        assertEquals("77", result.contentId)

        assertEquals("Memento", result.movie.title)
    }
}
