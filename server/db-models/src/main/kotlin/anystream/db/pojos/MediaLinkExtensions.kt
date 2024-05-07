/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
package anystream.db.pojos

import anystream.models.Descriptor
import anystream.models.MediaKind
import anystream.models.MediaLink
import anystream.models.MediaLinkType
import anystream.util.ObjectId
import kotlinx.datetime.Clock
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun Path.toMediaLink(
    mediaKind: MediaKind,
    descriptor: Descriptor,
    directoryId: String,
): MediaLink {
    val now = Clock.System.now()
    return MediaLink(
        id = ObjectId.get().toString(),
        fileIndex = null,
        hash = null,
        filePath = absolutePathString(),
        mediaKind = mediaKind,
        type = MediaLinkType.LOCAL,
        directoryId = directoryId,
        descriptor = descriptor,
        createdAt = now,
        updatedAt = now
    )
}