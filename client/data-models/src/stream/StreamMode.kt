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
package anystream.models.stream

import kotlinx.serialization.Serializable

/**
 * [StreamMode] describes how media files are processed for
 * playback on client devices.
 */
@Serializable
enum class StreamMode {
    /**
     * [SourceFile] mode is used when clients support local
     * playback of the media container, video & audio tracks,
     * and subtitles.
     */
    SourceFile,

    /**
     * [Remux] mode is required when clients do not support
     * local playback of the media container, audio tracks,
     * or subtitles.
     */
    Remux,

    /**
     * [Transcode] mode is required when clients do not support
     * local playback of the media video tracks.
     */
    Transcode,
}
