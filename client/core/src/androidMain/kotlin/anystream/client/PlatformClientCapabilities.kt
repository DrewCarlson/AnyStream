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
package anystream.client

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import anystream.models.ClientCapabilities

actual fun createPlatformClientCapabilities(): ClientCapabilities {
    val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    val codecInfos = codecList.codecInfos

    val videoFormatToCodec = mapOf(
        MediaFormat.MIMETYPE_VIDEO_AVC to "h264",
        MediaFormat.MIMETYPE_VIDEO_HEVC to "h265",
        MediaFormat.MIMETYPE_VIDEO_VP8 to "vp8",
        MediaFormat.MIMETYPE_VIDEO_VP9 to "vp9",
    ).run {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            plus(MediaFormat.MIMETYPE_VIDEO_AV1 to "av1")
        } else {
            this
        }
    }

    val audioFormatToCodec = mapOf(
        MediaFormat.MIMETYPE_AUDIO_AAC to "aac",
        MediaFormat.MIMETYPE_AUDIO_MPEG to "mp3",
        MediaFormat.MIMETYPE_AUDIO_OPUS to "opus",
        MediaFormat.MIMETYPE_AUDIO_FLAC to "flac",
    )

    val supportedFormats = findSupportedFormats(codecInfos)

    return ClientCapabilities(
        supportedVideoCodecs = videoFormatToCodec
            .filterKeys { it in supportedFormats }
            .values
            .toList(),
        supportedAudioCodecs = audioFormatToCodec
            .filterKeys { it in supportedFormats }
            .values
            .toList(),
        supportedContainers = listOf("mp4", "webm", "mkv", "mp3", "aac"),
    )
}

private fun findSupportedFormats(codecInfos: Array<MediaCodecInfo>): Set<String> {
    return buildSet {
        for (codecInfo in codecInfos) {
            if (codecInfo.isEncoder) continue

            codecInfo.supportedTypes.forEach { type ->
                add(type.lowercase())
            }
        }
    }
}
