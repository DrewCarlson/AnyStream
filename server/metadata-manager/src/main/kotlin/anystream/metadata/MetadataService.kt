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
package anystream.metadata

import anystream.db.MetadataDao
import anystream.models.MediaKind
import anystream.models.api.*
import anystream.util.isRemoteId
import io.ktor.util.encodeBase64
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists

class MetadataService(
    private val providers: List<MetadataProvider>,
    private val metadataDao: MetadataDao,
    private val imageStore: ImageStore,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.debug("Configuring metadata providers {}", providers.map { it.id })
    }

    suspend fun findByRemoteId(remoteId: String): QueryMetadataResult {
        val (providerId, mediaKindString, rawId) = remoteId.split(':')
        val mediaKind = MediaKind.valueOf(mediaKindString.uppercase())
        var parsedId = rawId
        val extras = when (mediaKind) {
            MediaKind.TV -> {
                if (rawId.contains('-')) {
                    val parts = rawId.split('-')
                    parsedId = parts[0]
                    QueryMetadata.Extras.TvShowExtras(
                        seasonNumber = parts.getOrNull(1)?.toIntOrNull(),
                        episodeNumber = parts.getOrNull(2)?.toIntOrNull(),
                    )
                } else {
                    null
                }
            }

            else -> null
        }

        return search(mediaKind = mediaKind) {
            this.providerId = providerId
            this.metadataId = parsedId
            this.extras = extras
        }.firstOrNull() ?: QueryMetadataResult.ErrorProviderNotFound
    }

    suspend fun search(
        mediaKind: MediaKind,
        block: QueryMetadataBuilder.() -> Unit
    ): List<QueryMetadataResult> {
        val request = QueryMetadataBuilder(mediaKind).apply(block).build()
        logger.debug("Performing metadata search for {}", request)
        return if (request.providerId == null) {
            providers
                .filter { it.mediaKinds.contains(request.mediaKind) }
                .map { provider -> provider.search(request) }
        } else {
            providers.find { it.id == request.providerId }
                ?.search(request)
                .run(::listOfNotNull)
        }.also { selectedProviders ->
            if (selectedProviders.isEmpty()) {
                logger.warn("No providers available for ${request.providerId}")
            }
        }
    }

    suspend fun importMetadata(request: ImportMetadata): List<ImportMetadataResult> {
        logger.debug("Performing metadata import for {}", request)
        val provider = providers.find { provider -> provider.id == request.providerId } ?: run {
            logger.warn("No providers available for request {}", request)
            return emptyList()
        }

        return provider.importMetadata(request)
    }

    suspend fun getImagePath(metadataId: String, imageType: String): Path? {
        return if (metadataId.isRemoteId) {
            getImagePathForRemoteId(metadataId, imageType)
        } else {
            getImagePathMetadataId(metadataId, imageType)
        }
    }

    private suspend fun getImagePathForRemoteId(imageKey: String, imageType: String): Path? {
        val queryResult = findByRemoteId(imageKey)
        val metadataResult = (queryResult as? QueryMetadataResult.Success)?.results?.firstOrNull()
        val cachePath = imageStore.getImagePath(
            imageType,
            imageKey.encodeBase64(),
            "remote-metadata-cache/${imageKey.substringBeforeLast('-').encodeBase64()}"
        )
        if (cachePath.exists()) {
            return cachePath
        }

        // TODO: Move image url creation into providers
        // TODO: pre-cache images for remote metadata that appears automatically, like home screen popular results
        val imagePaths = when (metadataResult) {
            is MetadataMatch.MovieMatch -> {
                listOf(
                    "poster" to metadataResult.movie.posterPath,
                    "backdrop" to metadataResult.movie.backdropPath,
                )
            }
            is MetadataMatch.TvShowMatch -> {
                val episode = metadataResult.episodes.singleOrNull()
                val season = metadataResult.seasons.singleOrNull()
                val show = metadataResult.tvShow
                when (imageKey.count { it == '-' }) {
                    0 -> {
                        listOf(
                            "poster" to show.posterPath,
                            "backdrop" to show.backdropPath,
                        )
                    }
                    1 -> listOf("poster" to season?.posterPath)
                    else -> listOf("poster" to episode?.stillPath)
                }
            }

            else -> return null
        }

        imagePaths.forEach { (type, path) ->
            val otherCachePath = imageStore.getImagePath(
                type,
                imageKey.encodeBase64(),
                "remote-metadata-cache/${imageKey.substringBeforeLast('-').encodeBase64()}"
            )
            val url = when (type) {
                "poster" -> "https://image.tmdb.org/t/p/w300$path"
                "backdrop" -> "https://image.tmdb.org/t/p/w1280$path"
                else -> return@forEach
            }
            imageStore.downloadInto(otherCachePath, url)
        }

        return cachePath.takeIf { it.exists() }
    }

    private suspend fun getImagePathMetadataId(imageKey: String, imageType: String): Path? {
        val rootId = metadataDao.findRootIdOrSelf(imageKey) ?: return null
        val cacheFile = imageStore.getImagePath(imageType, imageKey, rootId)
        return cacheFile.takeIf { it.exists() }
    }
}
