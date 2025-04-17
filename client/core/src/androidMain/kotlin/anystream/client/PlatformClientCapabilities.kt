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

import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import anystream.models.ClientCapabilities

/**
 * Creates ClientCapabilities for Android platform.
 * Uses MediaCodecList to determine supported codecs.
 */
actual fun createPlatformClientCapabilities(): ClientCapabilities {
    val supportedVideoCodecs = mutableListOf<String>()
    val supportedAudioCodecs = mutableListOf<String>()
    
    // Add common video codecs
    if (isCodecSupported(MediaFormat.MIMETYPE_VIDEO_AVC)) {
        supportedVideoCodecs.add("h264")
    }
    if (isCodecSupported(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
        supportedVideoCodecs.add("h265")
        supportedVideoCodecs.add("hevc")
    }
    if (isCodecSupported(MediaFormat.MIMETYPE_VIDEO_VP8)) {
        supportedVideoCodecs.add("vp8")
    }
    if (isCodecSupported(MediaFormat.MIMETYPE_VIDEO_VP9)) {
        supportedVideoCodecs.add("vp9")
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
        isCodecSupported(MediaFormat.MIMETYPE_VIDEO_AV1)) {
        supportedVideoCodecs.add("av1")
    }
    
    // Add common audio codecs
    if (isCodecSupported(MediaFormat.MIMETYPE_AUDIO_AAC)) {
        supportedAudioCodecs.add("aac")
    }
    if (isCodecSupported(MediaFormat.MIMETYPE_AUDIO_MPEG)) {
        supportedAudioCodecs.add("mp3")
    }
    if (isCodecSupported(MediaFormat.MIMETYPE_AUDIO_OPUS)) {
        supportedAudioCodecs.add("opus")
    }
    if (isCodecSupported(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
        supportedAudioCodecs.add("flac")
    }
    
    // Common container formats supported by Android
    val supportedContainers = listOf("mp4", "webm", "mkv", "mp3", "aac")
    
    return ClientCapabilities(
        supportedVideoCodecs = supportedVideoCodecs,
        supportedAudioCodecs = supportedAudioCodecs,
        supportedContainers = supportedContainers,
    )
}

/**
 * Checks if a specific MIME type is supported by any codec on the device.
 */
private fun isCodecSupported(mimeType: String): Boolean {
    val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    val codecInfos = codecList.codecInfos
    
    for (codecInfo in codecInfos) {
        if (codecInfo.isEncoder) continue // Skip encoders, we only need decoders
        
        try {
            val types = codecInfo.supportedTypes
            for (type in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore exceptions when checking codec support
        }
    }
    
    return false
}