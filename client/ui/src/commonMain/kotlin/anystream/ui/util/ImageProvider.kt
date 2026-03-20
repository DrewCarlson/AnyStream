/*
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.ui.util

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import anystream.client.AnyStreamClient
import anystream.models.MetadataId
import anystream.models.TagId

val LocalImageProvider = staticCompositionLocalOf<ImageProvider> { StaticImageProvider }

interface ImageProvider {
    @Stable
    fun url(
        imageType: String,
        metadataId: MetadataId,
        width: Int = 0,
    ): String

    @Stable
    fun url(
        imageType: String,
        tagId: TagId,
        width: Int = 0,
    ): String
}

private object StaticImageProvider : ImageProvider {
    override fun url(
        imageType: String,
        metadataId: MetadataId,
        width: Int,
    ): String {
        return when (imageType) {
            "poster" -> "https://image.tmdb.org/t/p/w300/xgAZRY9swQYRkj3waCZeBDBCkuj.jpg"
            "backdrop" -> "https://image.tmdb.org/t/p/w1280/pnIhvvYZytNDoDqwmxItWeSaDbp.jpg"
            else -> "https://image.tmdb.org/t/p/w300/xgAZRY9swQYRkj3waCZeBDBCkuj.jpg"
        }
    }

    override fun url(
        imageType: String,
        tagId: TagId,
        width: Int,
    ): String {
        return when (imageType) {
            "poster" -> "https://image.tmdb.org/t/p/w300/xgAZRY9swQYRkj3waCZeBDBCkuj.jpg"
            "backdrop" -> "https://image.tmdb.org/t/p/w1280/pnIhvvYZytNDoDqwmxItWeSaDbp.jpg"
            else -> "https://image.tmdb.org/t/p/w300/xgAZRY9swQYRkj3waCZeBDBCkuj.jpg"
        }
    }
}

fun AnyStreamClient.asImageProvider(): ImageProvider {
    return object : ImageProvider {
        override fun url(
            imageType: String,
            metadataId: MetadataId,
            width: Int,
        ): String {
            return images.buildImageUrl(imageType, metadataId, width)
        }

        override fun url(
            imageType: String,
            tagId: TagId,
            width: Int,
        ): String {
            return images.buildImageUrl(imageType, tagId, width)
        }
    }
}
