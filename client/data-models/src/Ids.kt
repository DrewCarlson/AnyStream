/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

interface IdBase {
    val value: String
}

@JvmInline @Serializable
value class UserId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}

@JvmInline @Serializable
value class SessionId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}

@JvmInline @Serializable
value class MetadataId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}

@JvmInline @Serializable
value class MediaLinkId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}

@JvmInline @Serializable
value class LibraryId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}

@JvmInline @Serializable
value class DirectoryId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}

@JvmInline @Serializable
value class TagId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}

@JvmInline @Serializable
value class PlaybackStateId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}

@JvmInline @Serializable
value class StreamEncodingId(
    override val value: String,
) : IdBase {
    override fun toString(): String = value
}
