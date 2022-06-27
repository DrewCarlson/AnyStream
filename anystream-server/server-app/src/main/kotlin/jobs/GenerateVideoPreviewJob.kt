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
package anystream.jobs

import anystream.data.MetadataDbQueries
import anystream.models.DownloadMediaLink
import anystream.models.LocalMediaLink
import anystream.service.stream.executeAwait
import com.github.kokorin.jaffree.JaffreeException
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffmpeg.UrlInput
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import kjob.core.Job
import kjob.core.KJob
import org.slf4j.Logger
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

private const val PREVIEW_IMAGE_WIDTH = "240" // Image width, height will be scaled
private const val PREVIEW_IMAGE_QUALITY = "5" // Possible values: 2-31
private const val PREVIEW_IMAGE_INTERVAL = "5" // Seconds between each image
private const val PREVIEW_IMAGE_FILE_NAME = "preview%d.jpg"
private const val FFMPEG_FILTER = "fps=fps=1/$PREVIEW_IMAGE_INTERVAL,scale=$PREVIEW_IMAGE_WIDTH:-1"

object GenerateVideoPreviewJob : Job("generate-video-preview") {
    private val MEDIA_REF_ID = string("mediaLinkId")

    fun register(
        kjob: KJob,
        ffmpeg: () -> FFmpeg,
        rootStorageDir: String,
        metadataDbQueries: MetadataDbQueries,
    ) {
        val previewStorage = "${rootStorageDir}${File.separator}previews"
        kjob.register(GenerateVideoPreviewJob) {
            execute {
                val mediaLinkId = props[MEDIA_REF_ID]
                when (val mediaLink = metadataDbQueries.findMediaLinkByGid(mediaLinkId)) {
                    null -> logger.error("No mediaLink found for id: $mediaLinkId")
                    is DownloadMediaLink ->
                        logger.error("Generating video previews for DownloadMediaLink is unsupported: $mediaLinkId")
                    is LocalMediaLink -> generatePreview(logger, ffmpeg(), previewStorage, mediaLink)
                }
            }
        }
    }

    suspend fun schedule(kjob: KJob, mediaLinkId: String) {
        kjob.schedule(GenerateVideoPreviewJob) {
            jobId = "$name-$mediaLinkId"
            props[MEDIA_REF_ID] = mediaLinkId
        }
    }

    private suspend fun generatePreview(
        logger: Logger,
        ffmpeg: FFmpeg,
        rootStorageDir: String,
        mediaLink: LocalMediaLink
    ) {
        val outputPath = Path(rootStorageDir, mediaLink.gid).createDirectories().absolutePathString()
        val input = UrlInput.fromUrl(mediaLink.filePath)
        val output = UrlOutput.toPath(Path(outputPath, PREVIEW_IMAGE_FILE_NAME))
        logger.debug("Starting video preview generation in: $outputPath")
        try {
            ffmpeg
                .addInput(input)
                .addArguments("-vf", FFMPEG_FILTER)
                .addArguments("-v:q", PREVIEW_IMAGE_QUALITY)
                .addOutput(output)
                .executeAwait()
            logger.debug("Video preview generation completed normally.")
        } catch (e: JaffreeException) {
            logger.error("Failed to generate video preview", e)
            return
        }
    }
}
