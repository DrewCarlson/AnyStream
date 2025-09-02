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

import anystream.AnyStreamConfig
import anystream.db.MediaLinkDao
import anystream.models.MediaLink
import anystream.models.MediaLinkType
import anystream.service.stream.MediaFileProbe
import com.github.kokorin.jaffree.JaffreeException
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffmpeg.UrlInput
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories


private const val PREVIEW_IMAGE_WIDTH = "240" // Image width, height will be scaled
private const val PREVIEW_IMAGE_QUALITY = "2" // Possible values: 2-31
private const val PREVIEW_IMAGE_INTERVAL = "5" // Seconds between each image
private const val PREVIEW_IMAGE_FILE_NAME = "preview%d.jpg"
private const val FFMPEG_FILTER = "fps=fps=1/$PREVIEW_IMAGE_INTERVAL,scale=$PREVIEW_IMAGE_WIDTH:-1"

class GenerateVideoPreviewJob(
    private val ffmpeg: () -> FFmpeg,
    private val config: AnyStreamConfig,
    private val mediaLinkDao: MediaLinkDao,
    private val mediaFileProbe: MediaFileProbe,
) {
    private val logger = LoggerFactory.getLogger(GenerateVideoPreviewJob::class.java)
    suspend fun execute(mediaLinkId: String) {
        val basePath = config.dataPath
            .resolve("previews")
            .createDirectories()

        val mediaLink = mediaLinkDao.findById(mediaLinkId)
        when (mediaLink?.type) {
            null -> logger.error("No mediaLink found for id: $mediaLinkId")
            MediaLinkType.DOWNLOAD -> logger.error("Generating video previews for DownloadMediaLink is unsupported: $mediaLinkId")
            MediaLinkType.LOCAL -> generatePreview(basePath, mediaLink)
        }
    }

    private suspend fun generatePreview(
        basePath: Path,
        mediaLink: MediaLink,
    ) {
        val outputPath = basePath.resolve(mediaLink.id)
            .createDirectories()
            .resolve(PREVIEW_IMAGE_FILE_NAME)
        logger.debug("Starting video preview generation in: {}", outputPath)
        try {
            ffmpeg()
                .addInput(UrlInput.fromUrl(mediaLink.filePath))
                .addArguments("-vf", FFMPEG_FILTER)
                .addArguments("-v:q", PREVIEW_IMAGE_QUALITY)
                .addOutput(UrlOutput.toPath(outputPath))
                .executeAsync()
                .toCompletableFuture()
                .await()
            /*val duration = mediaFileProbe.getFileDuration(FileSystems.getDefault().getPath(mediaLink.filePath!!))
            val frames = duration.inWholeSeconds / 5
            ffmpeg()
                .addInput(UrlInput.fromUrl(mediaLink.filePath))
                .addArguments("-vf", "fps=1/5,scale=240:135:force_original_aspect_ratio=increase,crop=240:135,tile=8x${frames / 8}")
                .addOutput(UrlOutput.toPath(outputPath))
                .executeAsync()
                .toCompletableFuture()
                .await()*/
            logger.debug("Video preview generation completed normally.")
        } catch (e: JaffreeException) {
            logger.error("Failed to generate video preview", e)
            return
        }
    }
}
