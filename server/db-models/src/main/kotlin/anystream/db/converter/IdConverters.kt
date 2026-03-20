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
package anystream.db.converter

import anystream.models.*
import org.jooq.Converter
import kotlin.reflect.KClass

abstract class IdConverter<T : IdBase>(
    private val factory: (String) -> T,
    private val type: KClass<T>,
) : Converter<String, T> {
    override fun from(p0: String?): T? = p0?.let(factory)

    override fun fromType(): Class<String> = String::class.java

    override fun to(p0: T?): String? = p0?.value

    override fun toType(): Class<T> = type.java
}

@Suppress("UNUSED")
class UserIdConverter : IdConverter<UserId>(::UserId, UserId::class)

@Suppress("UNUSED")
class SessionIdConverter : IdConverter<SessionId>(::SessionId, SessionId::class)

@Suppress("UNUSED")
class MetadataIdConverter : IdConverter<MetadataId>(::MetadataId, MetadataId::class)

@Suppress("UNUSED")
class MediaLinkIdConverter : IdConverter<MediaLinkId>(::MediaLinkId, MediaLinkId::class)

@Suppress("UNUSED")
class LibraryIdConverter : IdConverter<LibraryId>(::LibraryId, LibraryId::class)

@Suppress("UNUSED")
class DirectoryIdConverter : IdConverter<DirectoryId>(::DirectoryId, DirectoryId::class)

@Suppress("UNUSED")
class TagIdConverter : IdConverter<TagId>(::TagId, TagId::class)

@Suppress("UNUSED")
class PlaybackStateIdConverter : IdConverter<PlaybackStateId>(::PlaybackStateId, PlaybackStateId::class)

@Suppress("UNUSED")
class StreamEncodingIdConverter :
    IdConverter<StreamEncodingId>(
        ::StreamEncodingId,
        StreamEncodingId::class,
    )
