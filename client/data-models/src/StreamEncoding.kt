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
package anystream.models

import dev.drewhamilton.poko.Poko
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
sealed class StreamEncodingTyped {
    abstract val id: String
    abstract val streamId: String?
    abstract val codecName: String
    abstract val codecLongName: String?
    abstract val index: Int?
    abstract val language: String?
    abstract val duration: Duration?
    abstract val title: String?
    abstract val mediaLinkId: String
    abstract val default: Boolean

    val languageName: String
        get() {
            return language
                ?.let(IsoLanguageCodes::by639_2)
                ?.name
                ?.replaceFirstChar(Char::titlecaseChar)
                ?: "Unknown"
        }
}

@Poko
@Serializable
class AudioStreamEncoding(
    override val id: String,
    override val streamId: String?,
    override val index: Int?,
    override val codecName: String,
    override val codecLongName: String?,
    override val language: String?,
    override val duration: Duration?,
    override val title: String?,
    override val mediaLinkId: String,
    override val default: Boolean,
    val profile: String?,
    val bitRate: Int?,
    val channels: Int,
    val channelLayout: String?,
    val sampleFmt: String?,
    val sampleRate: Int?,
) : StreamEncodingTyped()

@Poko
@Serializable
class VideoStreamEncoding(
    override val id: String,
    override val streamId: String?,
    override val index: Int?,
    override val codecName: String,
    override val codecLongName: String?,
    override val language: String?,
    override val duration: Duration?,
    override val title: String?,
    override val mediaLinkId: String,
    override val default: Boolean,
    val profile: String?,
    val bitRate: Int?,
    val level: Int,
    val height: Int,
    val width: Int,
    val colorSpace: String?,
    val colorRange: String?,
    val colorTransfer: String?,
    val colorPrimaries: String?,
    val pixFmt: String?,
    val fieldOrder: String?,
) : StreamEncodingTyped()

@Poko
@Serializable
class SubtitleStreamEncoding(
    override val id: String,
    override val streamId: String?,
    override val index: Int?,
    override val codecName: String,
    override val codecLongName: String?,
    override val language: String?,
    override val duration: Duration?,
    override val title: String?,
    override val mediaLinkId: String,
    override val default: Boolean,
) : StreamEncodingTyped()

fun StreamEncoding.typed(): StreamEncodingTyped {
    return when (type) {
        StreamEncodingType.AUDIO ->
            AudioStreamEncoding(
                id = id,
                streamId = streamId,
                index = index,
                codecName = codecName,
                codecLongName = codecLongName,
                language = language,
                duration = duration,
                title = title,
                mediaLinkId = mediaLinkId,
                default = default,
                profile = profile,
                bitRate = bitRate,
                channels = requireNotNull(channels),
                channelLayout = channelLayout,
                sampleFmt = sampleFmt,
                sampleRate = sampleRate,
            )
        StreamEncodingType.VIDEO ->
            VideoStreamEncoding(
                id = id,
                streamId = streamId,
                index = index,
                codecName = codecName,
                codecLongName = codecLongName,
                language = language,
                duration = duration,
                title = title,
                mediaLinkId = mediaLinkId,
                default = default,
                profile = profile,
                bitRate = bitRate,
                level = requireNotNull(level),
                height = requireNotNull(height),
                width = requireNotNull(width),
                colorSpace = colorSpace,
                colorRange = colorRange,
                colorTransfer = colorTransfer,
                colorPrimaries = colorPrimaries,
                pixFmt = pixFmt,
                fieldOrder = fieldOrder,
            )
        StreamEncodingType.SUBTITLE ->
            SubtitleStreamEncoding(
                id = id,
                streamId = streamId,
                index = index,
                codecName = codecName,
                codecLongName = codecLongName,
                language = language,
                duration = duration,
                title = title,
                mediaLinkId = mediaLinkId,
                default = default
            )
    }
}