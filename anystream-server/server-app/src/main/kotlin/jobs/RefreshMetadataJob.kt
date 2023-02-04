package anystream.jobs

import anystream.db.MediaLinkDao
import anystream.media.LibraryManager
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
            execute {
                val userId = props[USER_ID]
                val mediaLinkId = props[MEDIA_LINK_ID]
                val mediaLink = try {
                    checkNotNull(mediaLinkDao.findByGid(mediaLinkId))
                } catch (e: Throwable) {
                    logger.error("Failed to find mediaLink with id '$mediaLinkId'", e)
                    return@execute
                }

                when (val scanResult = libraryManager.scanForMedia(userId, mediaLink)) {
                    is MediaScanResult.Success -> {
                        logger.debug("Scanning files: " + scanResult.allValidGids.joinToString())
                        scanResult.allValidGids.forEach { addedMediaLink ->
                            libraryManager.refreshMetadata(userId, addedMediaLink)
                        }
                    }
                    else -> {
                        logger.error("Failed to scan media: $scanResult")
                    }
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
