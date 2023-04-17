/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.jobs

import anystream.db.MediaLinkDao
import anystream.media.LibraryManager
import anystream.models.MediaLink
import anystream.models.api.MediaScanResult
import kjob.core.Job
import kjob.core.KJob

object RefreshMetadataJob : Job("refresh-metadata") {

    private val USER_ID = integer("userId")
    private val MEDIA_LINK_ID = string("mediaLinkId")

    fun register(
        kJob: KJob,
        mediaLinkDao: MediaLinkDao,
        libraryManager: LibraryManager,
    ) {
        kJob.register(RefreshMetadataJob) {
            maxRetries = 0
            execute {
                val userId = props[USER_ID]
                val mediaLinkId = props[MEDIA_LINK_ID]
                val mediaLink = try {
                    checkNotNull(mediaLinkDao.findByGid(mediaLinkId))
                } catch (e: Throwable) {
                    logger.error("Failed to find mediaLink with id '$mediaLinkId'", e)
                    return@execute
                }

                when (mediaLink.descriptor) {
                    MediaLink.Descriptor.ROOT_DIRECTORY -> {
                        when (val scanResult = libraryManager.scanForMedia(userId, mediaLink)) {
                            is MediaScanResult.Success -> {
                                logger.debug("Scanning files: {}", scanResult.allValidGids.joinToString())
                                scanResult.allValidGids.forEach { addedMediaLink ->
                                    libraryManager.refreshMetadata(userId, addedMediaLink)
                                }
                            }
                            else -> {
                                logger.error("Failed to scan media: {}", scanResult)
                            }
                        }
                    }
                    MediaLink.Descriptor.MEDIA_DIRECTORY -> TODO()
                    MediaLink.Descriptor.CHILD_DIRECTORY -> TODO()
                    MediaLink.Descriptor.VIDEO -> TODO()
                    else -> error("Refresh Metadata unsupported for MediaLink with descriptor(${mediaLink.descriptor})")
                }
            }
        }
    }

    suspend fun schedule(kjob: KJob, userId: Int, mediaLinkId: String) {
        kjob.schedule(RefreshMetadataJob) {
            jobId = "$name-$mediaLinkId"
            props[USER_ID] = userId
            props[MEDIA_LINK_ID] = mediaLinkId
        }
    }
}
