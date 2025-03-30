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

import anystream.data.MetadataDbQueries
import anystream.db.*
import anystream.metadata.providers.TmdbMetadataProvider
import anystream.models.MediaKind
import anystream.models.api.*
import app.moviebase.tmdb.Tmdb3
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.plugins.logging.*
import kotlin.test.*

class MetadataManagerTests : FunSpec({
    val db by bindTestDatabase()
    val metadataDao by bindForTest({ MetadataDao(db) })
    val queries by bindForTest({
        val tagsDao = TagsDao(db)
        val playbackStatesDao = PlaybackStatesDao(db)
        val mediaLinkDao = MediaLinkDao(db)

        MetadataDbQueries(db, metadataDao, tagsDao, mediaLinkDao, playbackStatesDao)
    })
    val tmdb by bindForTest({
        Tmdb3 {
            tmdbApiKey = "c1e9e8ade306dd9cbc5e17b05ed4badd"
            this.httpClient {
                install(Logging) {
                    level = LogLevel.ALL
                    logger = Logger.SIMPLE
                }
            }
        }
    })

    val manager by bindForTest({
        val provider = TmdbMetadataProvider(tmdb, queries)
        MetadataManager(listOf(provider))
    })

    test("import tmdb movie") {
        val request = ImportMetadata(
            metadataIds = listOf("77"),
            mediaKind = MediaKind.MOVIE,
            year = 2000,
            providerId = "tmdb",
            refresh = false,
        )
        val importResults = manager.importMetadata(request)
        val importResult = assertIs<ImportMetadataResult.Success>(importResults.first())
        val metadataMatch = assertIs<MetadataMatch.MovieMatch>(importResult.match)

        assertEquals("77", metadataMatch.remoteMetadataId)
        assertEquals("tmdb:movie:77", metadataMatch.remoteId)
        assertEquals("Memento", metadataMatch.movie.title)
        assertTrue(metadataMatch.exists)

        val dbResult = assertNotNull(metadataDao.find(metadataMatch.movie.id))
        assertEquals(metadataMatch.movie.title, dbResult.title)
    }

    test("import tmdb tv show") {
        val request = ImportMetadata(
            metadataIds = listOf("63333"),
            mediaKind = MediaKind.TV,
            year = 2015,
            providerId = "tmdb",
            refresh = false,
        )
        val importResults = manager.importMetadata(request)
        val importResult = assertIs<ImportMetadataResult.Success>(importResults.first())
        val metadataMatch = assertIs<MetadataMatch.TvShowMatch>(importResult.match)

        assertEquals("63333", metadataMatch.remoteMetadataId)
        assertEquals("tmdb:tv:63333", metadataMatch.remoteId)
        assertEquals("The Last Kingdom", metadataMatch.tvShow.name)
        assertTrue(metadataMatch.exists)

        val dbResult = assertNotNull(metadataDao.find(metadataMatch.tvShow.id))
        assertEquals(metadataMatch.tvShow.name, dbResult.title)
    }

    test("query tmdb movie") {
        val queryResult = manager.search(MediaKind.MOVIE) {
            providerId = "tmdb"
            query = "the avengers"
            year = 2012
        }
        val searchResult = queryResult.first()
        assertIs<QueryMetadataResult.Success>(searchResult)
        val result = searchResult.results.first()
        assertIs<MetadataMatch.MovieMatch>(result)

        assertEquals("The Avengers", result.movie.title)
    }

    test("query tmdb tv show") {
        val queryResult = manager.search(MediaKind.TV) {
            providerId = "tmdb"
            query = "last kingdom"
            year = 2015
        }
        val searchResult = queryResult.first()
        assertIs<QueryMetadataResult.Success>(searchResult)
        val result = searchResult.results.first()
        assertIs<MetadataMatch.TvShowMatch>(result)

        assertEquals("The Last Kingdom", result.tvShow.name)
    }

    test("find tmdb tv show by remote id") {
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
        assertEquals("456", result.remoteMetadataId)

        assertEquals("The Simpsons", result.tvShow.name)
    }

    test("find tmdb movie by remote id") {
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
        assertEquals("77", result.remoteMetadataId)

        assertEquals("Memento", result.movie.title)
    }
})
