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

import anystream.models.ClientCapabilities

actual fun createPlatformClientCapabilities(): ClientCapabilities {
    val supportedVideoCodecs = listOf(
        "h264",
        "vp8",
        "vp9",
        //"h265", // TODO: add only for supported browsers/hosts
    )

    val supportedAudioCodecs = listOf(
        "aac",
        //"ac3", // can be supported with hls.js instead of video.js
        "mp3",
        "opus",
        "vorbis",
    )

    val supportedContainers = listOf(
        "mp4",
        "webm",
    )

    return ClientCapabilities(
        supportedVideoCodecs = supportedVideoCodecs,
        supportedAudioCodecs = supportedAudioCodecs,
        supportedContainers = supportedContainers,
    )
}