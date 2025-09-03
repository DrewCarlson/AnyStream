/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.client.api

import anystream.client.AnyStreamClientException
import anystream.models.Directory
import anystream.models.Library
import anystream.models.MediaLink
import anystream.models.api.AddLibraryFolderRequest
import anystream.models.api.AddLibraryFolderResponse
import anystream.models.api.CurrentlyWatching
import anystream.models.api.HomeResponse
import anystream.models.api.LibraryFolderList
import anystream.models.api.ListFilesResponse
import anystream.models.api.MediaAnalyzerResult
import anystream.models.api.MediaLinkMatchResult
import anystream.models.api.MediaLinkResponse
import anystream.models.api.MediaLookupResponse
import anystream.models.api.MediaScanRequest
import anystream.models.api.MediaScanResult
import anystream.models.api.MoviesResponse
import anystream.models.api.Popular
import anystream.models.api.SearchResponse
import anystream.models.api.TvShowsResponse
import anystream.torrent.search.TorrentDescription2
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LibraryApiClient(
    private val core: AnyStreamApiCore,
) {
    suspend fun getHomeData(): HomeResponse = core.http.get("/api/home").bodyOrThrow()

    suspend fun getHomeWatching(): CurrentlyWatching =
        core.http.get("/api/home/watching").bodyOrThrow()

    suspend fun getHomePopular(): Popular = core.http.get("/api/home/popular").bodyOrThrow()

    suspend fun getMovies(): MoviesResponse =
        core.http.get("/api/movies").bodyOrThrow()

    suspend fun getMovies(offset: Int, limit: Int = 30): MoviesResponse =
        core.http.get("/api/movies") {
            parameter("offset", offset)
            parameter("limit", limit)
        }.bodyOrThrow()

    suspend fun getTvShows(page: Int = 1): TvShowsResponse =
        core.http.get("/api/tv") { pageParam(page) }.bodyOrThrow()

    suspend fun getLibraries(): List<Library> {
        return core.http.get("/api/library").bodyOrThrow()
    }

    suspend fun addLibraryFolder(libraryId: String, path: String): AddLibraryFolderResponse {
        return try {
            core.http.put("/api/library/${libraryId}") {
                contentType(ContentType.Application.Json)
                setBody(AddLibraryFolderRequest(path))
            }.bodyOrThrow()
        } catch (e: AnyStreamClientException) {
            e.printStackTrace()
            AddLibraryFolderResponse.RequestError(e.stackTraceToString())
        }
    }

    suspend fun removeMediaLink(mediaLinkId: String): Boolean {
        return try {
            core.http.delete("/api/medialink/$mediaLinkId").orThrow()
            true
        } catch (e: AnyStreamClientException) {
            if (e.response?.status == NotFound) false else throw e
        }
    }

    suspend fun getDirectories(libraryId: String): List<Directory> {
        return try {
            core.http.get("/api/library/$libraryId/directories").bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun removeDirectory(directoryId: String): Boolean {
        return try {
            core.http.delete("/api/library/directory/$directoryId").bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun scanDirectory(directoryId: String, refreshMetadata: Boolean = false) {
        try {
            core.http.get("/api/library/directory/$directoryId/scan") {
                parameter("refreshMetadata", refreshMetadata)
            }.bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun scanLibrary(libraryId: String) {
        try {
            core.http.get("/api/library/$libraryId/scan").orThrow()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun getLibraryFolderList(): LibraryFolderList {
        return try {
            core.http.get("/api/medialink/libraries").bodyOrThrow()
        } catch (e: Exception) {
            e.printStackTrace()
            LibraryFolderList(emptyList())
        }
    }

    suspend fun unmappedMedia(mediaScanRequest: MediaScanRequest): List<String> {
        return core.http.post("/api/metadata/libraries/unmapped") {
            contentType(ContentType.Application.Json)
            setBody(mediaScanRequest)
        }.bodyOrThrow()
    }

    suspend fun refreshMetadata(metadataId: String): MediaLookupResponse {
        return core.http.get("/api/media/$metadataId/refresh-metadata").bodyOrThrow()
    }

    suspend fun analyzeMediaLink(mediaLinkId: String): List<MediaAnalyzerResult> {
        return core.http.get("/api/medialink/$mediaLinkId/analyze") {
            parameter("waitForResult", true)
        }.bodyOrThrow()
    }

    suspend fun analyzeMediaLinksAsync(mediaLinkId: String) {
        core.http.get("/api/medialink/$mediaLinkId/analyze") {
            parameter("waitForResult", false)
        }.bodyOrThrow<String>()
    }

    suspend fun lookupMedia(mediaId: String): MediaLookupResponse =
        core.http.get("/api/media/$mediaId").bodyOrThrow()

    suspend fun getTmdbSources(tmdbId: Int): List<TorrentDescription2> =
        core.http.get("/api/media/tmdb/$tmdbId/sources").bodyOrThrow()

    suspend fun search(query: String, limit: Int? = null): SearchResponse {
        return core.http.get("/api/search") {
            parameter("query", query)
            parameter("limit", limit)
        }.bodyOrThrow()
    }

    suspend fun listFiles(path: String? = null, showFiles: Boolean = false): ListFilesResponse? {
        val response = core.http.get("/api/medialink/libraries/list-files") {
            parameter("showFiles", showFiles)
            if (!path.isNullOrBlank()) {
                parameter("root", path)
            }
        }

        if (response.status == NotFound) return null

        return response.bodyOrThrow()
    }

    suspend fun scanMediaLink(mediaLinkId: String): MediaScanResult? {
        val response = core.http.get("/api/medialink/$mediaLinkId/scan")
        return response.bodyOrThrow()
    }

    suspend fun matchesFor(mediaLinkId: String): List<MediaLinkMatchResult> {
        return core.http.get("/api/medialink/$mediaLinkId/matches").bodyOrThrow()
    }

    suspend fun matchFor(mediaLinkId: String, remoteId: String) {
        val body = buildJsonObject {
            put("remoteId", remoteId)
        }
        core.http.put("/api/medialink/$mediaLinkId/matches") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun getMediaLinks(parentId: String? = null): List<MediaLink> {
        return core.http.get("/api/medialink") {
            parameter("parent", parentId)
        }.bodyOrThrow()
    }

    suspend fun findMediaLink(
        mediaLinkId: String,
        includeMetadata: Boolean = true,
    ): MediaLinkResponse {
        val response = core.http.get("/api/medialink/$mediaLinkId") {
            parameter("includeMetadata", includeMetadata)
        }
        return response.bodyOrThrow()
    }


    suspend fun generatePreview(mediaLinkId: String): Boolean {
        return core.http.get("/api/medialink/$mediaLinkId/generate-preview").status == OK
    }

}